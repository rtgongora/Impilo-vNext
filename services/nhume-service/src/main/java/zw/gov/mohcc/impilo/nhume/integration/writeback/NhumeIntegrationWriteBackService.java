package zw.gov.mohcc.impilo.nhume.integration.writeback;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.ServletRequestAttributes;
import zw.gov.mohcc.impilo.nhume.domain.DeliveryRequestEntity;
import zw.gov.mohcc.impilo.nhume.integration.trust.TrustLayerGuard;
import zw.gov.mohcc.impilo.nhume.integration.writeback.NhumeWriteBackGateway.WriteBackContext;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * On drop-off sign-off (DELIVERED), confirm the movement with the services
 * that own the cargo truth, using the integration refs the dispatcher captured
 * in {@code metadata.links} at mission creation:
 *
 * <ul>
 *   <li>{@code orosOrderRef} — receive in-flight specimens at the lab (OROS owns specimen truth)</li>
 *   <li>{@code madiOrderRef} — complete the issued blood order (MADI owns blood truth)</li>
 *   <li>{@code pctReferralRef} — accept the referral package on arrival (PCT owns referral truth)</li>
 *   <li>{@code duraRequisitionRef} — honestly skipped: Dura's requisition lifecycle ends at
 *       FULFILLED (warehouse-side); no destination-receipt transition exists yet</li>
 * </ul>
 *
 * <p>Outcomes (including failures) are returned for recording on the delivery
 * so the integration-link panel shows the truth. A failed write-back never
 * blocks the courier-facing sign-off.
 */
@Service
public class NhumeIntegrationWriteBackService {

    private static final Logger log = LoggerFactory.getLogger(NhumeIntegrationWriteBackService.class);

    private final NhumeWriteBackGateway gateway;
    private final ObjectMapper objectMapper;
    private final boolean enabled;

    public NhumeIntegrationWriteBackService(
            NhumeWriteBackGateway gateway,
            ObjectMapper objectMapper,
            @Value("${nhume.writeback.enabled:true}") boolean enabled) {
        this.gateway = gateway;
        this.objectMapper = objectMapper;
        this.enabled = enabled;
    }

    /**
     * Execute all applicable write-backs for a delivered mission. Never throws.
     *
     * @return outcome map keyed by system (OROS/MADI/PCT/DURA); empty when
     *         disabled or the delivery carries no integration links.
     */
    public Map<String, WriteBackOutcome> onDelivered(DeliveryRequestEntity delivery,
                                                     TrustLayerGuard.ActorContext actor) {
        Map<String, WriteBackOutcome> outcomes = new LinkedHashMap<>();
        if (!enabled) {
            return outcomes;
        }
        Map<String, String> links = readLinks(delivery.getMetadataJson());
        if (links.isEmpty()) {
            return outcomes;
        }
        WriteBackContext ctx = buildContext(delivery, actor);

        String oros = links.get("orosOrderRef");
        if (oros != null && !oros.isBlank()) {
            outcomes.put("OROS", safely("OROS", () -> gateway.orosReceiveByOrder(oros, ctx)));
        }
        String madi = links.get("madiOrderRef");
        if (madi != null && !madi.isBlank()) {
            outcomes.put("MADI", safely("MADI", () -> gateway.madiCompleteOrder(madi, ctx)));
        }
        String pct = links.get("pctReferralRef");
        if (pct != null && !pct.isBlank()) {
            outcomes.put("PCT", safely("PCT", () -> gateway.pctAcceptReferral(pct, ctx)));
        }
        String dura = links.get("duraRequisitionRef");
        if (dura != null && !dura.isBlank()) {
            outcomes.put("DURA", WriteBackOutcome.skippedNoTransition(
                    "Dura has no destination-receipt transition (requisition lifecycle ends at FULFILLED); "
                            + "record receipt in Dura stock receiving"));
        }
        return outcomes;
    }

    /** Merge write-back outcomes into a delivery's metadata JSON under {@code links_writeback}. */
    public String mergeOutcomes(String metadataJson, Map<String, WriteBackOutcome> outcomes) {
        try {
            Map<String, Object> metadata = metadataJson == null || metadataJson.isBlank()
                    ? new LinkedHashMap<>()
                    : objectMapper.readValue(metadataJson, new TypeReference<Map<String, Object>>() {});
            Map<String, Object> recorded = new LinkedHashMap<>();
            String now = OffsetDateTime.now().toString();
            outcomes.forEach((system, outcome) -> recorded.put(system, Map.of(
                    "status", outcome.status().name(),
                    "detail", outcome.detail() == null ? "" : outcome.detail(),
                    "at", now)));
            metadata.put("links_writeback", recorded);
            return objectMapper.writeValueAsString(metadata);
        } catch (Exception e) {
            log.warn("Could not merge write-back outcomes into delivery metadata: {}", e.getMessage());
            return metadataJson;
        }
    }

    private Map<String, String> readLinks(String metadataJson) {
        if (metadataJson == null || metadataJson.isBlank()) {
            return Map.of();
        }
        try {
            Map<String, Object> metadata =
                    objectMapper.readValue(metadataJson, new TypeReference<Map<String, Object>>() {});
            Object links = metadata.get("links");
            if (!(links instanceof Map<?, ?> linkMap)) {
                return Map.of();
            }
            Map<String, String> out = new LinkedHashMap<>();
            linkMap.forEach((k, v) -> {
                if (k != null && v != null) out.put(k.toString(), v.toString());
            });
            return out;
        } catch (Exception e) {
            log.warn("Unparseable delivery metadata for write-back; skipping: {}", e.getMessage());
            return Map.of();
        }
    }

    private WriteBackContext buildContext(DeliveryRequestEntity delivery,
                                          TrustLayerGuard.ActorContext actor) {
        return new WriteBackContext(
                delivery.getTenantId() != null ? delivery.getTenantId().toString() : null,
                delivery.getPodId(),
                delivery.getCorrelationId() != null ? delivery.getCorrelationId().toString() : null,
                actor != null ? actor.actorId() : null,
                actor != null ? actor.actorType() : null,
                currentBearerToken(),
                delivery.getDeliveryId() != null ? delivery.getDeliveryId().toString() : null);
    }

    /** Propagate the signing actor's bearer so owning services authorize + attribute honestly. */
    private String currentBearerToken() {
        try {
            RequestAttributes attrs =
                    org.springframework.web.context.request.RequestContextHolder.getRequestAttributes();
            if (attrs instanceof ServletRequestAttributes servletAttrs) {
                HttpServletRequest request = servletAttrs.getRequest();
                return request.getHeader(HttpHeaders.AUTHORIZATION);
            }
        } catch (Exception ignored) {
            // Outside a request (e.g. scheduled retry) — call proceeds unauthenticated.
        }
        return null;
    }

    private WriteBackOutcome safely(String system, java.util.function.Supplier<WriteBackOutcome> call) {
        try {
            return call.get();
        } catch (Exception e) {
            log.warn("Nhume write-back to {} threw unexpectedly: {}", system, e.toString());
            return WriteBackOutcome.failed(system + " write-back error: " + e.getClass().getSimpleName());
        }
    }
}
