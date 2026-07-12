package zw.gov.mohcc.impilo.ia.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.ia.persistence.entity.AttestationEntity;
import zw.gov.mohcc.impilo.ia.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.ia.persistence.repository.AttestationRepository;
import zw.gov.mohcc.impilo.ia.persistence.repository.EventOutboxRepository;

/**
 * R1 "Reachable" rung of the gateway trust ladder: records and reads the
 * {@link AttestationType#CONTACT_VERIFIED} attestation — proof that a phone or email
 * contact channel was verified for an account via an out-of-band one-time code.
 *
 * <p><strong>Verifier boundary.</strong> {@link AttestationService#recordAttestation} is
 * deliberately fail-closed (claims land as PENDING because the caller is not a verifier).
 * This service is the deliberate integration seam that javadoc refers to for the contact
 * channel: the caller is a trusted platform service (the experience BFF) that has itself
 * completed the OTP check against the contact, service-to-service behind ext_authz. The
 * OTP check IS the real verifier, so the attestation is recorded as VERIFIED here.</p>
 *
 * <p><strong>Assurance boundary.</strong> A CONTACT_VERIFIED attestation does NOT raise the
 * assurance level. R1 = LOA1 + this attestation (doctrine §4.2); LOA raises remain the
 * dual-controlled {@link AssuranceService} upgrade workflow.</p>
 *
 * <p><strong>PII boundary.</strong> Only the masked contact value is accepted and stored —
 * never the raw phone number or email. Defensively, an unmasked-looking value is masked
 * server-side before persisting.</p>
 */
@Service
public class ContactVerificationService {

    /** Supported contact channels for the R1 rung. */
    private static final Set<String> CHANNELS = Set.of("PHONE", "EMAIL");

