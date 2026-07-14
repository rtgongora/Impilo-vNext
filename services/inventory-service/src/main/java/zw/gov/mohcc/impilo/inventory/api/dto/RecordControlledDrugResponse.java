package zw.gov.mohcc.impilo.inventory.api.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Result of recording a controlled-drug movement.
 */
public record RecordControlledDrugResponse(
        UUID entryId,
        String action,
        BigDecimal quantity,
        BigDecimal runningBalance,
        String witnessProvider
) {}
