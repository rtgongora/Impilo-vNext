package zw.gov.mohcc.impilo.coverage.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

/** Draw down subsidy value against an enrolment's annual cap. */
public record SubsidyConsumeRequest(
        @JsonProperty("amount")
        @NotNull(message = "amount is required")
        @Positive(message = "amount must be > 0")
        BigDecimal amount,
        @JsonProperty("reference") String reference
) {}
