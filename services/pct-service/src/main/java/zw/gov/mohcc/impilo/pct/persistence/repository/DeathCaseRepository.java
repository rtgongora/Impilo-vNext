package zw.gov.mohcc.impilo.pct.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.pct.persistence.entity.DeathCaseEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for {@link DeathCaseEntity} persistence operations.
 * All finder methods are tenant-scoped to enforce multi-tenancy isolation.
 */
@Repository
public interface DeathCaseRepository extends JpaRepository<DeathCaseEntity, UUID> {

    /**
     * Finds a specific death case by tenant and case ID.
     *
     * @param tenantId the tenant identifier
     * @param caseId   the death case identifier
     * @return the death case if found
     */
    Optional<DeathCaseEntity> findByTenantIdAndCaseId(UUID tenantId, UUID caseId);

    /**
     * Finds the death case for a given journey within a tenant.
     *
     * @param tenantId  the tenant identifier
     * @param journeyId the journey identifier
     * @return the death case if found
     */
    Optional<DeathCaseEntity> findByTenantIdAndJourneyId(UUID tenantId, String journeyId);

    /** All death cases for a facility, newest first — backs the facility death dashboard. */
    List<DeathCaseEntity> findByTenantIdAndFacilityIdOrderByCreatedAtDesc(UUID tenantId, UUID facilityId);

    /** Cases awaiting cause-of-death certification (DRAFT or NOT_STARTED) for a facility. */
    List<DeathCaseEntity> findByTenantIdAndFacilityIdAndCertificationStatus(UUID tenantId, UUID facilityId, String certificationStatus);

    long countByTenantIdAndFacilityIdAndCoronerReferralRequired(UUID tenantId, UUID facilityId, boolean coronerReferralRequired);
}
