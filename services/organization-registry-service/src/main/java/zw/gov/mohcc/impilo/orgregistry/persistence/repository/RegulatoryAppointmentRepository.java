package zw.gov.mohcc.impilo.orgregistry.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
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
}
