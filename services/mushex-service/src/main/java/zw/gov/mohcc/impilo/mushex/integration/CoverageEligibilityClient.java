package zw.gov.mohcc.impilo.mushex.integration;

import java.util.UUID;

/**
 * Calls coverage-service internal eligibility API (mesh).
 */
public interface CoverageEligibilityClient {

    /**
     * @param serviceCode optional; may be sent as empty string when absent
     */
    boolean checkEligibility(UUID tenantId, String patientCpid, String planCode, String serviceCode);
}
