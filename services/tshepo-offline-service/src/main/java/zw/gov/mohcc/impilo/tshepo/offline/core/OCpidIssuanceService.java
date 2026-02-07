package zw.gov.mohcc.impilo.tshepo.offline.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import zw.gov.mohcc.impilo.tshepo.offline.client.IdentityServiceClient;
import zw.gov.mohcc.impilo.tshepo.offline.exception.OfflineServiceException;

import java.util.UUID;

/**
 * Service for issuing and reconciling Offline Community Patient IDs (O-CPIDs).
 *
 * <p>O-CPIDs are provisional patient identifiers issued when a device is offline
 * and needs to register a new patient. They are reconciled with the master
 * patient index when the device comes back online.</p>
 *
 * <p>Delegates to tshepo-identity-service for actual CPID generation and
 * reconciliation against the master patient index.</p>
 */
@Service
public class OCpidIssuanceService {

    private static final Logger log = LoggerFactory.getLogger(OCpidIssuanceService.class);

    private final IdentityServiceClient identityClient;

    public OCpidIssuanceService(IdentityServiceClient identityClient) {
        this.identityClient = identityClient;
    }

    /**
     * Issue a provisional O-CPID for offline patient registration.
     *
     * @param tenantId    the tenant context
     * @param facilityId  the facility where offline registration occurs
     * @param actorId     the actor performing the registration
     * @return the newly issued provisional O-CPID
     */
    public UUID issueOCpid(UUID tenantId, UUID facilityId, String actorId) {
        log.info("Issuing O-CPID: tenant={}, facility={}, actor={}", tenantId, facilityId, actorId);

        try {
            UUID oCpid = identityClient.issueProvisionalCpid(tenantId, facilityId, actorId);
            log.info("O-CPID issued successfully: {}", oCpid);
            return oCpid;
        } catch (Exception e) {
            log.error("Failed to issue O-CPID: {}", e.getMessage(), e);
            throw new OfflineServiceException("OCPID_ISSUANCE_FAILED",
                    "Failed to issue provisional O-CPID: " + e.getMessage(), e);
        }
    }

    /**
     * Reconcile an O-CPID with the master patient index during sync-back.
     *
     * <p>If the patient was already registered (e.g., by another offline device),
     * the identity service will return the existing permanent CPID and mark the
     * O-CPID as a duplicate.</p>
     *
     * @param tenantId the tenant context
     * @param oCpid    the provisional O-CPID to reconcile
     * @return the permanent CPID (same as O-CPID if no duplicate, or the existing CPID if merged)
     */
    public UUID reconcileOCpid(UUID tenantId, UUID oCpid) {
        log.info("Reconciling O-CPID: tenant={}, oCpid={}", tenantId, oCpid);

        try {
            UUID permanentCpid = identityClient.reconcileCpid(tenantId, oCpid);
            log.info("O-CPID {} reconciled to permanent CPID {}", oCpid, permanentCpid);
            return permanentCpid;
        } catch (Exception e) {
            log.error("Failed to reconcile O-CPID {}: {}", oCpid, e.getMessage(), e);
            throw new OfflineServiceException("OCPID_RECONCILIATION_FAILED",
                    "Failed to reconcile O-CPID " + oCpid + ": " + e.getMessage(), e);
        }
    }
}
