package zw.gov.mohcc.impilo.tuso.persistence.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.tuso.persistence.entity.TelemetryEventEntity;

import java.time.Instant;
import java.util.UUID;

@Repository
public interface TelemetryEventRepository extends JpaRepository<TelemetryEventEntity, Long> {

    Page<TelemetryEventEntity> findByFacility_IdAndSourceOrderByRecordedAtDesc(
            Long facilityId, String source, Pageable pageable);

    Page<TelemetryEventEntity> findByTenantIdAndMetricTypeAndRecordedAtBetweenOrderByRecordedAtDesc(
            UUID tenantId, String metricType, Instant from, Instant to, Pageable pageable);

    Page<TelemetryEventEntity> findByFacility_IdOrderByRecordedAtDesc(Long facilityId, Pageable pageable);
}
