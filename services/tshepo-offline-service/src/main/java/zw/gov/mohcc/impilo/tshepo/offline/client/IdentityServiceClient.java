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
 * Client for the TSHEPO Identity Service.
 * Used to issue provisional O-CPIDs for offline patient registration.
 */
@Component
public class IdentityServiceClient {

    private static final Logger log = LoggerFactory.getLogger(IdentityServiceClient.class);

    private final RestClient identityRestClient;

    public IdentityServiceClient(@Qualifier("identityRestClient") RestClient identityRestClient) {
        this.identityRestClient = identityRestClient;
    }

    /**
     * Issue a provisional O-CPID (Offline Community Patient ID) for offline patient registration.
     *
     * @param tenantId    the tenant context
     * @param facilityId  the facility where offline registration occurs
     * @param actorId     the actor performing the registration
     * @return the issued O-CPID
     */
    public UUID issueProvisionalCpid(UUID tenantId, UUID facilityId, String actorId) {
        log.debug("Requesting provisional O-CPID: tenant={}, facility={}, actor={}", tenantId, facilityId, actorId);

        ProvisionalCpidRequest request = new ProvisionalCpidRequest(tenantId, facilityId, actorId, "OFFLINE");
        ProvisionalCpidResponse response = identityRestClient.post()
                .uri("/v1/identity/cpid/provisional")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(ProvisionalCpidResponse.class);

        if (response == null || response.cpid() == null) {
            throw new RuntimeException("Identity service returned null O-CPID");
        }

        log.info("Issued provisional O-CPID: {}", response.cpid());
        return response.cpid();
    }

    /**
     * Reconcile a provisional O-CPID with the master patient index.
     *
     * @param tenantId  the tenant context
     * @param oCpid     the provisional O-CPID to reconcile
     * @return the permanent CPID (which may be the same if no duplicate was found)
     */
    public UUID reconcileCpid(UUID tenantId, UUID oCpid) {
        log.debug("Reconciling O-CPID: tenant={}, oCpid={}", tenantId, oCpid);

        try {
            ReconcileCpidRequest request = new ReconcileCpidRequest(tenantId, oCpid);
            ReconcileCpidResponse response = identityRestClient.post()
                    .uri("/v1/identity/cpid/reconcile")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(ReconcileCpidResponse.class);

            if (response == null) {
                throw new RuntimeException("Identity service returned null reconciliation response");
            }

            log.info("Reconciled O-CPID {} -> permanent CPID {}, duplicate={}", oCpid, response.permanentCpid(), response.duplicate());
            return response.permanentCpid();
        } catch (Exception e) {
            log.error("Failed to reconcile O-CPID {}: {}", oCpid, e.getMessage(), e);
            throw new RuntimeException("O-CPID reconciliation failed for " + oCpid, e);
        }
    }

    public record ProvisionalCpidRequest(UUID tenantId, UUID facilityId, String actorId, String mode) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ProvisionalCpidResponse(UUID cpid, String status) {}

    public record ReconcileCpidRequest(UUID tenantId, UUID oCpid) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ReconcileCpidResponse(UUID permanentCpid, boolean duplicate, String mergeStatus) {}
}
