package zw.gov.mohcc.impilo.tshepo.audit.persistence.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.tshepo.audit.persistence.entity.AuditExportEntity;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface AuditExportRepository extends JpaRepository<AuditExportEntity, UUID> {

    /**
     * Find export by ID and tenant (tenant-isolated access).
     */
    Optional<AuditExportEntity> findByIdAndTenantId(UUID id, UUID tenantId);

    /**
     * List exports for a tenant.
     */
    Page<AuditExportEntity> findByTenantIdOrderByCreatedAtDesc(UUID tenantId, Pageable pageable);
}
