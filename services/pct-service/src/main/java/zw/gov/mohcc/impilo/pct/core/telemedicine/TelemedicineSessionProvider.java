package zw.gov.mohcc.impilo.pct.core.telemedicine;

import java.util.Map;
import java.util.UUID;

/**
 * Provider-neutral session provisioning contract for telemedicine engines.
 */
public interface TelemedicineSessionProvider {

    String providerType();

    SessionProvisioningResult provision(SessionProvisioningRequest request);

    record SessionProvisioningRequest(
            UUID tenantId,
            String patientCpid,
            String providerId,
            String facilityId,
            String sessionType,
            String referralId,
            String encounterId,
            Map<String, Object> attributes
    ) {
    }
}
