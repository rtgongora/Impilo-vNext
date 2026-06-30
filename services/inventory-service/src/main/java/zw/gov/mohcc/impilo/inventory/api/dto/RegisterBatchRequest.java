package zw.gov.mohcc.impilo.inventory.api.dto;

import jakarta.validation.constraints.NotBlank;

import java.time.LocalDate;

/**
 * Request to register (upsert) a batch/lot for a commodity.
 */
public record RegisterBatchRequest(
        @NotBlank String itemCode,
        @NotBlank String batchNumber,
        String lotNumber,
        LocalDate expiryDate,
        LocalDate manufactureDate,
        String manufacturer,
        String supplier,
        String serialNumber,
        String notes
) {}
