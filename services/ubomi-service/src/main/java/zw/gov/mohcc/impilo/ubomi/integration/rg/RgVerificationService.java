package zw.gov.mohcc.impilo.ubomi.integration.rg;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.ubomi.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.ubomi.persistence.repository.EventOutboxRepository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Outbound RG verification orchestration (W1b/W1c).
 *
 * <p>Every interaction is recorded in the {@code ubomi.rg_verification} ledger
 * and, on an authoritative outcome, emits a governed {@code civil.*} event via
 * the outbox (cpid-free, opaque token + status only). Trust posture:</p>
 * <ul>
 *   <li><b>Fail-open-for-care</b> — if the RG is disabled (no client bean) or
 *       unavailable ({@link RgUnavailableException}), the attempt is parked
 *       {@code RG_PENDING}; the call never throws to the caller and never blocks
 *       registration or care. The reconcile loop ({@link RgOutboundRelay})
 *       re-drives pending rows and expires them to {@code RG_UNAVAILABLE} after
 *       {@code ubomi.rg.pending-ttl-hours} — an honest status, never a
 *       fabricated verification.</li>
 *   <li><b>Response minimisation</b> — only the opaque {@code civilIdentityToken}
 *       and a one-way hash of the identifier are persisted; the raw identifier
 *       and the full RG record never leave {@link RegistrarGeneralClient}.</li>
 *   <li><b>Idempotency</b> — keyed by (tenant, type, subject, referenceHash);
 *       a terminal row short-circuits redelivery.</li>
 * </ul>
 */
@Service
public class RgVerificationService {

    private static final Logger log = LoggerFactory.getLogger(RgVerificationService.class);
    private static final String AUDIT_ACTOR = "ubomi-rg-relay";

    private final RgVerificationRepository ledger;
    private final EventOutboxRepository outbox;
    private final ObjectProvider<RegistrarGeneralClient> clientProvider;
    private final ObjectMapper objectMapper;

    public RgVerificationService(RgVerificationRepository ledger,
                                 EventOutboxRepository outbox,
                                 ObjectProvider<RegistrarGeneralClient> clientProvider,
                                 ObjectMapper objectMapper) {
        this.ledger = ledger;
        this.outbox = outbox;
        this.clientProvider = clientProvider;
        this.objectMapper = objectMapper;
    }

    /** Minimal result surfaced to callers — opaque token + status, never PII. */
    public record RgVerificationResult(String status, boolean matched, String civilIdentityToken) {}

