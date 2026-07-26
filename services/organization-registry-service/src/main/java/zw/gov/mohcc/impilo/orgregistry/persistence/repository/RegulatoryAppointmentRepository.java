package zw.gov.mohcc.impilo.orgregistry.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import zw.gov.mohcc.impilo.orgregistry.persistence.entity.RegulatoryAppointmentEntity;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RegulatoryAppointmentRepository extends JpaRepository<RegulatoryAppointmentEntity, UUID> {

    List<RegulatoryAppointmentEntity> findByTenantIdAndOrganizationId(UUID tenantId, UUID organizationId);

    List<RegulatoryAppointmentEntity> findByTenantIdAndPersonHealthId(UUID tenantId, String personHealthId);

    Optional<RegulatoryAppointmentEntity> findByTenantIdAndId(UUID tenantId, UUID id);

    boolean existsByTenantIdAndOrganizationIdAndPersonHealthIdAndRoleCodeAndStatus(
            UUID tenantId, UUID organizationId, String personHealthId, String roleCode, String status);

    /**
     * The expiry sweep's working set (V008): ACTIVE appointments whose validity ended strictly
     * before {@code asOf} and which have not already been swept.
     *
     * <p>Before this method existed, {@code valid_to} was written by {@code create} and read by
     * nothing — an appointment that lapsed a year ago still minted work-context tokens.</p>
     */
    List<RegulatoryAppointmentEntity> findByStatusAndValidToBeforeAndExpirySweptAtIsNull(
            String status, LocalDate asOf);

    /**
     * How many ACTIVE administrators an organisation has (V009). Drives both the succession guard
     * — a regulator must never be left with nobody who can administer it — and bootstrap
     * eligibility, since a founding administrator may only be granted while this is zero.
     *
     * <p>Counts by joining the role vocabulary rather than matching role-code prefixes: a role
     * added later is administrative because it is flagged, not because someone remembered to
     * update a string list here.</p>
     */
    @Query("SELECT COUNT(a) FROM RegulatoryAppointmentEntity a, AppointmentRoleEntity r "
            + "WHERE a.roleCode = r.roleCode AND r.administrative = true "
            + "AND a.tenantId = :tenantId AND a.organizationId = :organizationId "
            + "AND a.status = 'ACTIVE' AND (:excludingId IS NULL OR a.id <> :excludingId)")
    long countActiveAdministrators(@Param("tenantId") UUID tenantId,
                                   @Param("organizationId") UUID organizationId,
                                   @Param("excludingId") UUID excludingId);
}
