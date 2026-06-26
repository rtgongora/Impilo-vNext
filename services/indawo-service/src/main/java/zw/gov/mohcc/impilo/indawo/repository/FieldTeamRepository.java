package zw.gov.mohcc.impilo.indawo.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.indawo.domain.FieldTeamEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FieldTeamRepository extends JpaRepository<FieldTeamEntity, Long> {
    List<FieldTeamEntity> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);
    Optional<FieldTeamEntity> findByTenantIdAndTeamId(UUID tenantId, UUID teamId);
}