    /**
     * Verify an identifier for a subject (Health ID) against the RG. Never throws
     * on RG failure — parks {@code RG_PENDING} for reconcile (fail-open-for-care).
     *
     * @param subjectHealthId the internal person anchor the assurance attaches to (not civil PII)
     * @param verificationType NATIONAL_ID | PASSPORT | BIRTH_REGISTRATION
     * @param identifier       the raw identifier — transmitted to RG, never stored
     */
    @Transactional
    public RgVerificationResult verify(UUID tenantId, String subjectHealthId, String verificationType,
                                       String identifier, String fullName, String dateOfBirth) {
        String referenceHash = sha256Hex(identifier);
        String businessKey = verificationType + ":" + subjectHealthId + ":" + referenceHash;

        RgVerificationEntity row = ledger.findByTenantIdAndBusinessKey(tenantId, businessKey)
                .orElseGet(() -> {
                    RgVerificationEntity e = new RgVerificationEntity();
                    e.setTenantId(tenantId);
                    e.setSubjectRef(subjectHealthId);
                    e.setVerificationType(verificationType);
                    e.setReferenceHash(referenceHash);
                    e.setBusinessKey(businessKey);
                    e.setStatus(RgVerificationEntity.RG_PENDING);
                    return e;
                });

        // Idempotency: a terminal row is returned as-is (at-least-once tolerant).
        if (RgVerificationEntity.RG_VERIFIED.equals(row.getStatus())
                || RgVerificationEntity.RG_NO_MATCH.equals(row.getStatus())) {
            return toResult(row);
        }

        RegistrarGeneralClient client = clientProvider.getIfAvailable();
        if (client == null) {
            // RG disabled — fail-open to the native ladder; park pending.
            row.setLastError("RG disabled (ubomi.rg.enabled=false)");
            row = ledger.save(row);
            log.debug("RG disabled; parked RG_PENDING for subject={} type={}", subjectHealthId, verificationType);
            return toResult(row);
        }

        row.setAttemptCount(row.getAttemptCount() + 1);
        row.setLastAttemptAt(OffsetDateTime.now());
        try {
            RgVerificationOutcome outcome = client.verify(verificationType, identifier, fullName, dateOfBirth);
            if (outcome.matched()) {
                row.setStatus(RgVerificationEntity.RG_VERIFIED);
                row.setCivilIdentityToken(outcome.civilIdentityToken());
                row.setVerifiedAt(OffsetDateTime.now());
                row.setLastError(null);
                row = ledger.save(row);
                emitCivilEvent("CIVIL_IDENTITY", subjectHealthId, "CIVIL_IDENTITY_VERIFIED",
                        tenantId, subjectHealthId, Map.of(
                                "tenantId", tenantId.toString(),
                                "healthId", subjectHealthId,
                                "status", "VERIFIED",
                                "civilIdentityToken", outcome.civilIdentityToken(),
                                "verifiedAt", row.getVerifiedAt().toString()));
                log.info("RG verified subject={} type={}", subjectHealthId, verificationType);
            } else {
                row.setStatus(RgVerificationEntity.RG_NO_MATCH);
                row.setLastError(null);
                row = ledger.save(row);
                log.info("RG no-match subject={} type={}", subjectHealthId, verificationType);
            }
        } catch (RgUnavailableException e) {
            // Fail-open: stay RG_PENDING, record the error, do NOT rethrow.
            row.setStatus(RgVerificationEntity.RG_PENDING);
            row.setLastError(truncate(e.getMessage()));
            row = ledger.save(row);
            log.warn("RG unavailable; parked RG_PENDING subject={} type={}: {}",
                    subjectHealthId, verificationType, e.getMessage());
        }
        return toResult(row);
    }

    /**
     * Re-drive a single parked ({@code RG_PENDING}) row. Called by the reconcile
     * loop under its own per-row transaction; never throws.
     *
     * @return true if the row reached a terminal state.
     */
    @Transactional
    public boolean reDrive(Long rowId, int pendingTtlHours) {
        RgVerificationEntity row = ledger.findById(rowId).orElse(null);
        if (row == null || !RgVerificationEntity.RG_PENDING.equals(row.getStatus())) {
            return false;
        }
        // TTL expiry — honest terminal status, never a fabricated verification.
        if (row.getCreatedAt() != null
                && row.getCreatedAt().plusHours(pendingTtlHours).isBefore(OffsetDateTime.now())) {
            row.setStatus(RgVerificationEntity.RG_UNAVAILABLE);
            ledger.save(row);
            log.info("RG attempt expired to RG_UNAVAILABLE subject={} type={}",
                    row.getSubjectRef(), row.getVerificationType());
            return true;
        }
        RegistrarGeneralClient client = clientProvider.getIfAvailable();
        if (client == null) {
            return false; // still disabled — wait for TTL
        }
        row.setAttemptCount(row.getAttemptCount() + 1);
        row.setLastAttemptAt(OffsetDateTime.now());
        try {
            // We only hold the hash, so a re-drive of a raw-identifier verify is
            // not possible; re-drive uses the confirm-by-hash RG endpoint.
            RgVerificationOutcome outcome =
                    client.confirmRegistration(row.getVerificationType(), row.getReferenceHash());
            if (outcome.matched()) {
                row.setStatus(RgVerificationEntity.RG_VERIFIED);
                row.setCivilIdentityToken(outcome.civilIdentityToken());
                row.setVerifiedAt(OffsetDateTime.now());
                row.setLastError(null);
                ledger.save(row);
                emitCivilEvent("CIVIL_IDENTITY", row.getSubjectRef(), "CIVIL_IDENTITY_VERIFIED",
                        row.getTenantId(), row.getSubjectRef(), Map.of(
                                "tenantId", row.getTenantId().toString(),
                                "healthId", row.getSubjectRef(),
                                "status", "VERIFIED",
                                "civilIdentityToken", outcome.civilIdentityToken(),
                                "verifiedAt", row.getVerifiedAt().toString()));
                return true;
            }
            ledger.save(row);
            return false;
        } catch (RgUnavailableException e) {
            row.setLastError(truncate(e.getMessage()));
            ledger.save(row);
            return false;
        }
    }

