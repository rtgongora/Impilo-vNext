package zw.gov.mohcc.impilo.pharmacy.api.dto;

import zw.gov.mohcc.impilo.pharmacy.domain.PickupMethod;
import zw.gov.mohcc.impilo.pharmacy.domain.PickupStatus;
import zw.gov.mohcc.impilo.pharmacy.persistence.entity.PickupProofEntity;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * DTO for pickup proof records. Never exposes the token hash.
 * Uses a static factory method to map from the entity.
 */
public record PickupProofDto(
        UUID proofId,
        PickupMethod method,
        PickupStatus status,
        OffsetDateTime expiresAt,
        String delegatedTo,
        String claimedBy,
        OffsetDateTime claimedAt,
        OffsetDateTime createdAt
) {
    /**
     * Factory method to create a pickup proof DTO from the entity.
     * Note: the token is never exposed in the API response.
     */
    public static PickupProofDto from(PickupProofEntity entity) {
        return new PickupProofDto(
                entity.getProofId(),
                entity.getMethod(),
                entity.getStatus(),
                entity.getExpiresAt(),
                entity.getDelegatedTo(),
                entity.getClaimedBy(),
                entity.getClaimedAt(),
                entity.getCreatedAt()
        );
    }
}
