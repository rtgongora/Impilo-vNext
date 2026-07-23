package zw.gov.mohcc.impilo.telemonitoring.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.telemonitoring.domain.AlertStatus;
import zw.gov.mohcc.impilo.telemonitoring.persistence.entity.AlertEpisodeEntity;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AlertEpisodeRepository extends JpaRepository<AlertEpisodeEntity, UUID> {

    /** One-live-episode lookup (the UNIQUE constraint on live_guard backstops the race). */
    Optional<AlertEpisodeEntity> findByLiveGuard(String liveGuard);

    List<AlertEpisodeEntity> findByTenantIdAndPlanIdOrderByCreatedAtDesc(UUID tenantId, UUID planId);

    List<AlertEpisodeEntity> findByTenantIdAndStatusOrderByCreatedAtDesc(UUID tenantId, AlertStatus status);

    List<AlertEpisodeEntity> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);

    List<AlertEpisodeEntity> findByTenantIdAndDeviceRefOrderByCreatedAtDesc(UUID tenantId, String deviceRef);

    /** Overdue sweep population: unacknowledged episodes past their response deadline (§14.6). */
    List<AlertEpisodeEntity> findByStatusAndResponseDeadlineAtBefore(AlertStatus status, OffsetDateTime cutoff);
}
