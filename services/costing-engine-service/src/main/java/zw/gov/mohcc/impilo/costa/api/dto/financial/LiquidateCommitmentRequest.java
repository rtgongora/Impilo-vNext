package zw.gov.mohcc.impilo.costa.api.dto.financial;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record LiquidateCommitmentRequest(@NotNull BigDecimal amount) {}
