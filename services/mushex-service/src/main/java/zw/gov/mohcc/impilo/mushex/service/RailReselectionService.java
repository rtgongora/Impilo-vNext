package zw.gov.mohcc.impilo.mushex.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.mushex.domain.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.mushex.domain.entity.PaymentIntentEntity;
import zw.gov.mohcc.impilo.mushex.domain.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.mushex.domain.repository.PaymentIntentRepository;
import zw.gov.mohcc.impilo.mushex.service.rail.RailSelectionPolicy;
import zw.gov.mohcc.impilo.mushex.service.rail.RailSelectionRequest;
import zw.gov.mohcc.impilo.mushex.service.rail.RailSelectionResult;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Phase 3 follow-on — controlled rail reselection.
 *
 * <p>Re-runs {@link RailSelectionPolicy} against the persisted intent and stamps the
 * new decision onto {@code metadata.rail_selection}, appending the previous value to
 * {@code metadata.rail_selection.history[]}. Emits a dedicated
 * {@code ATTEMPT_RESELECTED} outbox event.
 *
 * <p>This is intentionally <em>not</em> attempt-creation — no adapter is called,
 * no money is moved. It only changes which rail the next
 * {@link PaymentAttemptService#initiateAttempt(String, String)} call will use.
 *
 * <p>See {@code docs/design/phase-3-attempt-time-rail-enforcement-implementation.md} §11
 * and the original audit doc §3.2 (reselection / controlled retry).
 */
@Service
public class RailReselectionService {

    private static final Logger log = LoggerFactory.getLogger(RailReselectionService.class);

    private final PaymentIntentService paymentIntentService;
    private final PaymentIntentRepository intentRepository;
    private final EventOutboxRepository outboxRepository;
    private final RailSelectionPolicy railSelectionPolicy;
    private final ObjectMapper objectMapper;

    public RailReselectionService(PaymentIntentService paymentIntentService,
                                  PaymentIntentRepository intentRepository,
                                  EventOutboxRepository outboxRepository,
                                  RailSelectionPolicy railSelectionPolicy,
                                  ObjectMapper objectMapper) {
        this.paymentIntentService = paymentIntentService;
        this.intentRepository = intentRepository;
        this.outboxRepository = outboxRepository;
        this.railSelectionPolicy = railSelectionPolicy;
        this.objectMapper = objectMapper;
    }

    /**
     * Re-run rail selection for the named intent.
     *
     * @param intentId    the persisted intent identifier.
     * @param retryReason optional operator-supplied reason for the reselection; recorded
     *                    in {@code metadata.rail_selection.reason_detail} and in the
     *                    {@code ATTEMPT_RESELECTED} outbox payload.
     * @return the updated intent entity.
     * @throws IntentNotFoundException when the intent does not exist.
     */
    @Transactional
    public PaymentIntentEntity reselect(String intentId, String retryReason) {
        TrustContext ctx = TrustContextHolder.require();
        PaymentIntentEntity intent = paymentIntentService.getIntent(intentId);

        boolean isImpiloSim = isImpiloSimulation(intent.getMetadata());
        RailSelectionRequest request = RailSelectionRequest.empty(
                intent.getSourceType(),
                intent.getAmountTotal(),
                intent.getCurrency(),
                intent.getFacilityId(),
                isImpiloSim);
        RailSelectionResult result = railSelectionPolicy.select(request);

        JsonNode previous = readRailSelection(intent.getMetadata());
        String mergedMetadata = mergeReselectIntoMetadata(intent.getMetadata(), result, previous, retryReason);
        intent.setMetadata(mergedMetadata);
        intent = intentRepository.save(intent);

        log.info("Intent {} reselected: previousRail={} -> newRail={} reason={} retryReason={}",
                intentId, readPreviousEffectiveRail(previous), result.effectiveRail(),
                result.reason(), retryReason);

        publishReselected(intent, result, previous, retryReason, ctx.tenantId());
        return intent;
    }

    private boolean isImpiloSimulation(String metadataJson) {
        if (metadataJson == null || metadataJson.isBlank()) {
            return false;
        }
        try {
            JsonNode n = objectMapper.readTree(metadataJson);
            if (n.hasNonNull("impilo_simulation") && n.get("impilo_simulation").asBoolean()) {
                return true;
            }
            return n.hasNonNull("impiloSimulation") && n.get("impiloSimulation").asBoolean();
        } catch (Exception e) {
            return false;
        }
    }

    private JsonNode readRailSelection(String metadataJson) {
        if (metadataJson == null || metadataJson.isBlank()) {
            return null;
        }
        try {
            JsonNode n = objectMapper.readTree(metadataJson);
            if (n == null || !n.isObject()) {
                return null;
            }
            JsonNode rs = n.get("rail_selection");
            return rs != null && rs.isObject() ? rs : null;
        } catch (Exception e) {
            return null;
        }
    }

    private String readPreviousEffectiveRail(JsonNode previousRailSelection) {
        if (previousRailSelection == null) {
            return "NONE";
        }
        JsonNode er = previousRailSelection.get("effective_rail");
        return er == null || er.isNull() ? "NONE" : er.asText();
    }

    /**
     * Build the new metadata string: replace {@code rail_selection} with the freshly-computed
     * selection, push the previous {@code rail_selection} onto a {@code history[]} array
     * (creating it if absent), record the operator-supplied retry reason.
     */
    private String mergeReselectIntoMetadata(String inboundMetadata,
                                             RailSelectionResult result,
                                             JsonNode previous,
                                             String retryReason) {
        try {
            JsonNode parsed = null;
            if (inboundMetadata != null && !inboundMetadata.isBlank()) {
                parsed = objectMapper.readTree(inboundMetadata);
            }
            ObjectNode root;
            if (parsed instanceof ObjectNode obj) {
                root = obj;
            } else {
                root = objectMapper.createObjectNode();
            }
            ObjectNode rs = objectMapper.createObjectNode();
            rs.put("effective_rail", result.effectiveRail().name());
            if (result.preferredRail() != null) {
                rs.put("preferred_rail", result.preferredRail().name());
            } else {
                rs.putNull("preferred_rail");
            }
            rs.put("fallback_applied", result.fallbackApplied());
            rs.put("direct_gateway_requested", result.directGatewayRequested());
            rs.put("reason", result.reason().name());
            String detail = retryReason != null && !retryReason.isBlank()
                    ? "Reselected: " + retryReason
                    : "Reselected at operator request";
            rs.put("reason_detail", detail);
            rs.put("selected_at", OffsetDateTime.now().toString());
            rs.put("selection_version", "2");
            rs.put("reselected", true);

            ArrayNode history;
            JsonNode existingHistory = rs.path("history");
            JsonNode preserved = root.path("rail_selection").path("history");
            if (preserved.isArray()) {
                history = (ArrayNode) preserved.deepCopy();
            } else if (existingHistory.isArray()) {
                history = (ArrayNode) existingHistory;
            } else {
                history = objectMapper.createArrayNode();
            }
            if (previous != null) {
                ObjectNode snapshot = previous.deepCopy();
                snapshot.put("superseded_at", OffsetDateTime.now().toString());
                history.add(snapshot);
            }
            rs.set("history", history);

            root.set("rail_selection", rs);
            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            log.warn("Failed to merge reselect into metadata; preserving original: {}", e.getMessage());
            return inboundMetadata;
        }
    }

    private void publishReselected(PaymentIntentEntity intent,
                                   RailSelectionResult result,
                                   JsonNode previous,
                                   String retryReason,
                                   UUID tenantId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("intentId", intent.getIntentId());
        payload.put("previousRail", readPreviousEffectiveRail(previous));
        payload.put("effectiveRail", result.effectiveRail().name());
        payload.put("preferredRail",
                result.preferredRail() != null ? result.preferredRail().name() : "NONE");
        payload.put("reason", result.reason().name());
        if (retryReason != null && !retryReason.isBlank()) {
            payload.put("retryReason", retryReason);
        }
        try {
            EventOutboxEntity event = new EventOutboxEntity();
            event.setAggregateType("PAYMENT_INTENT");
            event.setAggregateId(intent.getIntentId());
            event.setEventType("ATTEMPT_RESELECTED");
            event.setPayload(objectMapper.writeValueAsString(payload));
            event.setTenantId(tenantId);
            outboxRepository.save(event);
        } catch (Exception e) {
            log.error("Failed to write ATTEMPT_RESELECTED outbox event", e);
        }
    }
}
