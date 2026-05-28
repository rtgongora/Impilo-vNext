package zw.gov.mohcc.impilo.pct.core.telemedicine;

import java.util.Map;
import java.util.UUID;

/**
 * Provider-neutral session provisioning contract for telemedicine engines.
 */
public interface TelemedicineSessionProvider {

    String providerType();

    SessionProvisioningResult provision(SessionProvisioningRequest request);

    default void end(String sessionId) {
        // Most non-media providers do not own an external room lifecycle.
    }

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
