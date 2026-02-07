package zw.gov.mohcc.impilo.inventory.api.dto;

import zw.gov.mohcc.impilo.inventory.persistence.entity.CountLineEntity;

import java.time.LocalDate;
import java.util.UUID;

/**
 * DTO representing a single count line within a stock count session.
 *
 * @param lineId         the line identifier
 * @param sessionId      the parent session identifier
 * @param itemCode       the item code
 * @param batch          the batch number
 * @param expiry         the expiry date
 * @param qtySystem      the system-recorded quantity at count start
 * @param qtyCounted     the physically counted quantity (null if not yet counted)
 * @param variance       the computed variance (counted - system), null if not yet counted
 * @param barcodeScanned barcode scanned during counting
 * @param notes          line-level notes
 */
public record CountLineDto(
        UUID lineId,
        UUID sessionId,
        String itemCode,
        String batch,
        LocalDate expiry,
        int qtySystem,
        Integer qtyCounted,
        Integer variance,
        String barcodeScanned,
        String notes
) {

    /**
     * Map a CountLineEntity to a CountLineDto.
     *
     * @param entity the count line entity
     * @return the DTO representation
     */
    public static CountLineDto from(CountLineEntity entity) {
        return new CountLineDto(
                entity.getLineId(),
                entity.getSessionId(),
                entity.getItemCode(),
                entity.getBatch(),
                entity.getExpiry(),
                entity.getQtySystem(),
                entity.getQtyCounted(),
                entity.getVariance(),
                entity.getBarcodeScanned(),
                entity.getNotes()
        );
    }
}
