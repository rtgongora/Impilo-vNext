package zw.gov.mohcc.impilo.inventory.api.dto;

import zw.gov.mohcc.impilo.inventory.persistence.entity.OnHandEntity;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * DTO representing a materialised on-hand stock position.
 *
 * @param id         the on-hand record identifier
 * @param facilityId the facility identifier
 * @param storeId    the store identifier
 * @param binId      the bin identifier (may be null)
 * @param itemCode   the item code
 * @param batch      the batch number
 * @param expiry     the expiry date
 * @param qtyOnHand  the current quantity on hand
 * @param updatedAt  when the position was last updated
 */
public record OnHandDto(
        UUID id,
        UUID facilityId,
        UUID storeId,
        UUID binId,
        String itemCode,
        String batch,
        LocalDate expiry,
        int qtyOnHand,
        OffsetDateTime updatedAt
) {

    /**
     * Map an OnHandEntity to an OnHandDto.
     *
     * @param entity the on-hand entity
     * @return the DTO representation
     */
    public static OnHandDto from(OnHandEntity entity) {
        return new OnHandDto(
                entity.getId(),
                entity.getFacilityId(),
                entity.getStoreId(),
                entity.getBinId(),
                entity.getItemCode(),
                entity.getBatch(),
                entity.getExpiry(),
                entity.getQtyOnHand(),
                entity.getUpdatedAt()
        );
    }
}
