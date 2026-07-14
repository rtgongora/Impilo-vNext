package zw.gov.mohcc.impilo.coverage.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Enrol a member into a subsidy programme. Identify the programme by id or by code
 * (one is required). Optionally carries a per-member annual-cap override, an
 * exemption category (billing classification for downstream costing waivers), and a
 * backdated effective-from (defaults to today).
 */
public record SubsidyEnrolmentRequest(
        @JsonProperty("subsidy_program_id") UUID subsidyProgramId,
        @JsonProperty("program_code") String programCode,
        @JsonProperty("member_cpid")
        @NotBlank(message = "member_cpid is required")
        String memberCpid,
        @JsonProperty("annual_cap_override") BigDecimal annualCapOverride,
        @JsonProperty("exemption_category") String exemptionCategory,
        @JsonProperty("currency") String currency,
        @JsonProperty("enrolled_by") String enrolledBy,
        @JsonProperty("effective_from") LocalDate effectiveFrom
) {}
