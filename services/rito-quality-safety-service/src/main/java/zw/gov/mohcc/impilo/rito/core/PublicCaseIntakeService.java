package zw.gov.mohcc.impilo.rito.core;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.rito.domain.CaseClassification;
import zw.gov.mohcc.impilo.rito.domain.PublicFeedbackRoutingPolicy;
import zw.gov.mohcc.impilo.rito.events.RitoEventEmitter;
import zw.gov.mohcc.impilo.rito.persistence.entity.CaseEntity;
import zw.gov.mohcc.impilo.rito.persistence.repository.CaseRepository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Anonymous public case intake (gateway-public-lane ADR, W4 {@code gateway-feedback-claim}):
 * a citizen opens a complaint or safety concern without an account and receives a one-time
 * claim code; status is readable ONLY with that code. The plaintext code is never stored —
 * only its SHA-256 hash on the case — and the case reference alone never unlocks status
 * (its 6-char suffix is far too guessable to act as a secret).
 *
 * <p>The lane is allow-listed to the two public case types; anonymity is forced when no
 * contact is volunteered, and volunteered contact goes into case metadata (never into
 * reporter identity, which stays null on this lane).</p>
 */
@Service
public class PublicCaseIntakeService {

    /**
     * Public lane allow-list (ADR: allow-listed DTO). Complaint + safety concern are the original
     * two; compliment + suggestion widen the lane for the plain thank-you / idea paths. Clinical-only
     * types (SAFETY_INCIDENT / ADVERSE_EVENT) are deliberately NOT reachable from the anonymous lane.
     */
    public static final Set<String> PUBLIC_CASE_TYPES = Set.of(
            CaseClassification.COMPLAINT, CaseClassification.SAFETY_CONCERN,
            CaseClassification.COMPLIMENT, CaseClassification.SUGGESTION);

    /** Routed public-feedback outbox event carrying the operational owner for that team's queue. */
    public static final String FEEDBACK_ROUTED_EVENT = "rito.public.feedback.routed";

    /** Outbox event emitted for every anonymous public submission (routed or plain). */
    public static final String FEEDBACK_RECEIVED_EVENT = "rito.public.feedback.received";

    private static final SecureRandom RANDOM = new SecureRandom();
    /** Crockford-ish alphabet — no 0/O/1/I confusion for a code citizens may write down. */
    private static final char[] CODE_ALPHABET = "23456789ABCDEFGHJKMNPQRSTVWXYZ".toCharArray();
    private static final int CODE_LENGTH = 20;

    private final CaseService caseService;
    private final CaseRepository caseRepository;
    private final RitoEventEmitter emitter;

    public PublicCaseIntakeService(CaseService caseService, CaseRepository caseRepository,
                                   RitoEventEmitter emitter) {
        this.caseService = caseService;
        this.caseRepository = caseRepository;
        this.emitter = emitter;
    }

    public record PublicIntakeResult(UUID caseId, String caseReference, String claimCode, String status) {}

    /** Disclosure-limited status view for the claim-code holder. */
    public record PublicCaseStatus(String caseReference, String caseType, String status,
                                   String severity, java.time.OffsetDateTime createdAt,
                                   java.time.OffsetDateTime updatedAt) {}

    /** Backward-compatible entry (no category, no incident ref). */
    @Transactional
    public PublicIntakeResult openPublicCase(UUID tenantId, String caseType, String title,
                                             String description, UUID facilityId, String contact) {
        return openPublicCase(tenantId, caseType, null, title, description, facilityId, contact, null);
    }

