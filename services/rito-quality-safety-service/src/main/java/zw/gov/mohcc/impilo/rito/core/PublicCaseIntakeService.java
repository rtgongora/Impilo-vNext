package zw.gov.mohcc.impilo.rito.core;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.rito.domain.CaseClassification;
import zw.gov.mohcc.impilo.rito.persistence.entity.CaseEntity;
import zw.gov.mohcc.impilo.rito.persistence.repository.CaseRepository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;
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

    /** Public lane accepts only these case types (ADR: allow-listed DTO). */
    public static final Set<String> PUBLIC_CASE_TYPES =
            Set.of(CaseClassification.COMPLAINT, CaseClassification.SAFETY_CONCERN);

    private static final SecureRandom RANDOM = new SecureRandom();
    /** Crockford-ish alphabet — no 0/O/1/I confusion for a code citizens may write down. */
    private static final char[] CODE_ALPHABET = "23456789ABCDEFGHJKMNPQRSTVWXYZ".toCharArray();
    private static final int CODE_LENGTH = 20;

    private final CaseService caseService;
    private final CaseRepository caseRepository;

    public PublicCaseIntakeService(CaseService caseService, CaseRepository caseRepository) {
        this.caseService = caseService;
        this.caseRepository = caseRepository;
    }

    public record PublicIntakeResult(UUID caseId, String caseReference, String claimCode, String status) {}

    /** Disclosure-limited status view for the claim-code holder. */
    public record PublicCaseStatus(String caseReference, String caseType, String status,
                                   String severity, java.time.OffsetDateTime createdAt,
                                   java.time.OffsetDateTime updatedAt) {}

    @Transactional
    public PublicIntakeResult openPublicCase(UUID tenantId, String caseType, String title,
                                             String description, UUID facilityId, String contact) {
        if (caseType == null || !PUBLIC_CASE_TYPES.contains(caseType)) {
            throw new IllegalArgumentException("caseType must be one of " + PUBLIC_CASE_TYPES);
        }
        boolean hasContact = contact != null && !contact.isBlank();
        Map<String, Object> metadata = hasContact
                ? Map.of("publicLane", true, "volunteeredContact", contact.trim())
                : Map.of("publicLane", true);

        CaseEntity c = caseService.createCase(tenantId, new CaseService.NewCase(
                caseType, title, description, null, "WEB_PORTAL", null,
                true /* anonymous: reporter identity is never captured on this lane */,
                facilityId, null, null, null,
                null, null, null, false, metadata));

        String claimCode = newClaimCode();
        c.setClaimCodeHash(sha256Hex(claimCode));
        caseRepository.save(c);
        return new PublicIntakeResult(c.getId(), c.getCaseReference(), claimCode, c.getStatus());
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
