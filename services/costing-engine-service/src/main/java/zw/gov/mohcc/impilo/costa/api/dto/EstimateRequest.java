package zw.gov.mohcc.impilo.costa.api.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import zw.gov.mohcc.impilo.costa.domain.enums.BillLineKind;
import zw.gov.mohcc.impilo.costa.domain.enums.CostMethodType;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public record EstimateRequest(
        @NotEmpty List<LineItem> items,
        String exemptionCategory,
        Long insurancePlanId,
        Map<String, Object> context
) {
    public record LineItem(
            @NotNull String msikaCode,
            String description,
            @NotNull BillLineKind kind,
            @NotNull BigDecimal qty,
            CostMethodType costMethod,
            Map<String, Object> extras
    ) {}
}
