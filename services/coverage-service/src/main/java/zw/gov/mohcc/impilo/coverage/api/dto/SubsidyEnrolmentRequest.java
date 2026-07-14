package zw.gov.mohcc.impilo.coverage.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Enrol a member into a subsidy programme. Identify the programme by id or by code
 * (one is required). An optional per-member annual-cap override may be supplied.
 */
public record SubsidyEnrolmentRequest(
        @JsonProperty("subsidy_program_id") UUID subsidyProgramId,
        @JsonProperty("program_code") String programCode,
        @JsonProperty("member_cpid")
        @NotBlank(message = "member_cpid is required")
        String memberCpid,
        @JsonProperty("annual_cap_override") BigDecimal annualCapOverride,
        @JsonProperty("currency") String currency,
        @JsonProperty("enrolled_by") String enrolledBy,
        /** Optional per-member billing classification (e.g. INDIGENT, HEALTH_WORKER) for COSTA exemptions. */
        @JsonProperty("exemption_category") String exemptionCategory
) {}
