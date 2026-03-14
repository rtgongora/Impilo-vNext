package zw.gov.mohcc.impilo.jobs.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.jobs.persistence.entity.JobExecutionEntity;

import java.util.List;
import java.util.UUID;

@Repository
public interface JobExecutionRepository extends JpaRepository<JobExecutionEntity, Long> {

    List<JobExecutionEntity> findByJobDefinitionId(Long jobDefinitionId);

    List<JobExecutionEntity> findByTenantId(UUID tenantId);

    List<JobExecutionEntity> findByStatus(String status);
}
