package zw.gov.mohcc.impilo.costa.api.dto;

import jakarta.validation.constraints.NotNull;
import zw.gov.mohcc.impilo.costa.domain.enums.BillLineKind;
import zw.gov.mohcc.impilo.costa.domain.enums.CostMethodType;

import java.math.BigDecimal;
import java.util.Map;

public record PostLineRequest(
        @NotNull String msikaCode,
        @NotNull String description,
        @NotNull BillLineKind kind,
        @NotNull BigDecimal qty,
        CostMethodType costMethod,
        String sourceEvent,
        String sourceRef,
        Map<String, Object> context
) {}
