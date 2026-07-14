package zw.gov.mohcc.impilo.coverage.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import zw.gov.mohcc.impilo.coverage.domain.SubsidyEnrolmentEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record SubsidyEnrolmentResponse(
        @JsonProperty("id") UUID id,
        @JsonProperty("subsidy_program_id") UUID subsidyProgramId,
        @JsonProperty("member_cpid") String memberCpid,
        @JsonProperty("status") String status,
        @JsonProperty("annual_cap_override") BigDecimal annualCapOverride,
        @JsonProperty("effective_cap") BigDecimal effectiveCap,
        @JsonProperty("consumed_amount") BigDecimal consumedAmount,
        @JsonProperty("remaining_amount") BigDecimal remainingAmount,
        @JsonProperty("currency") String currency,
        @JsonProperty("effective_from") LocalDate effectiveFrom,
        @JsonProperty("effective_to") LocalDate effectiveTo,
        /** NON_NULL: absent for value-only enrolments, so the legacy shape is unchanged. */
        @JsonProperty("exemption_category") String exemptionCategory
) {
    public static SubsidyEnrolmentResponse of(SubsidyEnrolmentEntity e, BigDecimal effectiveCap,
                                              BigDecimal consumed, BigDecimal remaining) {
        return new SubsidyEnrolmentResponse(
                e.getId(), e.getSubsidyProgramId(), e.getMemberCpid(), e.getStatus(),
                e.getAnnualCapOverride(), effectiveCap, consumed, remaining, e.getCurrency(),
                e.getEffectiveFrom(), e.getEffectiveTo(), e.getExemptionCategory());
    }
}
