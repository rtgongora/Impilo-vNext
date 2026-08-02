package zw.gov.mohcc.impilo.tshepo.contracts.v1;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Calling or destination workload identity. Never collapsed into a human subject.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record WorkloadSubject(
        String contractVersion,
        String workloadId,
        String serviceName,
        String kubernetesServiceAccount,
        String keycloakClientId,
        String audience
) {
    public WorkloadSubject {
        TrustContractVersion.requireV1(contractVersion);
        if (workloadId == null || workloadId.isBlank()) {
            throw new IllegalArgumentException("workloadId is required");
        }
    }

    public static WorkloadSubject of(
            String workloadId,
            String serviceName,
            String kubernetesServiceAccount,
            String keycloakClientId,
            String audience) {
        return new WorkloadSubject(
                TrustContractVersion.V1,
                workloadId,
                serviceName,
                kubernetesServiceAccount,
                keycloakClientId,
                audience);
    }
}
