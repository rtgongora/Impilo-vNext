package zw.gov.mohcc.impilo.tuso.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.tuso.persistence.entity.WorkspaceDashboardEntity;

import java.util.List;
import java.util.UUID;

@Repository
public interface WorkspaceDashboardRepository extends JpaRepository<WorkspaceDashboardEntity, Long> {

    List<WorkspaceDashboardEntity> findByWorkspaceIdAndActiveTrueOrderBySortOrderAsc(UUID workspaceId);

    List<WorkspaceDashboardEntity> findByWorkspaceId(UUID workspaceId);
}
