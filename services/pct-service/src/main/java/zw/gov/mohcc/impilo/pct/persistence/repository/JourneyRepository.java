package zw.gov.mohcc.impilo.pct.persistence.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.pct.domain.JourneyState;
import zw.gov.mohcc.impilo.pct.persistence.entity.JourneyEntity;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for {@link JourneyEntity} persistence operations.
 * All finder methods are tenant-scoped to enforce multi-tenancy isolation.
 */
@Repository
public interface JourneyRepository extends JpaRepository<JourneyEntity, String> {

    /**
     * Finds a journey by tenant and journey ID.
     *
     * @param tenantId  the tenant identifier
     * @param journeyId the ULID journey identifier
     * @return the journey if found
     */
    Optional<JourneyEntity> findByTenantIdAndJourneyId(UUID tenantId, String journeyId);

    /**
     * Finds all journeys for a specific patient within a tenant.
     *
     * @param tenantId the tenant identifier
     * @param cpid     the patient's CPID
     * @return list of journeys for the patient
     */
    List<JourneyEntity> findByTenantIdAndPatientCpid(UUID tenantId, String cpid);

    /**
     * Finds journeys at a facility that are in any of the given states.
     *
     * @param tenantId   the tenant identifier
     * @param facilityId the facility identifier
     * @param states     list of journey states to filter by
     * @return list of matching journeys
     */
    List<JourneyEntity> findByTenantIdAndFacilityIdAndStateIn(UUID tenantId, UUID facilityId, List<JourneyState> states);

    /**
     * Returns a paginated list of journeys at a facility.
     *
     * @param tenantId   the tenant identifier
     * @param facilityId the facility identifier
     * @param pageable   pagination parameters
     * @return page of journeys
     */
    Page<JourneyEntity> findByTenantIdAndFacilityId(UUID tenantId, UUID facilityId, Pageable pageable);

    // ------------------------------------------------------------------
    // Non-tenant-scoped methods
    // ------------------------------------------------------------------

    /**
     * Finds a journey by its journey ID (non-tenant-scoped).
     * Used by QueueEngine and other services where tenant context is already validated.
     *
     * @param journeyId the ULID journey identifier
     * @return the journey if found
     */
    Optional<JourneyEntity> findByJourneyId(String journeyId);

    /**
     * Counts journeys at a facility with a given state.
     * Used by the control tower for active journey counts.
     *
     * @param facilityId the facility identifier
     * @param state      the journey state to count
     * @return number of matching journeys
     */
    long countByFacilityIdAndState(UUID facilityId, JourneyState state);

    /**
     * Counts journeys at a facility with a given state updated after a timestamp.
     * Used by the control tower for throughput calculations.
     *
     * @param facilityId the facility identifier
     * @param state      the journey state to count
     * @param after      the timestamp lower bound
     * @return number of matching journeys
     */
    long countByFacilityIdAndStateAndUpdatedAtAfter(UUID facilityId, JourneyState state, OffsetDateTime after);
}
