package zw.gov.mohcc.impilo.procedures.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.procedures.persistence.entity.SedationLevelEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SedationLevelRepository extends JpaRepository<SedationLevelEntity, UUID> {
    Optional<SedationLevelEntity> findByTenantIdAndLevelCode(UUID tenantId, String levelCode);
    List<SedationLevelEntity> findByTenantIdOrderByDepthRankAsc(UUID tenantId);
}
