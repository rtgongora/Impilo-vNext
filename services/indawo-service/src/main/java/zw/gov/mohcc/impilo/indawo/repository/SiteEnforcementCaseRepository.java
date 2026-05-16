package zw.gov.mohcc.impilo.indawo.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;
import zw.gov.mohcc.impilo.indawo.domain.SiteEnforcementCaseEntity;

import java.util.List;
import java.util.UUID;

public interface SiteEnforcementCaseRepository extends JpaRepository<SiteEnforcementCaseEntity, UUID> {
    List<SiteEnforcementCaseEntity> findBySiteIdOrderByOpenedAtDesc(UUID siteId);
    List<SiteEnforcementCaseEntity> findByTenantIdOrderByOpenedAtDesc(UUID tenantId, Pageable pageable);
    List<SiteEnforcementCaseEntity> findByTenantIdAndStatusIgnoreCaseOrderByOpenedAtDesc(UUID tenantId, String status, Pageable pageable);
}

