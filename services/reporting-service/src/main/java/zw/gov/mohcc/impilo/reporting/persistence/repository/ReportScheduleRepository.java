package zw.gov.mohcc.impilo.reporting.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.reporting.persistence.entity.ReportScheduleEntity;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface ReportScheduleRepository extends JpaRepository<ReportScheduleEntity, Long> {

    List<ReportScheduleEntity> findByTenantIdAndDefinitionId(UUID tenantId, Long definitionId);

    @Query("SELECT s FROM ReportScheduleEntity s WHERE s.status = 'ACTIVE' AND s.nextRunAt <= :now")
    List<ReportScheduleEntity> findDueSchedules(@Param("now") OffsetDateTime now);
}
