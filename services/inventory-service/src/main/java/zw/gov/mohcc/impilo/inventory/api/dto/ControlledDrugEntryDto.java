package zw.gov.mohcc.impilo.inventory.api.dto;

import zw.gov.mohcc.impilo.inventory.persistence.entity.ControlledDrugRegisterEntity;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * A controlled-drug register entry projection for list/read surfaces.
 */
public record ControlledDrugEntryDto(
        UUID entryId,
        String itemCode,
        String drugName,
        String action,
        BigDecimal quantity,
        String unit,
        BigDecimal runningBalance,
        String administeringProvider,
        String witnessProvider,
        String refType,
        String refId,
        String chartEntryRef,
        boolean reconciled,
        OffsetDateTime createdAt
) {
    public static ControlledDrugEntryDto from(ControlledDrugRegisterEntity e) {
        return new ControlledDrugEntryDto(
                e.getEntryId(), e.getItemCode(), e.getDrugName(), e.getAction(),
                e.getQuantity(), e.getUnit(), e.getRunningBalance(),
                e.getAdministeringProvider(), e.getWitnessProvider(),
                e.getRefType(), e.getRefId(), e.getChartEntryRef(),
                e.isReconciled(), e.getCreatedAt());
    }
}
