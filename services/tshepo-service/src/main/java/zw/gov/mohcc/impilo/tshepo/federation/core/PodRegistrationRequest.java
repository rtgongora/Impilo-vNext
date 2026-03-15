package zw.gov.mohcc.impilo.tshepo.federation.core;

import java.util.List;
import java.util.UUID;

/**
 * Pod registration request submitted by a facility pod to the national spine.
 *
 * @param podId                    unique identifier for the pod
 * @param podName                  human-readable pod name (e.g. "Facility-Pod-Harare-Central")
 * @param podCertificate           PEM-encoded X.509 certificate for mTLS
 * @param capabilities             list of capabilities the pod supports (e.g. PATIENT_REGISTRY)
 * @param region                   geographic region of the pod
 * @param facilityIds              list of facility UUIDs this pod serves
 * @param requestedDataClasses     data classes the pod wants access to
 * @param requestedMaxOfflineHours max hours the pod may operate offline
 */
public record PodRegistrationRequest(
        UUID podId,
        String podName,
        String podCertificate,
        List<String> capabilities,
        String region,
        List<UUID> facilityIds,
        List<String> requestedDataClasses,
        Integer requestedMaxOfflineHours
) {
}
