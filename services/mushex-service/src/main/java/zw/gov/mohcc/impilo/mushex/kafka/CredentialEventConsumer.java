package zw.gov.mohcc.impilo.mushex.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import zw.gov.mohcc.impilo.mushex.service.PaymentIntentService;

import java.util.UUID;

/**
 * Consumes credential-verification-service topics so MUSHEX can react to revocations in near real time.
 */
@Component
@Profile("!test")
public class CredentialEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(CredentialEventConsumer.class);

    private final ObjectMapper objectMapper;
    private final PaymentIntentService paymentIntentService;

    public CredentialEventConsumer(ObjectMapper objectMapper,
                                   PaymentIntentService paymentIntentService) {
        this.objectMapper = objectMapper;
        this.paymentIntentService = paymentIntentService;
    }

    @KafkaListener(topics = "creds.credential.revoked", groupId = "mushex-service")
    public void onCredentialRevoked(String message, Acknowledgment ack) {
        try {
            JsonNode root = objectMapper.readTree(message);
            String tenantId = text(root, "tenantId");
            String subjectType = text(root, "subjectType");
            String subjectId = text(root, "subjectId");
            String credentialId = text(root, "credentialId");
            String correlationId = firstNonBlank(text(root, "correlation_id"), text(root, "correlationId"));

            log.warn("Credential REVOKED credentialId={} tenantId={} subjectType={} subjectId={} correlationId={}",
                    credentialId, tenantId, subjectType, subjectId, correlationId);

            if (tenantId != null && subjectId != null && !subjectId.isBlank()) {
                paymentIntentService.cancelPendingIntentsForCredentialRevocation(
                        UUID.fromString(tenantId), subjectType, subjectId, "CREDENTIAL_REVOKED");
            }
        } catch (Exception e) {
            log.error("Failed to process creds.credential.revoked — poison message or bad payload. {}", e.toString());
        } finally {
            ack.acknowledge();
        }
    }

    @KafkaListener(topics = "creds.credential.issued", groupId = "mushex-service")
    public void onCredentialIssued(String message, Acknowledgment ack) {
        try {
            JsonNode root = objectMapper.readTree(message);
            logAudit("CREDENTIAL_ISSUED", root);
        } catch (Exception e) {
            log.error("Failed to process creds.credential.issued — {}", e.toString());
        } finally {
            ack.acknowledge();
        }
    }

    @KafkaListener(topics = "creds.credential.verified", groupId = "mushex-service")
    public void onCredentialVerified(String message, Acknowledgment ack) {
        try {
            JsonNode root = objectMapper.readTree(message);
            logAudit("CREDENTIAL_VERIFIED", root);
        } catch (Exception e) {
            log.error("Failed to process creds.credential.verified — {}", e.toString());
        } finally {
            ack.acknowledge();
        }
    }

    private void logAudit(String kind, JsonNode root) {
        log.info("{} credentialId={} tenantId={} subjectType={} subjectId={} verificationResult={} correlationId={}",
                kind,
                text(root, "credentialId"),
                text(root, "tenantId"),
                text(root, "subjectType"),
                text(root, "subjectId"),
                text(root, "verificationResult"),
                firstNonBlank(text(root, "correlation_id"), text(root, "correlationId")));
    }

    private static String text(JsonNode node, String field) {
        return node != null && node.has(field) && !node.get(field).isNull()
                ? node.get(field).asText()
                : null;
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a;
        }
        if (b != null && !b.isBlank()) {
            return b;
        }
        return null;
    }
}
