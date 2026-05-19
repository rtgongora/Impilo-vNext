package zw.gov.mohcc.impilo.dispatch.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.dispatch.domain.AutonomousMissionEntity;

import java.util.List;
import java.util.UUID;

public interface AutonomousMissionRepository extends JpaRepository<AutonomousMissionEntity, UUID> {
    List<AutonomousMissionEntity> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);
}
