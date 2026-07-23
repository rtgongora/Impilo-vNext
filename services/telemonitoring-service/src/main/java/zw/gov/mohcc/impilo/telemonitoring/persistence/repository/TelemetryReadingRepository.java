package zw.gov.mohcc.impilo.telemonitoring.persistence.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.telemonitoring.domain.ReadingStatus;
import zw.gov.mohcc.impilo.telemonitoring.domain.ShrWriteState;
import zw.gov.mohcc.impilo.telemonitoring.persistence.entity.TelemetryReadingEntity;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TelemetryReadingRepository extends JpaRepository<TelemetryReadingEntity, UUID> {

    /** Journey #64 pre-flight — the UNIQUE constraint on dedup_key backstops the race. */
    boolean existsByDedupKey(String dedupKey);

    Optional<TelemetryReadingEntity> findByDedupKey(String dedupKey);

    Page<TelemetryReadingEntity> findByTenantIdAndPlanIdOrderByMeasuredAtDesc(
            UUID tenantId, UUID planId, Pageable pageable);

    Page<TelemetryReadingEntity> findByTenantIdAndDeviceRefOrderByMeasuredAtDesc(
            UUID tenantId, String deviceRef, Pageable pageable);

    /** Retry-sweep population: rows still owed to the SHR (PENDING / RETRYING). */
    List<TelemetryReadingEntity> findTop100ByShrWriteStateInOrderByCreatedAtAsc(
            Collection<ShrWriteState> states);

    /** OF-B26 heartbeat sweep (trigger 8): the device's most recent reading. */
    Optional<TelemetryReadingEntity> findFirstByDeviceRefOrderByMeasuredAtDesc(String deviceRef);

    /** OF-B26 device-quality trigger (trigger 9): persistent bad stamps from one device. */
    long countByDeviceRefAndStatusInAndReceivedAtAfter(
            String deviceRef, Collection<ReadingStatus> statuses, OffsetDateTime cutoff);
}
