package zw.gov.mohcc.impilo.datagovernance.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.datagovernance.domain.DecisionAuditEntity;

import java.util.List;
import java.util.UUID;

public interface DecisionAuditRepository extends JpaRepository<DecisionAuditEntity, UUID> {

    List<DecisionAuditEntity> findByPrincipalIdAndDatasetName(String principalId, String datasetName);

    long countByPrincipalId(String principalId);

    Page<DecisionAuditEntity> findByTenantIdOrderByDecidedAtDesc(UUID tenantId, Pageable pageable);
}
