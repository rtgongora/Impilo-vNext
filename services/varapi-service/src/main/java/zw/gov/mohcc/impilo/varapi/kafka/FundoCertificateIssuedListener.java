package zw.gov.mohcc.impilo.varapi.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import zw.gov.mohcc.impilo.varapi.core.FundoCpdGovernanceService;

import java.time.Instant;
import java.util.UUID;

/**
 * Native CPD egress consumer (G-FU-03). Lands Fundo {@code certificate.issued.v1} events into the
 * governed CPD candidate flow — the same {@link FundoCpdGovernanceService#ingestCompletion} entry the
 * Fundo webhook uses, so varapi remains the single CPD ledger authority and Fundo never awards points.
 *
 * <p>Guarded: only CPD-eligible certificates issued to a PROVIDER subject become candidates (a citizen
 * learner's certificate is not CPD). Idempotent via the candidate's external reference, so re-delivery
 * is safe. Errors are swallowed (logged) so a bad message never poisons the listener.
 */
@Component
public class FundoCertificateIssuedListener {

    private static final Logger log = LoggerFactory.getLogger(FundoCertificateIssuedListener.class);

    private final FundoCpdGovernanceService cpdGovernanceService;
    private final ObjectMapper objectMapper;

    public FundoCertificateIssuedListener(FundoCpdGovernanceService cpdGovernanceService,
                                          ObjectMapper objectMapper) {
        this.cpdGovernanceService = cpdGovernanceService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(
            topics = "${varapi.fundo.certificate-issued-topic:impilo.learning.certificate.issued.v1}",
            groupId = "${spring.kafka.consumer.group-id:varapi-service}")
    public void onCertificateIssued(String payload) {
        try {
            JsonNode n = objectMapper.readTree(payload);

            // Only CPD-eligible provider certificates are CPD candidates.
            if (!n.path("cpdEligible").asBoolean(false)) {
                return;
            }
            if (!"PROVIDER".equalsIgnoreCase(n.path("subjectType").asText(""))) {
                return;
            }

            UUID tenantId = UUID.fromString(n.get("tenantId").asText());
            String providerPublicId = n.get("subjectId").asText();
            String courseId = textOrNull(n, "courseId");
            String courseName = textOrNull(n, "title");
            Instant completedAt = n.hasNonNull("issuedAt") ? Instant.parse(n.get("issuedAt").asText()) : Instant.now();
            int credits = n.path("suggestedCpdPoints").asInt(0);
            String externalRef = textOrNull(n, "certificateNumber");

            cpdGovernanceService.ingestCompletion(tenantId, providerPublicId, null, courseId, courseName,
                    completedAt, credits, externalRef, payload);
            log.info("Landed native Fundo CPD candidate from certificate.issued provider={} ref={}",
                    providerPublicId, externalRef);
        } catch (Exception e) {
            log.warn("Failed to ingest Fundo certificate.issued CPD candidate: {}", e.getMessage());
        }
    }

    private static String textOrNull(JsonNode n, String field) {
        return n.hasNonNull(field) ? n.get(field).asText() : null;
    }
}
