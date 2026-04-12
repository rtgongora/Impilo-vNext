package zw.gov.mohcc.impilo.mushex.integration;

import java.util.UUID;

/**
 * Ensures a facility may receive or initiate payments under v1.3 trust rules.
 */
public interface FacilityCredentialVerifier {

    void assertFacilityPaymentAllowed(UUID tenantId, UUID facilityId);
}
