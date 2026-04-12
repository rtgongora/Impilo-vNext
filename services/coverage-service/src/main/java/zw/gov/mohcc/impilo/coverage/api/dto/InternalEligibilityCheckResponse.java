package zw.gov.mohcc.impilo.coverage.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record InternalEligibilityCheckResponse(
        boolean eligible,
        @JsonProperty("reason") String reason,
        @JsonProperty("utilization_limit") BigDecimal utilizationLimit,
        @JsonProperty("utilization_used") BigDecimal utilizationUsed,
        @JsonProperty("remaining_amount") BigDecimal remainingAmount
) {}
