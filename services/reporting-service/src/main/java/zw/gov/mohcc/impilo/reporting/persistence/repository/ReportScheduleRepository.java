package zw.gov.mohcc.impilo.reporting.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.reporting.persistence.entity.ReportScheduleEntity;

import java.util.List;
import java.util.UUID;

@Repository
public interface ReportScheduleRepository extends JpaRepository<ReportScheduleEntity, Long> {

    List<ReportScheduleEntity> findByTenantIdAndDefinitionId(UUID tenantId, Long definitionId);
}
