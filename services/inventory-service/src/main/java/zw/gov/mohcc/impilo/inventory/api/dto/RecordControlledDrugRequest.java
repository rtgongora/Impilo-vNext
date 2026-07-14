package zw.gov.mohcc.impilo.inventory.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Request to record a controlled-drug movement in the witness register.
 *
 * <p>{@code action} is one of ISSUE | ADMINISTER | WASTE | DISCARD.
 * ADMINISTER/WASTE/DISCARD require a non-blank {@code witnessProvider}.</p>
 */
public record RecordControlledDrugRequest(
        UUID facilityId,
        UUID storeId,
        @NotBlank String itemCode,
        String drugName,
        @NotBlank String action,
        @NotNull BigDecimal quantity,
        String unit,
        String administeringProvider,
        String witnessProvider,
        String refType,
        String refId,
        String chartEntryRef,
        String notes
) {}
