package zw.gov.mohcc.impilo.connectorfhir.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.connectorfhir.domain.RelayAuditEntity;

import java.util.List;
import java.util.UUID;

public interface RelayAuditRepository extends JpaRepository<RelayAuditEntity, UUID> {
    Page<RelayAuditEntity> findByTenantIdOrderByCreatedAtDesc(UUID tenantId, Pageable pageable);
    List<RelayAuditEntity> findByBundleId(String bundleId);
}
