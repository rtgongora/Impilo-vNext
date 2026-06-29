package zw.gov.mohcc.impilo.inventory.persistence.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.inventory.persistence.entity.TemperatureLogEntity;

import java.util.UUID;

/**
 * Repository for Dura cold-chain temperature logs.
 */
@Repository
public interface TemperatureLogRepository extends JpaRepository<TemperatureLogEntity, UUID> {

    Page<TemperatureLogEntity> findByTenantIdAndCcLocationIdOrderByRecordedAtDesc(
            UUID tenantId, UUID ccLocationId, Pageable pageable);
}
