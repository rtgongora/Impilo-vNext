package zw.gov.mohcc.impilo.workflow.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.workflow.domain.WorkflowTaskEntity;

import java.util.List;
import java.util.UUID;

public interface WorkflowTaskRepository extends JpaRepository<WorkflowTaskEntity, UUID> {
    List<WorkflowTaskEntity> findByInstanceIdOrderByCreatedAtAsc(UUID instanceId);
}
