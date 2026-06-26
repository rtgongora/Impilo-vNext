package zw.gov.mohcc.impilo.rito.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.rito.persistence.entity.AuditEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AuditRepository extends JpaRepository<AuditEntity, UUID> {

    Optional<AuditEntity> findByIdAndTenantId(UUID id, UUID tenantId);

    List<AuditEntity> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);

    List<AuditEntity> findByTenantIdAndStatus(UUID tenantId, String status);

    List<AuditEntity> findByTenantIdAndFacilityId(UUID tenantId, UUID facilityId);

    boolean existsByTenantIdAndAuditReference(UUID tenantId, String auditReference);
}
