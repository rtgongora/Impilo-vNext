package zw.gov.mohcc.impilo.tshepo.contracts.v1;

import com.fasterxml.jackson.annotation.JsonInclude;

/** Destination service and operation under evaluation. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ServiceRequestContext(
        String contractVersion,
        String destinationServiceId,
        String method,
        String path,
        String action,
        String resourceType,
        String resourceId,
        String sensitivityClass
) {
    public ServiceRequestContext {
        TrustContractVersion.requireV1(contractVersion);
        if (destinationServiceId == null || destinationServiceId.isBlank()) {
            throw new IllegalArgumentException("destinationServiceId is required");
        }
    }

    public static ServiceRequestContext of(
            String destinationServiceId,
            String method,
            String path,
            String action,
            String resourceType,
            String resourceId,
            String sensitivityClass) {
        return new ServiceRequestContext(
                TrustContractVersion.V1,
                destinationServiceId,
                method,
                path,
                action,
                resourceType,
                resourceId,
                sensitivityClass);
    }
}
