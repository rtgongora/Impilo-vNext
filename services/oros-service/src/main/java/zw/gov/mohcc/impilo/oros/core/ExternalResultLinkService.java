package zw.gov.mohcc.impilo.oros.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.oros.integration.ShareSlipClient;
import zw.gov.mohcc.impilo.oros.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.oros.persistence.entity.OrderEntity;
import zw.gov.mohcc.impilo.oros.persistence.entity.ResultEntity;
import zw.gov.mohcc.impilo.oros.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.oros.persistence.repository.OrderRepository;
import zw.gov.mohcc.impilo.oros.persistence.repository.ResultRepository;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Issues scoped, OTP-protected, time-limited external access to a diagnostic result by delegating
 * to the share-slip secure-link primitive ({@code subjectType=DIAGNOSTIC_RESULT}) — acceptance
 * criterion E. No clinical content travels over insecure channels; the link references the
 * result's stored documents and is governed + audited by share-slip + an OROS outbox event.
 */
@Service
public class ExternalResultLinkService {

    private static final Logger log = LoggerFactory.getLogger(ExternalResultLinkService.class);

    private final ResultRepository resultRepository;
    private final OrderRepository orderRepository;
    private final ShareSlipClient shareSlipClient;
    private final EventOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public ExternalResultLinkService(ResultRepository resultRepository,
                                     OrderRepository orderRepository,
                                     ShareSlipClient shareSlipClient,
                                     EventOutboxRepository outboxRepository,
                                     ObjectMapper objectMapper) {
        this.resultRepository = resultRepository;
        this.orderRepository = orderRepository;
        this.shareSlipClient = shareSlipClient;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Issue a secure external link for a result.
     *
     * @throws IllegalArgumentException if the result does not exist
     * @throws IllegalStateException    if the result has no documents to share, or share-slip fails
     */
    @Transactional
    public ShareSlipClient.ShareLinkRef issue(UUID resultId, int expiryHours, int maxClaims) {
        TrustContext ctx = TrustContextHolder.require();
        ResultEntity r = resultRepository.findById(resultId)
                .orElseThrow(() -> new IllegalArgumentException("Result not found: " + resultId));

        List<UUID> docs = parseDocIds(r.getDocIds());
        if (docs.isEmpty()) {
            throw new IllegalStateException("Result " + resultId + " has no documents to share externally");
        }

        OrderEntity order = orderRepository.findByOrderId(r.getOrderId()).orElse(null);
        String subjectName = "Diagnostic result " + (order != null && order.getAccessionNumber() != null
                ? order.getAccessionNumber() : r.getOrderId());

        ShareSlipClient.ShareLinkRef ref = shareSlipClient.createLink(
                "DIAGNOSTIC_RESULT", resultId.toString(), subjectName, docs,
                "External secure result access", expiryHours, maxClaims);

        publishEvent(r.getOrderId(), resultId, ref, ctx.tenantId());
        log.info("Issued external result link: resultId={}, linkId={}", resultId, ref.linkId());
        return ref;
    }

    private List<UUID> parseDocIds(String docIdsJson) {
        List<UUID> out = new ArrayList<>();
        if (docIdsJson == null || docIdsJson.isBlank()) {
            return out;
        }
        try {
            JsonNode node = objectMapper.readTree(docIdsJson);
            if (node.isArray()) {
                for (JsonNode el : node) {
                    tryAdd(out, el.asText());
                }
            } else if (node.isTextual()) {
                tryAdd(out, node.asText());
            }
        } catch (Exception e) {
            log.warn("Could not parse result docIds as JSON: {}", e.getMessage());
        }
        return out;
    }

    private void tryAdd(List<UUID> out, String value) {
        try {
            out.add(UUID.fromString(value));
        } catch (IllegalArgumentException ignored) {
            // non-UUID document reference — skip (only UUID landela docs are shareable)
        }
    }

    private void publishEvent(String orderId, UUID resultId, ShareSlipClient.ShareLinkRef ref, UUID tenantId) {
        try {
            EventOutboxEntity event = new EventOutboxEntity();
            event.setAggregateType("RESULT");
            event.setAggregateId(resultId.toString());
            event.setEventType("RESULT_EXTERNAL_LINK_ISSUED");
            event.setPayload(objectMapper.writeValueAsString(java.util.Map.of(
                    "orderId", orderId,
                    "resultId", resultId.toString(),
                    "linkId", ref.linkId() != null ? ref.linkId() : "",
                    "status", ref.status() != null ? ref.status() : "")));
            event.setTenantId(tenantId);
            outboxRepository.save(event);
        } catch (Exception e) {
            log.error("Failed to write external-link outbox event: {}", e.getMessage(), e);
        }
    }
}
