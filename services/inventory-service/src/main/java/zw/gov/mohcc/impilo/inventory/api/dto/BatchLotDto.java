package zw.gov.mohcc.impilo.inventory.api.dto;

import zw.gov.mohcc.impilo.inventory.domain.BatchLotStatus;
import zw.gov.mohcc.impilo.inventory.persistence.entity.BatchLotEntity;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * DTO for a Dura batch/lot.
 */
public record BatchLotDto(
        UUID batchLotId,
        UUID itemId,
        String itemCode,
        String batchNumber,
        String lotNumber,
        LocalDate expiryDate,
        LocalDate manufactureDate,
        String manufacturer,
        String supplier,
        String serialNumber,
        BatchLotStatus status,
        String notes,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public static BatchLotDto from(BatchLotEntity e) {
        return new BatchLotDto(
                e.getBatchLotId(),
                e.getItemId(),
                e.getItemCode(),
                e.getBatchNumber(),
                e.getLotNumber(),
                e.getExpiryDate(),
                e.getManufactureDate(),
                e.getManufacturer(),
                e.getSupplier(),
                e.getSerialNumber(),
                e.getStatus(),
                e.getNotes(),
                e.getCreatedAt(),
                e.getUpdatedAt()
        );
    }
}