    /**
     * Anonymous public intake with operational-owner routing.
     *
     * <p>When {@code feedbackCategory} is present it wins: the routing policy derives the canonical
     * caseType, the operational owner and the triage priority (which also sets case severity so the
     * existing SLA / critical path applies). Without a category the caller-supplied {@code caseType}
     * must be in {@link #PUBLIC_CASE_TYPES}. An optional {@code incidentRef} lets several citizen
     * reports be associated with one known incident (stored on the case; no incident engine).</p>
     */
    @Transactional
    public PublicIntakeResult openPublicCase(UUID tenantId, String caseType, String feedbackCategory,
                                             String title, String description, UUID facilityId,
                                             String contact, String incidentRef) {
        String effectiveCaseType;
        String operationalOwner = null;
        String triagePriority = null;
        String severity = null;

        boolean hasCategory = feedbackCategory != null && !feedbackCategory.isBlank();
        if (hasCategory) {
            PublicFeedbackRoutingPolicy.Routing routing =
                    PublicFeedbackRoutingPolicy.resolve(feedbackCategory);
            if (routing == null) {
                throw new IllegalArgumentException("Unknown feedbackCategory: " + feedbackCategory);
            }
            effectiveCaseType = routing.caseType();
            operationalOwner = routing.operationalOwner();
            triagePriority = routing.triagePriority();
            severity = PublicFeedbackRoutingPolicy.severityFor(triagePriority);
        } else {
            if (caseType == null || !PUBLIC_CASE_TYPES.contains(caseType)) {
                throw new IllegalArgumentException("caseType must be one of " + PUBLIC_CASE_TYPES);
            }
            effectiveCaseType = caseType;
        }

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("publicLane", true);
        if (contact != null && !contact.isBlank()) {
            metadata.put("volunteeredContact", contact.trim());
        }
        if (hasCategory) {
            metadata.put("feedbackCategory", feedbackCategory);
            metadata.put("operationalOwner", operationalOwner);
            metadata.put("triagePriority", triagePriority);
        }
        if (incidentRef != null && !incidentRef.isBlank()) {
            metadata.put("incidentRef", incidentRef.trim());
        }

        CaseEntity c = caseService.createCase(tenantId, new CaseService.NewCase(
                effectiveCaseType, title, description, severity, "WEB_PORTAL", null,
                true /* anonymous: reporter identity is never captured on this lane */,
                facilityId, null, null, null,
                null, null, null, false, metadata));

        c.setFeedbackCategory(hasCategory ? feedbackCategory : null);
        c.setOperationalOwner(operationalOwner);
        c.setTriagePriority(triagePriority);

        String claimCode = newClaimCode();
        c.setClaimCodeHash(sha256Hex(claimCode));
        caseRepository.save(c);

        emitFeedbackRouted(c, feedbackCategory, operationalOwner, triagePriority,
                incidentRef == null || incidentRef.isBlank() ? null : incidentRef.trim());

        return new PublicIntakeResult(c.getId(), c.getCaseReference(), claimCode, c.getStatus());
    }

    /**
     * Emits the routed outbox event so the owning team's governed queue can consume the report.
     * Always emits a {@code received} event; when an operational owner was routed it emits the
     * {@code routed} event carrying that owner + triage priority.
     */
    private void emitFeedbackRouted(CaseEntity c, String feedbackCategory, String operationalOwner,
                                    String triagePriority, String incidentRef) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("caseId", c.getId().toString());
        payload.put("caseReference", c.getCaseReference());
        payload.put("caseType", c.getCaseType());
        payload.put("feedbackCategory", feedbackCategory);
        payload.put("operationalOwner", operationalOwner);
        payload.put("triagePriority", triagePriority);
        payload.put("facilityId", c.getFacilityId() != null ? c.getFacilityId().toString() : null);
        payload.put("incidentRef", incidentRef);

        String eventType = operationalOwner != null ? FEEDBACK_ROUTED_EVENT : FEEDBACK_RECEIVED_EVENT;
        emitter.emit("CASE", c.getId().toString(), eventType, "CASE", c.getId().toString(),
                payload, c.getTenantId());
    }

    @Transactional(readOnly = true)
    public Optional<PublicCaseStatus> statusByClaimCode(UUID tenantId, String claimCode) {
        if (claimCode == null || claimCode.isBlank() || claimCode.length() > 64) {
            return Optional.empty();
        }
        return caseRepository.findByTenantIdAndClaimCodeHash(tenantId, sha256Hex(claimCode.trim()))
                .map(c -> new PublicCaseStatus(c.getCaseReference(), c.getCaseType(), c.getStatus(),
                        c.getSeverity(), c.getCreatedAt(), c.getUpdatedAt()));
    }

    static String newClaimCode() {
        StringBuilder sb = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            sb.append(CODE_ALPHABET[RANDOM.nextInt(CODE_ALPHABET.length)]);
        }
        return sb.toString();
    }

    static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
