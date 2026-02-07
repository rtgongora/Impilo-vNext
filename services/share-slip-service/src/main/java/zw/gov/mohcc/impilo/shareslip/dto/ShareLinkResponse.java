package zw.gov.mohcc.impilo.shareslip.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Response representing a share link with its current state.
 *
 * @param linkId               unique link identifier (UUID)
 * @param tenantId             tenant that owns this link
 * @param shareToken           the shareable token (used in QR codes and URLs)
 * @param subjectType          type of shared subject
 * @param subjectId            identifier of the shared subject
 * @param purpose              purpose of the share
 * @param documentIds          list of document IDs being shared
 * @param verificationMethod   OTP or QR_SCAN
 * @param maxClaims            maximum allowed claims
 * @param claimCount           current claim count
 * @param status               ACTIVE, CLAIMED, EXPIRED, or REVOKED
 * @param expiresAt            when the link expires
 * @param createdBy            who created the link
 * @param createdAt            when the link was created
 */
public record ShareLinkResponse(
        UUID linkId,
        UUID tenantId,
        String shareToken,
        String subjectType,
        String subjectId,
        String purpose,
        List<UUID> documentIds,
        String verificationMethod,
        int maxClaims,
        int claimCount,
        String status,
        OffsetDateTime expiresAt,
        String createdBy,
        OffsetDateTime createdAt
) {
}
