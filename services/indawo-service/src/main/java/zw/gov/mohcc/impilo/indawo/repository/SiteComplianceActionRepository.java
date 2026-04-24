package zw.gov.mohcc.impilo.indawo.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.indawo.domain.SiteComplianceActionEntity;

import java.util.List;
import java.util.UUID;

public interface SiteComplianceActionRepository extends JpaRepository<SiteComplianceActionEntity, UUID> {
    List<SiteComplianceActionEntity> findBySiteIdOrderByUpdatedAtDesc(UUID siteId);
    long countByTenantIdAndStatus(UUID tenantId, String status);
}

