package zw.gov.mohcc.impilo.fhirgateway.persistence.repository;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.fhirgateway.persistence.entity.FhirAuditLogEntity;

@Repository
public interface FhirAuditLogRepository extends JpaRepository<FhirAuditLogEntity, Long> {

    Page<FhirAuditLogEntity> findByTenantId(UUID tenantId, Pageable pageable);

    Page<FhirAuditLogEntity> findByTenantIdAndResourceType(UUID tenantId, String resourceType, Pageable pageable);
}
