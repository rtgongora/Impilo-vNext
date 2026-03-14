package zw.gov.mohcc.impilo.jobs.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.jobs.persistence.entity.JobDefinitionEntity;

import java.util.List;
import java.util.UUID;

@Repository
public interface JobDefinitionRepository extends JpaRepository<JobDefinitionEntity, Long> {

    List<JobDefinitionEntity> findByTenantId(UUID tenantId);
}
