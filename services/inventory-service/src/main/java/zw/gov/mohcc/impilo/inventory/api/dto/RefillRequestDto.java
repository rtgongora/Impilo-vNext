package zw.gov.mohcc.impilo.inventory.api.dto;

import zw.gov.mohcc.impilo.inventory.domain.RefillStatus;
import zw.gov.mohcc.impilo.inventory.persistence.entity.RefillRequestEntity;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * DTO for a client refill request.
 */
public record RefillRequestDto(
        UUID refillId,
        String clientId,
        String itemCode,
        String itemName,
        int qtyRequested,
        RefillStatus status,
        UUID preferredFacilityId,
        String requestedBy,
        String notes,
        OffsetDateTime createdAt,
        OffsetDateTime fulfilledAt
) {
    public static RefillRequestDto from(RefillRequestEntity e) {
        return new RefillRequestDto(
                e.getRefillId(), e.getClientId(), e.getItemCode(), e.getItemName(), e.getQtyRequested(),
                e.getStatus(), e.getPreferredFacilityId(), e.getRequestedBy(), e.getNotes(),
                e.getCreatedAt(), e.getFulfilledAt());
    }
}
