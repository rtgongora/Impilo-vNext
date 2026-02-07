package zw.gov.mohcc.impilo.tuso.persistence.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.tuso.persistence.entity.WorkspaceOverrideLogEntity;

import java.util.UUID;

@Repository
public interface WorkspaceOverrideLogRepository extends JpaRepository<WorkspaceOverrideLogEntity, Long> {

    Page<WorkspaceOverrideLogEntity> findByWorkspaceIdOrderByCreatedAtDesc(UUID workspaceId, Pageable pageable);

    Page<WorkspaceOverrideLogEntity> findByTenantIdOrderByCreatedAtDesc(UUID tenantId, Pageable pageable);
}
