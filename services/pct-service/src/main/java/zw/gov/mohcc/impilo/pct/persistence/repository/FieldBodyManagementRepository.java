package zw.gov.mohcc.impilo.pct.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.pct.persistence.entity.FieldBodyManagementEntity;

import java.util.List;
import java.util.UUID;

@Repository
public interface FieldBodyManagementRepository extends JpaRepository<FieldBodyManagementEntity, UUID> {
    List<FieldBodyManagementEntity> findByTenantIdAndCaseIdOrderByCreatedAtDesc(UUID tenantId, UUID caseId);
}
