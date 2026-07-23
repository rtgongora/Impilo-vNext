package zw.gov.mohcc.impilo.pharmacy.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import zw.gov.mohcc.impilo.pharmacy.domain.PickupMethod;
import zw.gov.mohcc.impilo.pharmacy.domain.PickupStatus;
import zw.gov.mohcc.impilo.pharmacy.domain.PickupVerificationGrade;
import zw.gov.mohcc.impilo.pharmacy.persistence.entity.PickupProofEntity;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * DTO for pickup proof records. Never exposes the token hash.
 * Uses a static factory method to map from the entity.
 *
 * <p>M3 BUG-5 — {@code oneTimeCredential}: the plaintext OTP / QR token /
 * SMS reference is returned EXACTLY ONCE, in the creation response only
 * (via {@link #created}). It is the delivery seam for the patient/SMS lane;
 * only the HMAC hash is ever stored, and every read/claim path maps through
 * {@link #from}, which never carries it. Before this fix the generated
 * credential was dropped entirely — pickup claim and SMS resolve were
 * undeliverable on the live path.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PickupProofDto(
        UUID proofId,
        PickupMethod method,
        PickupStatus status,
        OffsetDateTime expiresAt,
        String delegatedTo,
        String claimedBy,
        OffsetDateTime claimedAt,
        OffsetDateTime createdAt,
        String collectorIdentity,
        PickupVerificationGrade verificationGrade,
        String namedRecipient,
        String oneTimeCredential
) {
    /**
     * Factory method to create a pickup proof DTO from the entity.
     * Note: the token is never exposed in the API response.
     */
    public static PickupProofDto from(PickupProofEntity entity) {
        return build(entity, null);
    }

    /**
     * Creation-response mapping ONLY (M3 BUG-5): carries the plaintext
     * one-time credential the service just generated. Must never be used on
     * a read or claim path.
     */
    public static PickupProofDto created(PickupProofEntity entity, String oneTimeCredential) {
        return build(entity, oneTimeCredential);
    }

    private static PickupProofDto build(PickupProofEntity entity, String oneTimeCredential) {
        return new PickupProofDto(
                entity.getProofId(),
                entity.getMethod(),
                entity.getStatus(),
                entity.getExpiresAt(),
                entity.getDelegatedTo(),
                entity.getClaimedBy(),
                entity.getClaimedAt(),
                entity.getCreatedAt(),
                entity.getCollectorIdentity(),
                entity.getVerificationGrade(),
                entity.getNamedRecipient(),
                oneTimeCredential
        );
    }
}
