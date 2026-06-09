package zw.gov.mohcc.impilo.pct.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.pct.persistence.entity.EmergencyCaseEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EmergencyCaseRepository extends JpaRepository<EmergencyCaseEntity, Long> {
    Optional<EmergencyCaseEntity> findByEmergencyCaseId(UUID emergencyCaseId);
    List<EmergencyCaseEntity> findByTenantIdAndStatusOrderByCreatedAtDesc(UUID tenantId, String status);
}
