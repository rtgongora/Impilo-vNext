package zw.gov.mohcc.impilo.governance.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ImportBatchRepository extends JpaRepository<ImportBatchEntity, UUID> {
    List<ImportBatchEntity> findByTenantIdAndOrganisationIdOrderByCreatedAtDesc(UUID tenantId, UUID organisationId);
}
