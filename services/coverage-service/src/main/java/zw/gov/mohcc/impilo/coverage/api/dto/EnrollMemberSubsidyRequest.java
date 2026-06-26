package zw.gov.mohcc.impilo.coverage.api.dto;

import jakarta.validation.constraints.NotBlank;
import java.time.LocalDate;

/**
 * Enrol a member (CPID) into a subsidy programme.
 *
 * @param memberCpid        the patient CPID being enrolled
 * @param programCode       the subsidy programme code (must exist for the tenant)
 * @param exemptionCategory the per-member billing classification (e.g. INDIGENT, ELDERLY,
 *                          MATERNITY) that COSTA charging rules key exemptions on
 * @param effectiveFrom     enrolment start date; defaults to today when null
 */
public record EnrollMemberSubsidyRequest(
        @NotBlank String memberCpid,
        @NotBlank String programCode,
        @NotBlank String exemptionCategory,
        LocalDate effectiveFrom
) {}
