package zw.gov.mohcc.impilo.rito.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.rito.persistence.entity.AuditToolEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AuditToolRepository extends JpaRepository<AuditToolEntity, UUID> {

    Optional<AuditToolEntity> findByIdAndTenantId(UUID id, UUID tenantId);

    Optional<AuditToolEntity> findByTenantIdAndToolKey(UUID tenantId, String toolKey);

    List<AuditToolEntity> findByTenantIdAndActiveTrue(UUID tenantId);

    boolean existsByTenantIdAndToolKey(UUID tenantId, String toolKey);
}
