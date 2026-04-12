package zw.gov.mohcc.impilo.shareslip.events;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import zw.gov.mohcc.impilo.shareslip.core.ShareLinkService;
import zw.gov.mohcc.impilo.shareslip.dto.CreateShareLinkRequest;

import java.util.List;
import java.util.UUID;

/**
 * Consumes MUSHEX (costing / claims / payment) events to auto-generate share slips
 * (payment proofs for patients, remittance proofs for providers).
 */
@Component
public class ShareSlipEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(ShareSlipEventConsumer.class);

    /** Placeholder document id when no Landela document is attached yet (claim / remittance subject only). */
    public static final UUID AUTOMATION_DOCUMENT_PLACEHOLDER =
            UUID.fromString("00000000-0000-4000-8000-000000000001");

    private final ObjectMapper objectMapper;
    private final ShareLinkService shareLinkService;

    public ShareSlipEventConsumer(ObjectMapper objectMapper, ShareLinkService shareLinkService) {
        this.objectMapper = objectMapper;
        this.shareLinkService = shareLinkService;
    }

    @KafkaListener(topics = "mushex.claim.adjudicated", groupId = "share-slip-service")
    public void consumeClaimAdjudicated(String message, Acknowledgment acknowledgment) {
        handle(message, acknowledgment, (payload, correlationId) -> {
            String status = firstNonBlank(
                    text(payload, "status"),
                    text(payload, "adjudicationStatus"),
                    text(payload, "claimStatus"),
                    text(payload, "adjudication_status"));
            if (status == null || (!status.equalsIgnoreCase("PAID") && !status.equalsIgnoreCase("APPROVED"))) {
                log.debug("SHARE-SLIP: claim adjudication status {} does not trigger auto-share", status);
                return;
            }
            String claimId = firstNonBlank(text(payload, "claim_id"), text(payload, "claimId"));
            if (claimId == null) {
                log.warn("SHARE-SLIP: adjudicated claim missing claim_id correlationId={}", correlationId);
                return;
            }
            UUID tenantId = parseUuid(firstNonBlank(
                    text(payload, "tenant_id"),
                    text(payload, "tenantId")));
            if (tenantId == null) {
                log.warn("SHARE-SLIP: adjudicated claim missing tenant_id claimId={} correlationId={}",
                        claimId, correlationId);
                return;
            }
            String patientCpid = firstNonBlank(text(payload, "patient_cpid"), text(payload, "patientCpid"));
            String amount = firstNonBlank(
                    text(payload, "amount"), text(payload, "paidAmount"), text(payload, "totalAmount"));

            CreateShareLinkRequest request = new CreateShareLinkRequest(
                    "CLAIM",
                    claimId,
                    patientCpid,
                    List.of(AUTOMATION_DOCUMENT_PLACEHOLDER),
                    "Automated payment proof (claim " + claimId + ", amount=" + amount + ")",
                    "OTP",
                    30 * 24,
                    1
            );
            shareLinkService.createAutomatedShareLink(request, tenantId, correlationId, "CLAIM_ADJUDICATED");
        });
    }

    @KafkaListener(topics = "mushex.remittance.issued", groupId = "share-slip-service")
    public void consumeRemittanceIssued(String message, Acknowledgment acknowledgment) {
        handle(message, acknowledgment, (payload, correlationId) -> {
            String remittanceId = firstNonBlank(
                    text(payload, "remittance_id"),
                    text(payload, "remittanceId"));
            if (remittanceId == null) {
                log.warn("SHARE-SLIP: remittance event missing remittance_id correlationId={}", correlationId);
                return;
            }
            UUID tenantId = parseUuid(firstNonBlank(
                    text(payload, "tenant_id"),
                    text(payload, "tenantId")));
            if (tenantId == null) {
                log.warn("SHARE-SLIP: remittance missing tenant_id remittanceId={} correlationId={}",
                        remittanceId, correlationId);
                return;
            }
            String providerId = firstNonBlank(text(payload, "provider_id"), text(payload, "providerId"));
            String amount = firstNonBlank(text(payload, "amount"), text(payload, "totalAmount"));

            CreateShareLinkRequest request = new CreateShareLinkRequest(
                    "REMITTANCE",
                    remittanceId,
                    providerId,
                    List.of(AUTOMATION_DOCUMENT_PLACEHOLDER),
                    "Automated remittance share (provider=" + providerId + ", amount=" + amount + ")",
                    "OTP",
                    30 * 24,
                    1
            );
            shareLinkService.createAutomatedShareLink(request, tenantId, correlationId, "REMITTANCE_ISSUED");
        });
    }

    @KafkaListener(topics = "mushex.payment.status.changed", groupId = "share-slip-service")
    public void consumePaymentStatusChanged(String message, Acknowledgment acknowledgment) {
        handle(message, acknowledgment, (payload, correlationId) -> {
            String status = firstNonBlank(text(payload, "status"), text(payload, "paymentStatus"));
            if (status == null || !status.equalsIgnoreCase("PAID")) {
                log.debug("SHARE-SLIP: payment status {} does not trigger auto-share", status);
                return;
            }
            String claimId = firstNonBlank(text(payload, "claim_id"), text(payload, "claimId"));
            if (claimId == null) {
                log.debug("SHARE-SLIP: payment event without claim_id, skipping correlationId={}", correlationId);
                return;
            }
            UUID tenantId = parseUuid(firstNonBlank(
                    text(payload, "tenant_id"),
                    text(payload, "tenantId")));
            if (tenantId == null) {
                log.warn("SHARE-SLIP: payment event missing tenant_id claimId={} correlationId={}",
                        claimId, correlationId);
                return;
            }
            String patientCpid = firstNonBlank(text(payload, "patient_cpid"), text(payload, "patientCpid"));
            String amount = firstNonBlank(text(payload, "amount"), text(payload, "paidAmount"));

            CreateShareLinkRequest request = new CreateShareLinkRequest(
                    "CLAIM",
                    claimId,
                    patientCpid,
                    List.of(AUTOMATION_DOCUMENT_PLACEHOLDER),
                    "Automated payment proof (paid, claim " + claimId + ", amount=" + amount + ")",
                    "OTP",
                    30 * 24,
                    1
            );
            shareLinkService.createAutomatedShareLink(request, tenantId, correlationId, "PAYMENT_PAID");
        });
    }

    @FunctionalInterface
    private interface PayloadHandler {
        void accept(JsonNode payload, String correlationId) throws JsonProcessingException;
    }

    private void handle(String message, Acknowledgment acknowledgment, PayloadHandler handler) {
        try {
            JsonNode root = objectMapper.readTree(message);
            String correlationId = firstNonBlank(
                    text(root, "correlation_id"),
                    text(root, "correlationId"));
            JsonNode payload = extractPayload(root);
            if (payload == null || payload.isNull()) {
                log.warn("SHARE-SLIP: mushex event missing payload, skipping correlationId={}", correlationId);
                return;
            }
            handler.accept(payload, correlationId);
        } catch (JsonProcessingException e) {
            log.error("SHARE-SLIP: failed to parse mushex event: {}", e.getMessage(), e);
        } catch (RuntimeException e) {
            log.error("SHARE-SLIP: error handling mushex event: {}", e.getMessage(), e);
        } finally {
            if (acknowledgment != null) {
                acknowledgment.acknowledge();
            }
        }
    }

    private JsonNode extractPayload(JsonNode root) throws JsonProcessingException {
        if (!root.has("payload")) {
            return root;
        }
        JsonNode p = root.get("payload");
        if (p == null || p.isNull()) {
            return null;
        }
        if (p.isObject()) {
            return p;
        }
        if (p.isTextual()) {
            return objectMapper.readTree(p.asText());
        }
        return p;
    }

    private static String text(JsonNode node, String field) {
        if (node == null || !node.has(field) || node.get(field).isNull()) {
            return null;
        }
        return node.get(field).asText(null);
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return null;
    }

    private static UUID parseUuid(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(raw.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
