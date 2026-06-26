package zw.gov.mohcc.impilo.indawo.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.indawo.domain.FieldTeamDeploymentEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FieldTeamDeploymentRepository extends JpaRepository<FieldTeamDeploymentEntity, Long> {
    List<FieldTeamDeploymentEntity> findByTenantIdAndTeamIdOrderByDeployedAtDesc(UUID tenantId, UUID teamId);
    List<FieldTeamDeploymentEntity> findByTenantIdAndOutbreakIdOrderByDeployedAtDesc(UUID tenantId, UUID outbreakId);
    Optional<FieldTeamDeploymentEntity> findByTenantIdAndDeploymentId(UUID tenantId, UUID deploymentId);

    long countByTenantIdAndTeamIdAndStatus(UUID tenantId, UUID teamId, String status);
}
