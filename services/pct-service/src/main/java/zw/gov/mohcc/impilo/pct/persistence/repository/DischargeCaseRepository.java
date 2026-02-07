package zw.gov.mohcc.impilo.pct.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.pct.persistence.entity.DischargeCaseEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for {@link DischargeCaseEntity} persistence operations.
 * All finder methods are tenant-scoped to enforce multi-tenancy isolation.
 */
@Repository
public interface DischargeCaseRepository extends JpaRepository<DischargeCaseEntity, UUID> {

    /**
     * Finds a specific discharge case by tenant and case ID.
     *
     * @param tenantId the tenant identifier
     * @param caseId   the discharge case identifier
     * @return the discharge case if found
     */
    Optional<DischargeCaseEntity> findByTenantIdAndCaseId(UUID tenantId, UUID caseId);

    /**
     * Finds the discharge case for a given journey within a tenant.
     *
     * @param tenantId  the tenant identifier
     * @param journeyId the journey identifier
     * @return the discharge case if found
     */
    Optional<DischargeCaseEntity> findByTenantIdAndJourneyId(UUID tenantId, String journeyId);

    /**
     * Finds a discharge case by journey ID and matching any of the given statuses.
     * Used by event consumers to find active discharge cases for auto-clearing blockers.
     *
     * @param journeyId the journey identifier
     * @param statuses  list of statuses to match (e.g. ["INITIATED"])
     * @return the discharge case if found
     */
    Optional<DischargeCaseEntity> findByJourneyIdAndStatusIn(String journeyId, List<String> statuses);

    /**
     * Finds discharge cases at a facility matching any of the given statuses.
     * Used by the control tower to detect stuck discharges.
     *
     * @param facilityId the facility identifier
     * @param statuses   list of statuses to match
     * @return list of matching discharge cases
     */
    List<DischargeCaseEntity> findByFacilityIdAndStatusIn(UUID facilityId, List<String> statuses);
}
