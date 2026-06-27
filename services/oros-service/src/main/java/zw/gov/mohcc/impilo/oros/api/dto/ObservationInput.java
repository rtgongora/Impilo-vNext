package zw.gov.mohcc.impilo.oros.api.dto;

import java.math.BigDecimal;

/**
 * Inbound structured observation for lab-result entry. {@code analyteName} is required; the rest
 * are optional (numeric or text value, unit, reference range, abnormal/critical flags).
 */
public record ObservationInput(
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
        Boolean criticalFlag,
        String comment
) {}
