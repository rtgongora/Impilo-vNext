package zw.gov.mohcc.impilo.vito.core;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.vito.persistence.entity.DedupCaseEntity;
import zw.gov.mohcc.impilo.vito.persistence.entity.ProvisionalIdEntity;
import zw.gov.mohcc.impilo.vito.persistence.repository.DedupCaseRepository;
import zw.gov.mohcc.impilo.vito.persistence.repository.ProvisionalIdRepository;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Mode B: Standalone Registry Admin.
 *
 * Comprehensive registry management workflow for manual operations:
 *   - Provisional ID reconciliation (offline-issued IDs → permanent)
 *   - Dedup case management (merge/reject duplicate candidates)
 *   - Client search and update by trusted operators
 *
 * Active when vito.registry-mode=STANDALONE (default).
 */
@Service
public class StandaloneRegistryService {

    private final ProvisionalIdRepository provisionalIdRepository;
    private final DedupCaseRepository dedupCaseRepository;
    private final IdentityService identityService;

    public StandaloneRegistryService(ProvisionalIdRepository provisionalIdRepository,
                                      DedupCaseRepository dedupCaseRepository,
                                      IdentityService identityService) {
        this.provisionalIdRepository = provisionalIdRepository;
        this.dedupCaseRepository = dedupCaseRepository;
        this.identityService = identityService;
    }

    /**
     * Issue a provisional ID for offline registration.
     */
    @Transactional
    public ProvisionalIdEntity issueProvisionalId(UUID tenantId, UUID facilityId,
                                                    String issuedBy, int expiryHours) {
        ProvisionalIdEntity provisional = new ProvisionalIdEntity();
        provisional.setTenantId(tenantId);
        provisional.setProvisionalRef("PROV-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase());
        provisional.setFacilityId(facilityId);
        provisional.setIssuedBy(issuedBy);
        provisional.setStatus("PENDING");
        provisional.setExpiresAt(OffsetDateTime.now().plusHours(expiryHours));

        return provisionalIdRepository.save(provisional);
    }

    /**
     * Reconcile a provisional ID with a permanent health_id.
     */
    @Transactional
    public ProvisionalIdEntity reconcile(UUID tenantId, String provisionalRef, UUID healthId) {
        ProvisionalIdEntity provisional = provisionalIdRepository
                .findByTenantIdAndProvisionalRef(tenantId, provisionalRef)
                .orElseThrow(() -> new IllegalArgumentException("Provisional ID not found"));

        if (!"PENDING".equals(provisional.getStatus())) {
            throw new IllegalStateException("Provisional ID already " + provisional.getStatus());
        }

        provisional.setStatus("RECONCILED");
        provisional.setReconciledTo(healthId);
        provisional.setReconciledAt(OffsetDateTime.now());

        return provisionalIdRepository.save(provisional);
    }

    /**
     * Get pending provisional IDs for a facility.
     */
    @Transactional(readOnly = true)
    public Page<ProvisionalIdEntity> getPendingProvisionalIds(UUID tenantId, UUID facilityId, Pageable pageable) {
        return provisionalIdRepository.findByTenantIdAndFacilityIdAndStatus(
                tenantId, facilityId, "PENDING", pageable);
    }

    /**
     * Get pending dedup cases for operator resolution.
     */
    @Transactional(readOnly = true)
    public Page<DedupCaseEntity> getPendingDedupCases(UUID tenantId, Pageable pageable) {
        return dedupCaseRepository.findByTenantIdAndStatus(tenantId, "PENDING", pageable);
    }

    /**
     * Resolve a dedup case (merge or reject).
     */
    @Transactional
    public DedupCaseEntity resolveDedupCase(Long caseId, String decision, String reviewedBy) {
        DedupCaseEntity dedupCase = dedupCaseRepository.findById(caseId)
                .orElseThrow(() -> new IllegalArgumentException("Dedup case not found"));

        dedupCase.setStatus(decision); // MERGED or REJECTED
        dedupCase.setReviewedBy(reviewedBy);
        dedupCase.setReviewedAt(OffsetDateTime.now());

        return dedupCaseRepository.save(dedupCase);
    }
}
