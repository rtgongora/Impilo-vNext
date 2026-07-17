package zw.gov.mohcc.impilo.participation.core;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.participation.domain.ContributionType;
import zw.gov.mohcc.impilo.participation.persistence.entity.ContributionEntity;
import zw.gov.mohcc.impilo.participation.persistence.repository.ContributionRepository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Anonymous public contribution intake (gateway-public-lane ADR): a citizen submits an idea
 * or experience without an account and receives a one-time claim code; status is readable
 * ONLY with that code. The plaintext code is never stored — only its SHA-256 hash on the
 * contribution — and the reference alone never unlocks status (its 6-char suffix is far too
 * guessable to act as a secret).
 *
 * <p>Mirrors Rito's {@code PublicCaseIntakeService} exactly. The public lane is allow-listed
 * to IDEA and EXPERIENCE; anonymity is forced (no reporter identity is captured); public
 * contributions land with {@code moderation_state = PENDING} and are NOT shown on the public
 * board until a moderator approves them.</p>
 */
@Service
public class PublicContributionIntakeService {

    private static final SecureRandom RANDOM = new SecureRandom();
    /** Crockford-ish alphabet — no 0/O/1/I confusion for a code citizens may write down. */
    private static final char[] CODE_ALPHABET = "23456789ABCDEFGHJKMNPQRSTVWXYZ".toCharArray();
    private static final int CODE_LENGTH = 20;

    private final ContributionService contributionService;
    private final ContributionRepository contributionRepository;

    public PublicContributionIntakeService(ContributionService contributionService,
                                           ContributionRepository contributionRepository) {
        this.contributionService = contributionService;
        this.contributionRepository = contributionRepository;
    }

    public record PublicIntakeResult(UUID contributionId, String reference, String claimCode, String status) {}

    /** Disclosure-limited status view for the claim-code holder. */
    public record PublicContributionStatus(String reference, String type, String status,
                                           String moderationState, int supportCount,
                                           java.time.OffsetDateTime createdAt,
                                           java.time.OffsetDateTime updatedAt) {}

    @Transactional
    public PublicIntakeResult submitPublic(UUID tenantId, String type, String needArea, String title,
                                           String problemOrOpportunity, String betterExperience,
                                           String beneficiary, boolean willingToHelp, String contact) {
        String resolvedType = type == null || type.isBlank() ? ContributionType.IDEA : type;
        if (!ContributionType.PUBLIC_TYPES.contains(resolvedType)) {
            throw new IllegalArgumentException("type must be one of " + ContributionType.PUBLIC_TYPES);
        }
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("publicLane", true);
        if (contact != null && !contact.isBlank()) {
            // Volunteered contact goes into metadata only (never into a reporter identity).
            metadata.put("volunteeredContact", contact.trim());
        }

        ContributionEntity c = contributionService.create(tenantId, new ContributionService.NewContribution(
                resolvedType, needArea, title, problemOrOpportunity, betterExperience,
                beneficiary, willingToHelp, "ANONYMOUS", null, metadata));

        String claimCode = newClaimCode();
        c.setClaimCodeHash(sha256Hex(claimCode));
        contributionRepository.save(c);
        return new PublicIntakeResult(c.getId(), c.getReference(), claimCode, c.getStatus());
    }

    @Transactional(readOnly = true)
    public Optional<PublicContributionStatus> statusByClaimCode(UUID tenantId, String claimCode) {
        if (claimCode == null || claimCode.isBlank() || claimCode.length() > 64) {
            return Optional.empty();
        }
        return contributionRepository.findByTenantIdAndClaimCodeHash(tenantId, sha256Hex(claimCode.trim()))
                .map(c -> new PublicContributionStatus(c.getReference(), c.getType(), c.getStatus(),
                        c.getModerationState(), c.getSupportCount() == null ? 0 : c.getSupportCount(),
                        c.getCreatedAt(), c.getUpdatedAt()));
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
