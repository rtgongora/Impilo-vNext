package zw.gov.mohcc.impilo.pct.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.pct.persistence.entity.AdmissionEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for {@link AdmissionEntity} persistence operations.
 * All finder methods are tenant-scoped to enforce multi-tenancy isolation.
 */
@Repository
public interface AdmissionRepository extends JpaRepository<AdmissionEntity, UUID> {

    /**
     * Finds a specific admission by tenant and admission ID.
     *
     * @param tenantId    the tenant identifier
     * @param admissionId the admission identifier
     * @return the admission if found
     */
    Optional<AdmissionEntity> findByTenantIdAndAdmissionId(UUID tenantId, UUID admissionId);

    /**
     * Finds all admissions for a given journey within a tenant.
     *
     * @param tenantId  the tenant identifier
     * @param journeyId the journey identifier
     * @return list of admissions for the journey
     */
    List<AdmissionEntity> findByTenantIdAndJourneyId(UUID tenantId, String journeyId);

    /**
     * Finds all admissions with a given status within a tenant.
     *
     * @param tenantId the tenant identifier
     * @param status   the admission status to filter by
     * @return list of matching admissions
     */
    List<AdmissionEntity> findByTenantIdAndStatus(UUID tenantId, String status);

    /**
     * Checks if a specific bed in a ward is occupied by finding an admission with the given status.
     * Useful for bed occupancy validation before assigning a new patient.
     *
     * @param tenantId the tenant identifier
     * @param wardId   the ward identifier
     * @param bedId    the bed identifier
     * @param status   the admission status (typically "ADMITTED")
     * @return the admission occupying the bed, if any
     */
    Optional<AdmissionEntity> findByTenantIdAndWardIdAndBedIdAndStatus(UUID tenantId, UUID wardId, UUID bedId, String status);

    /**
     * Counts the number of admissions in a ward with a given status.
     * Useful for calculating ward occupancy rates.
     *
     * @param tenantId the tenant identifier
     * @param wardId   the ward identifier
     * @param status   the admission status to count (typically "ADMITTED")
     * @return the number of admissions matching the criteria
     */
    long countByTenantIdAndWardIdAndStatus(UUID tenantId, UUID wardId, String status);

    /**
     * Finds admissions at a facility matching any of the given statuses.
     * Used by the control tower for bed occupancy calculations.
     *
     * @param facilityId the facility identifier
     * @param statuses   list of statuses to match (e.g. ["ADMITTED"])
     * @return list of matching admissions
     */
    List<AdmissionEntity> findByFacilityIdAndStatusIn(UUID facilityId, List<String> statuses);

    /**
     * Finds an admission for a journey matching any of the given statuses.
     * Used to locate the active admission for transfer workflows.
     *
     * @param journeyId the journey identifier
     * @param statuses  list of statuses to match
     * @return the matching admission if found
     */
    Optional<AdmissionEntity> findByJourneyIdAndStatusIn(String journeyId, List<String> statuses);

    /**
     * Checks if a specific bed in a ward is occupied by another admission.
     * Excludes the specified admission ID (for reassignment scenarios).
     *
     * @param wardId   the ward identifier
     * @param bedId    the bed identifier
     * @param statuses list of active statuses to check
     * @param excludeId the admission ID to exclude from the check
     * @return true if the bed is occupied by another admission
     */
    boolean existsByWardIdAndBedIdAndStatusInAndIdNot(UUID wardId, UUID bedId,
                                                       List<String> statuses, UUID excludeId);

    /**
     * The admission-handshake back-link (V018): resolves inpatient-service's own admission id to
     * PCT's admission row, which carries the journey. Used by
     * {@code InFacilityDeteriorationConsumer} (W6b) to anchor an in-facility emergency episode onto
     * the admission's EXISTING journey rather than minting a second one.
     */
    Optional<AdmissionEntity> findByTenantIdAndInpatientAdmissionRef(UUID tenantId, UUID inpatientAdmissionRef);
}
