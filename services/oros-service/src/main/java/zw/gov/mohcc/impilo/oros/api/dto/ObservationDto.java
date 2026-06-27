package zw.gov.mohcc.impilo.oros.api.dto;

import zw.gov.mohcc.impilo.oros.persistence.entity.ResultObservationEntity;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/** A structured laboratory observation (read view). */
public record ObservationDto(
        UUID observationId,
        UUID resultId,
        String orderId,
        int sequence,
        String analyteCode,
        String analyteSystem,
        String analyteName,
        String valueText,
        BigDecimal valueNumeric,
        String valueType,
        String unit,
        BigDecimal refRangeLow,
        BigDecimal refRangeHigh,
        String refRangeText,
        String abnormalFlag,
        boolean criticalFlag,
        String comment,
        OffsetDateTime createdAt
) {
    public static ObservationDto from(ResultObservationEntity e) {
        return new ObservationDto(
                e.getObservationId(), e.getResultId(), e.getOrderId(), e.getSequence(),
                e.getAnalyteCode(), e.getAnalyteSystem(), e.getAnalyteName(),
                e.getValueText(), e.getValueNumeric(), e.getValueType(), e.getUnit(),
                e.getRefRangeLow(), e.getRefRangeHigh(), e.getRefRangeText(),
                e.getAbnormalFlag(), e.isCriticalFlag(), e.getComment(), e.getCreatedAt());
    }
}
