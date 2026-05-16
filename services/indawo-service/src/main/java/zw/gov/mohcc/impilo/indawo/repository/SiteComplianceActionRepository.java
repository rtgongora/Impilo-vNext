package zw.gov.mohcc.impilo.indawo.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;
import zw.gov.mohcc.impilo.indawo.domain.SiteComplianceActionEntity;

import java.util.List;
import java.util.UUID;

public interface SiteComplianceActionRepository extends JpaRepository<SiteComplianceActionEntity, UUID> {
    List<SiteComplianceActionEntity> findBySiteIdOrderByUpdatedAtDesc(UUID siteId);
    long countByTenantIdAndStatus(UUID tenantId, String status);
    List<SiteComplianceActionEntity> findByTenantIdOrderByUpdatedAtDesc(UUID tenantId, Pageable pageable);
    List<SiteComplianceActionEntity> findByTenantIdAndStatusIgnoreCaseOrderByUpdatedAtDesc(UUID tenantId, String status, Pageable pageable);
}

