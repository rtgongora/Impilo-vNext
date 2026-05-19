package zw.gov.mohcc.impilo.indawo.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;
import zw.gov.mohcc.impilo.indawo.domain.SiteInspectionEntity;

import java.util.List;
import java.util.UUID;

public interface SiteInspectionRepository extends JpaRepository<SiteInspectionEntity, UUID> {
    List<SiteInspectionEntity> findBySiteIdOrderByCreatedAtDesc(UUID siteId);
    List<SiteInspectionEntity> findByApplicationIdOrderByCreatedAtDesc(UUID applicationId);
    List<SiteInspectionEntity> findByTenantIdOrderByCreatedAtDesc(UUID tenantId, Pageable pageable);
    List<SiteInspectionEntity> findByTenantIdAndStatusIgnoreCaseOrderByCreatedAtDesc(UUID tenantId, String status, Pageable pageable);
}

