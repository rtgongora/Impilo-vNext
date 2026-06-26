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
        @JsonProperty("remaining_amount") BigDecimal remainingAmount,
        @JsonProperty("subsidy") SubsidyHeadroom subsidy
) {
    public InternalEligibilityCheckResponse(boolean eligible, String reason, BigDecimal utilizationLimit,
                                            BigDecimal utilizationUsed, BigDecimal remainingAmount) {
        this(eligible, reason, utilizationLimit, utilizationUsed, remainingAmount, null);
    }

    /** Subsidy annual-cap headroom surfaced alongside scheme eligibility. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record SubsidyHeadroom(
            @JsonProperty("available") boolean available,
            @JsonProperty("reason") String reason,
            @JsonProperty("cap") BigDecimal cap,
            @JsonProperty("consumed") BigDecimal consumed,
            @JsonProperty("remaining") BigDecimal remaining
    ) {}
}
