package zw.gov.mohcc.impilo.tuso.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.tuso.persistence.entity.WorkspaceRuleEntity;

import java.util.List;
import java.util.UUID;

@Repository
public interface WorkspaceRuleRepository extends JpaRepository<WorkspaceRuleEntity, Long> {

    List<WorkspaceRuleEntity> findByWorkspaceId(UUID workspaceId);

    List<WorkspaceRuleEntity> findByWorkspaceIdAndRuleType(UUID workspaceId, String ruleType);
}
