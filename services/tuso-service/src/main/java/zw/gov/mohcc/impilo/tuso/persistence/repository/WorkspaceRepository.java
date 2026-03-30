package zw.gov.mohcc.impilo.tuso.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.tuso.persistence.entity.WorkspaceEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WorkspaceRepository extends JpaRepository<WorkspaceEntity, UUID> {

    List<WorkspaceEntity> findByFacility_IdAndActiveTrue(Long facilityId);

    Optional<WorkspaceEntity> findByIdAndTenantId(UUID id, UUID tenantId);

    List<WorkspaceEntity> findByTenantIdAndWorkspaceTypeAndActiveTrue(UUID tenantId, String workspaceType);
}