    private final AttestationRepository attestationRepository;
    private final EventOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public ContactVerificationService(AttestationRepository attestationRepository,
                                      EventOutboxRepository outboxRepository,
                                      ObjectMapper objectMapper) {
        this.attestationRepository = attestationRepository;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    /** One verified contact channel of an account. */
    public record VerifiedChannel(String channel, String maskedValue, String method,
                                  OffsetDateTime verifiedAt) {}

    /** The contact-verification status surfaced to policy and to SOS-callback flows. */
    public record ContactVerificationView(String accountId, boolean contactVerified,
                                          List<VerifiedChannel> channels) {}

    /**
     * Record that {@code accountId}'s contact channel was verified (method = OTP unless
     * stated otherwise). Idempotent per (account, channel, maskedValue): re-recording an
     * already-verified contact returns the existing attestation instead of duplicating it.
     *
     * @param accountId  the subject account (Keycloak user id / actor id) — passed explicitly
     *                   because during registration the subject is not yet the caller
     * @param recordedBy the calling principal from trust context (audit)
     */
    @Transactional
    public AttestationEntity recordContactVerified(UUID tenantId, String accountId,
                                                   String recordedBy, UUID correlationId,
                                                   String channel, String maskedValue,
                                                   String method) {
        if (accountId == null || accountId.isBlank()) {
            throw new IllegalArgumentException("accountId is required");
        }
        String normalizedChannel = channel == null ? "" : channel.trim().toUpperCase();
        if (!CHANNELS.contains(normalizedChannel)) {
            throw new IllegalArgumentException("channel must be one of " + CHANNELS);
        }
        if (maskedValue == null || maskedValue.isBlank()) {
            throw new IllegalArgumentException("maskedValue is required");
        }
        String safeMasked = ensureMasked(maskedValue.trim());
        String effectiveMethod = (method == null || method.isBlank()) ? "OTP" : method.trim().toUpperCase();

        // Idempotency: same account + channel + masked value already VERIFIED → return it.
        for (AttestationEntity existing : attestationRepository
                .findByTenantIdAndActorId(tenantId, accountId)) {
            if (existing.getAttestationType() == AttestationType.CONTACT_VERIFIED
                    && existing.getOutcome() == AttestationOutcome.VERIFIED) {
                JsonNode evidence = parseEvidence(existing.getEvidence());
                if (normalizedChannel.equals(evidence.path("channel").asText())
                        && safeMasked.equals(evidence.path("maskedValue").asText())) {
                    return existing;
                }
            }
        }

        OffsetDateTime verifiedAt = OffsetDateTime.now();
        ObjectNode evidence = objectMapper.createObjectNode();
        evidence.put("channel", normalizedChannel);
        evidence.put("maskedValue", safeMasked);
        evidence.put("method", effectiveMethod);
        evidence.put("verifiedAt", verifiedAt.toString());

        AttestationEntity entity = new AttestationEntity();
        entity.setTenantId(tenantId);
        entity.setActorId(accountId);
        entity.setAttestationType(AttestationType.CONTACT_VERIFIED);
        entity.setEvidence(evidence.toString());
        // The caller (BFF) completed the OTP check — this endpoint is the verifier seam,
        // so the outcome is VERIFIED (deterministic possession proof of the channel).
        entity.setOutcome(AttestationOutcome.VERIFIED);
        entity.setConfidence(BigDecimal.ONE);
        entity.setRecordedBy(recordedBy != null && !recordedBy.isBlank() ? recordedBy : "experience-bff");
        entity = attestationRepository.save(entity);

        publishEvent(entity, tenantId, correlationId);
        return entity;
    }

    /** Does {@code accountId} have a verified contact, and on which channel(s)? */
    @Transactional(readOnly = true)
    public ContactVerificationView getContactVerification(UUID tenantId, String accountId) {
        List<VerifiedChannel> channels = new ArrayList<>();
        for (AttestationEntity attestation : attestationRepository
                .findByTenantIdAndActorId(tenantId, accountId)) {
            if (attestation.getAttestationType() != AttestationType.CONTACT_VERIFIED
                    || attestation.getOutcome() != AttestationOutcome.VERIFIED) {
                continue;
            }
            JsonNode evidence = parseEvidence(attestation.getEvidence());
            OffsetDateTime verifiedAt = attestation.getCreatedAt();
            String verifiedAtText = evidence.path("verifiedAt").asText(null);
            if (verifiedAtText != null) {
                try {
                    verifiedAt = OffsetDateTime.parse(verifiedAtText);
                } catch (Exception ignored) {
                    // fall back to createdAt
                }
            }
            channels.add(new VerifiedChannel(
                    evidence.path("channel").asText(null),
                    evidence.path("maskedValue").asText(null),
                    evidence.path("method").asText("OTP"),
                    verifiedAt));
        }
        return new ContactVerificationView(accountId, !channels.isEmpty(), List.copyOf(channels));
    }

    /**
     * Defensive PII guard: callers must send masked values; if a value without any masking
     * character slips through, mask it here rather than persisting a raw contact.
     */
    static String ensureMasked(String value) {
        if (value.indexOf('*') >= 0) {
            return value;
        }
        int at = value.indexOf('@');
        if (at > 0) { // email: keep first char + domain
            return value.charAt(0) + "***" + value.substring(at);
        }
        if (value.length() > 5) { // phone: keep leading 3 + trailing 2
            return value.substring(0, 3) + "*".repeat(value.length() - 5)
                    + value.substring(value.length() - 2);
        }
        return "*".repeat(value.length());
    }

    private JsonNode parseEvidence(String evidence) {
        try {
            return objectMapper.readTree(evidence == null ? "{}" : evidence);
        } catch (Exception e) {
            return objectMapper.createObjectNode();
        }
    }

    private void publishEvent(AttestationEntity entity, UUID tenantId, UUID correlationId) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("id", entity.getId());
        payload.put("tenantId", tenantId.toString());
        payload.put("accountId", entity.getActorId());
        payload.put("attestationType", AttestationType.CONTACT_VERIFIED.name());
        payload.put("outcome", entity.getOutcome().name());
        JsonNode evidence = parseEvidence(entity.getEvidence());
        payload.put("channel", evidence.path("channel").asText(null));
        payload.put("method", evidence.path("method").asText(null));

        EventOutboxEntity event = new EventOutboxEntity();
        event.setAggregateType("ATTESTATION");
        event.setAggregateId(entity.getId().toString());
        event.setEventType("CONTACT_VERIFIED_RECORDED");
        event.setPayload(payload.toString());
        event.setTenantId(tenantId.toString());
        event.setCorrelationId(correlationId != null ? correlationId.toString() : null);
        outboxRepository.save(event);
    }
}
