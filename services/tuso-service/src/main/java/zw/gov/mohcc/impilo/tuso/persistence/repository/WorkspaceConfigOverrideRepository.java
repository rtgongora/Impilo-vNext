package zw.gov.mohcc.impilo.tuso.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.tuso.persistence.entity.WorkspaceConfigOverrideEntity;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface WorkspaceConfigOverrideRepository extends JpaRepository<WorkspaceConfigOverrideEntity, Long> {

    Optional<WorkspaceConfigOverrideEntity> findByWorkspaceId(UUID workspaceId);
}
