package zw.gov.mohcc.impilo.reporting.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.reporting.persistence.entity.ReportDefinitionEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ReportDefinitionRepository extends JpaRepository<ReportDefinitionEntity, Long> {

    Optional<ReportDefinitionEntity> findByTenantIdAndReportKey(UUID tenantId, String reportKey);

    List<ReportDefinitionEntity> findByTenantId(UUID tenantId);
}
