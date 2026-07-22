package zw.gov.mohcc.impilo.orgregistry.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.orgregistry.persistence.entity.RegulatoryAppointmentEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RegulatoryAppointmentRepository extends JpaRepository<RegulatoryAppointmentEntity, UUID> {

    List<RegulatoryAppointmentEntity> findByTenantIdAndOrganizationId(UUID tenantId, UUID organizationId);

    List<RegulatoryAppointmentEntity> findByTenantIdAndPersonHealthId(UUID tenantId, String personHealthId);

    Optional<RegulatoryAppointmentEntity> findByTenantIdAndId(UUID tenantId, UUID id);

    boolean existsByTenantIdAndOrganizationIdAndPersonHealthIdAndRoleCodeAndStatus(
            UUID tenantId, UUID organizationId, String personHealthId, String roleCode, String status);
}
