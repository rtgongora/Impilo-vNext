package zw.gov.mohcc.impilo.madi.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.madi.persistence.entity.BloodForecastEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BloodForecastRepository extends JpaRepository<BloodForecastEntity, Long> {
    Optional<BloodForecastEntity> findByForecastIdAndTenantId(UUID forecastId, UUID tenantId);
    List<BloodForecastEntity> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);
}
