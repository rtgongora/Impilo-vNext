package zw.gov.mohcc.impilo.iotingestion.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import zw.gov.mohcc.impilo.iotingestion.domain.TelemetryReadingEntity;

import java.time.OffsetDateTime;
import java.util.UUID;

public interface TelemetryReadingRepository extends JpaRepository<TelemetryReadingEntity, Long> {

    @Query("SELECT r FROM TelemetryReadingEntity r WHERE r.deviceId = :deviceId"
            + " AND r.tenantId = :tenantId"
            + " ORDER BY r.recordedAt DESC")
    Page<TelemetryReadingEntity> findByDeviceAndTenant(
            @Param("deviceId") String deviceId,
            @Param("tenantId") UUID tenantId,
            Pageable pageable);

    @Query("SELECT r FROM TelemetryReadingEntity r WHERE r.tenantId = :tenantId"
            + " AND (:deviceId IS NULL OR r.deviceId = :deviceId)"
            + " AND (:metricType IS NULL OR r.metricType = :metricType)"
            + " ORDER BY r.ingestedAt DESC")
    Page<TelemetryReadingEntity> findFiltered(
            @Param("tenantId") UUID tenantId,
            @Param("deviceId") String deviceId,
            @Param("metricType") String metricType,
            Pageable pageable);

    long countByDeviceId(String deviceId);
}
