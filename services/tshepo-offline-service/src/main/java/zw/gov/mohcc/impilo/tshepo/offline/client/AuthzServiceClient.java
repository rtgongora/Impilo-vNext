package zw.gov.mohcc.impilo.tshepo.offline.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.UUID;

/**
 * Client for the TSHEPO Authorization Service.
 * Used to validate that an actor has access to a given facility
 * before issuing offline capability tokens.
 */
@Component
public class AuthzServiceClient {

    private static final Logger log = LoggerFactory.getLogger(AuthzServiceClient.class);

    private final RestClient authzRestClient;

    public AuthzServiceClient(@Qualifier("authzRestClient") RestClient authzRestClient) {
        this.authzRestClient = authzRestClient;
    }

    /**
     * Check whether the given actor is authorized to access the specified facility.
     *
     * @param tenantId    the tenant context
     * @param actorId     the actor requesting access
     * @param facilityId  the facility to check access for
     * @return true if the actor has facility access
     */
    public boolean checkFacilityAccess(UUID tenantId, String actorId, UUID facilityId) {
        log.debug("Checking facility access: tenant={}, actor={}, facility={}", tenantId, actorId, facilityId);

        try {
            FacilityAccessRequest request = new FacilityAccessRequest(
                    tenantId, actorId, facilityId, "OFFLINE_CAPABILITY_ISSUANCE"
            );
            FacilityAccessResponse response = authzRestClient.post()
                    .uri("/v1/authz/facility-access")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(FacilityAccessResponse.class);

            return response != null && response.allowed();
        } catch (Exception e) {
            log.error("Failed to check facility access: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Validate an offline action against current authorization policies.
     * Used during reconciliation to ensure the action is still permitted.
     *
     * @param tenantId     the tenant context
     * @param actorId      the actor who performed the action
     * @param facilityId   the facility context
     * @param actionType   the type of action performed
     * @param resourceType the type of resource affected
     * @return true if the action is currently authorized
     */
    public boolean validateActionPolicy(UUID tenantId, String actorId, UUID facilityId,
                                         String actionType, String resourceType) {
        log.debug("Validating action policy: tenant={}, actor={}, action={}, resource={}",
                tenantId, actorId, actionType, resourceType);

        try {
            ActionValidationRequest request = new ActionValidationRequest(
                    tenantId, actorId, facilityId, actionType, resourceType
            );
            ActionValidationResponse response = authzRestClient.post()
                    .uri("/v1/authz/validate-action")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(ActionValidationResponse.class);

            return response != null && response.allowed();
        } catch (Exception e) {
            log.error("Failed to validate action policy: {}", e.getMessage(), e);
            return false;
        }
    }

    public record FacilityAccessRequest(UUID tenantId, String actorId, UUID facilityId, String purpose) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record FacilityAccessResponse(boolean allowed, String reason) {}

    public record ActionValidationRequest(UUID tenantId, String actorId, UUID facilityId,
                                           String actionType, String resourceType) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ActionValidationResponse(boolean allowed, String reason) {}
}
