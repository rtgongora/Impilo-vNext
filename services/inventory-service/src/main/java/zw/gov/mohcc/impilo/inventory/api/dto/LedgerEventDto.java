package zw.gov.mohcc.impilo.inventory.api.dto;

import zw.gov.mohcc.impilo.inventory.domain.LedgerEventType;
import zw.gov.mohcc.impilo.inventory.persistence.entity.LedgerEventEntity;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * DTO representing an immutable stock ledger event.
 *
 * @param eventId        the event identifier
 * @param facilityId     the facility identifier
 * @param storeId        the store identifier
 * @param binId          the bin identifier (may be null)
 * @param itemCode       the item code
 * @param batch          the batch number
 * @param expiry         the expiry date
 * @param qtyDelta       the signed quantity change
 * @param uom            the unit of measure
 * @param eventType      the type of ledger event
 * @param reason         the reason for the event
 * @param refType        the reference type (e.g. ORDER, TRANSFER, COUNT)
 * @param refId          the reference identifier
 * @param idempotencyKey the idempotency key for deduplication
 * @param actorId        the actor who initiated the event
 * @param createdAt      when the event was recorded
 */
public record LedgerEventDto(
        UUID eventId,
        UUID facilityId,
        UUID storeId,
        UUID binId,
        String itemCode,
        String batch,
        LocalDate expiry,
        int qtyDelta,
        String uom,
        LedgerEventType eventType,
        String reason,
        String refType,
        String refId,
        String idempotencyKey,
        String actorId,
        OffsetDateTime createdAt
) {

    /**
     * Map a LedgerEventEntity to a LedgerEventDto.
     *
     * @param entity the ledger event entity
     * @return the DTO representation
     */
    public static LedgerEventDto from(LedgerEventEntity entity) {
        return new LedgerEventDto(
                entity.getEventId(),
                entity.getFacilityId(),
                entity.getStoreId(),
                entity.getBinId(),
                entity.getItemCode(),
                entity.getBatch(),
                entity.getExpiry(),
                entity.getQtyDelta(),
                entity.getUom(),
                entity.getEventType(),
                entity.getReason(),
                entity.getRefType(),
                entity.getRefId(),
                entity.getIdempotencyKey(),
                entity.getActorId(),
                entity.getCreatedAt()
        );
    }
}
