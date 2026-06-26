package zw.gov.mohcc.impilo.indawo.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.indawo.domain.SurveillanceAlertEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SurveillanceAlertRepository extends JpaRepository<SurveillanceAlertEntity, Long> {
    List<SurveillanceAlertEntity> findByTenantIdOrderByDetectedAtDesc(UUID tenantId);
    List<SurveillanceAlertEntity> findByTenantIdAndStatusOrderByDetectedAtDesc(UUID tenantId, String status);
    Optional<SurveillanceAlertEntity> findByTenantIdAndAlertId(UUID tenantId, UUID alertId);
    long countByTenantIdAndStatus(UUID tenantId, String status);
}
