package zw.gov.mohcc.impilo.vito.events;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.vito.core.proofing.ProofingOutcome;
import zw.gov.mohcc.impilo.vito.core.proofing.ProofingService;

import java.util.UUID;

/**
 * Consumes {@code civil.identity.verified} (UBOMI RG/CRVS adapter, X2 W1c) and
 * records the {@link ProofingOutcome#AUTHORITATIVELY_VERIFIED} outcome into the
 * VITO proofing ledger — the first and only producer of that previously-dark
 * outcome. This is how an authoritative Registrar-General match <em>raises</em>
 * a person's assurance (LOA 3, method {@code MOSIP_INDIRECT}).
 *
 * <p>The event is cpid-free and PII-minimal: it carries the internal Health ID
 * anchor + an opaque {@code civilIdentityToken} + status. The opaque token is
 * the only RG artefact stored (in the proofing event's {@code artifactRefs}) —
 * never civil PII, never the National ID.</p>
 *
 * <p>Best-effort and idempotent-tolerant: a malformed or non-VERIFIED event is
 * logged and skipped, never allowed to break the consumer. The proofing ledger
 * is append-only and assurance is computed as a max, so an at-least-once
 * redelivery is harmless.</p>
 */
@Component
public class CivilIdentityEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(CivilIdentityEventConsumer.class);
    private static final String VERIFIER_ID = "ubomi-rg-relay";

    private final ProofingService proofingService;
    private final ObjectMapper objectMapper;

    public CivilIdentityEventConsumer(ProofingService proofingService, ObjectMapper objectMapper) {
        this.proofingService = proofingService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "civil.identity.verified", groupId = "vito-civil-identity")
    @Transactional
    public void onCivilIdentityVerified(String message) {
        try {
            JsonNode node = objectMapper.readTree(message);
            // Tolerate an outer v1.1 envelope {payload:{...}} or a flat payload.
            JsonNode p = node.has("payload") && node.get("payload").isObject() ? node.get("payload") : node;

            String status = p.path("status").asText("");
            if (!"VERIFIED".equalsIgnoreCase(status)) {
                return; // only an authoritative verification raises assurance
            }
            String tenantIdStr = p.path("tenantId").asText("");
            String healthIdStr = p.path("healthId").asText("");
            String civilIdentityToken = p.path("civilIdentityToken").asText("");
            if (tenantIdStr.isBlank() || healthIdStr.isBlank() || civilIdentityToken.isBlank()) {
                log.warn("civil.identity.verified missing required fields; skipping");
                return;
            }

            UUID tenantId = UUID.fromString(tenantIdStr);
            UUID healthId = UUID.fromString(healthIdStr);

            // Store ONLY the opaque token as the artefact ref — never civil PII.
            String artifactRefs = objectMapper.writeValueAsString(
                    java.util.List.of("civil-identity-token:" + civilIdentityToken));

            proofingService.recordEvent(
                    tenantId,
                    healthId,
                    ProofingOutcome.AUTHORITATIVELY_VERIFIED.ledgerMethod(),
                    ProofingOutcome.AUTHORITATIVELY_VERIFIED.assuranceLevel(),
                    artifactRefs,
                    VERIFIER_ID,
                    "Registrar-General authoritative verification (CRVS, X2)");

            log.info("Recorded AUTHORITATIVELY_VERIFIED proofing outcome for healthId={} (LOA {})",
                    healthId, ProofingOutcome.AUTHORITATIVELY_VERIFIED.assuranceLevel());
        } catch (Exception e) {
            // Never break the consumer on a malformed/foreign event.
            log.warn("Failed to process civil.identity.verified event: {}", e.getMessage());
        }
    }
}
