package zw.gov.mohcc.impilo.experience.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.experience.domain.Workspace;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WorkspaceRepository extends JpaRepository<Workspace, UUID> {

    Optional<Workspace> findByIdAndTenantId(UUID id, String tenantId);

    List<Workspace> findByFacilityIdAndTenantIdOrderByNameAsc(UUID facilityId, String tenantId);
}
