package zw.gov.mohcc.impilo.pct.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.pct.persistence.entity.TelemetryEventEntity;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Repository for {@link TelemetryEventEntity} persistence operations.
 *
 * <p>Primarily an append-only store for telemetry events. The finder methods
 * support the ops controller's telemetry endpoint for real-time operational
 * dashboards.</p>
 */
@Repository
public interface TelemetryEventRepository extends JpaRepository<TelemetryEventEntity, Long> {

    /**
     * Retrieve telemetry events for a facility since a given timestamp,
     * ordered by most recent first.
     *
     * @param tenantId   the tenant identifier for multi-tenancy isolation
     * @param facilityId the facility to query
     * @param since      the earliest creation timestamp to include
     * @return list of telemetry events in reverse chronological order
     */
    List<TelemetryEventEntity> findByTenantIdAndFacilityIdAndCreatedAtAfterOrderByCreatedAtDesc(
            UUID tenantId, UUID facilityId, OffsetDateTime since);
}
