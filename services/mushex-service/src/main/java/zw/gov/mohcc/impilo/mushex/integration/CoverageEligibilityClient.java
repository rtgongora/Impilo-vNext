package zw.gov.mohcc.impilo.mushex.integration;

import java.util.UUID;

/**
 * Mesh client for coverage-service eligibility checks.
 */
public interface CoverageEligibilityClient {

    /**
     * @param serviceCode optional; may be sent as empty string when absent
     * @param reasonCode machine reason from coverage (e.g. {@code COVERAGE_INELIGIBLE}, {@code UTILIZATION_LIMIT_EXCEEDED})
     * @param coverageReference stable id to persist on the claim when the result is eligible
     */
    record EligibilityResult(boolean eligible, String reasonCode, String coverageReference) {}

    EligibilityResult evaluateEligibility(UUID tenantId, String patientCpid, String planCode, String serviceCode);
}