    /** Record an identity dispute and emit {@code civil.identity.disputed}. */
    @Transactional
    public void dispute(UUID tenantId, String subjectHealthId, String reason) {
        emitCivilEvent("CIVIL_IDENTITY", subjectHealthId, "CIVIL_IDENTITY_DISPUTED",
                tenantId, subjectHealthId, Map.of(
                        "status", "DISPUTED",
                        "reason", reason != null ? reason : ""));
        log.info("Emitted civil.identity.disputed for subject={}", subjectHealthId);
    }

    /** Record an identity correction and emit {@code civil.identity.corrected}. */
    @Transactional
    public void correct(UUID tenantId, String subjectHealthId, String civilIdentityToken) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("status", "CORRECTED");
        if (civilIdentityToken != null) payload.put("civilIdentityToken", civilIdentityToken);
        emitCivilEvent("CIVIL_IDENTITY", subjectHealthId, "CIVIL_IDENTITY_CORRECTED",
                tenantId, subjectHealthId, payload);
        log.info("Emitted civil.identity.corrected for subject={}", subjectHealthId);
    }

    /** Emit {@code civil.birth.confirmed} for a registered birth notification. */
    @Transactional
    public void confirmBirth(UUID tenantId, String notificationRef, String civilIdentityToken) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("status", "CONFIRMED");
        if (civilIdentityToken != null) payload.put("civilIdentityToken", civilIdentityToken);
        emitCivilEvent("CIVIL_BIRTH", notificationRef, "CIVIL_BIRTH_CONFIRMED",
                tenantId, notificationRef, payload);
    }

    /** Emit {@code civil.death.confirmed} for a registered death notification. */
    @Transactional
    public void confirmDeath(UUID tenantId, String notificationRef, String civilIdentityToken) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("status", "CONFIRMED");
        if (civilIdentityToken != null) payload.put("civilIdentityToken", civilIdentityToken);
        emitCivilEvent("CIVIL_DEATH", notificationRef, "CIVIL_DEATH_CONFIRMED",
                tenantId, notificationRef, payload);
    }

    // ── helpers ──

    private RgVerificationResult toResult(RgVerificationEntity row) {
        return new RgVerificationResult(row.getStatus(),
                RgVerificationEntity.RG_VERIFIED.equals(row.getStatus()),
                row.getCivilIdentityToken());
    }

    private void emitCivilEvent(String aggregateType, String aggregateId, String eventType,
                                UUID tenantId, String subjectId, Map<String, Object> payload) {
        try {
            EventOutboxEntity event = new EventOutboxEntity();
            event.setAggregateType(aggregateType);
            event.setAggregateId(aggregateId);
            event.setEventType(eventType);
            event.setTenantId(tenantId);
            event.setSubjectType(aggregateType);
            event.setSubjectId(subjectId);
            event.setPartitionKey(subjectId);
            event.setProducer(AUDIT_ACTOR);
            event.setPayload(objectMapper.writeValueAsString(payload));
            outbox.save(event);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize civil.* event payload", e);
        }
    }

    private static String truncate(String s) {
        if (s == null) return null;
        return s.length() > 900 ? s.substring(0, 900) : s;
    }

    private static String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 hashing failed", e);
        }
    }
}
