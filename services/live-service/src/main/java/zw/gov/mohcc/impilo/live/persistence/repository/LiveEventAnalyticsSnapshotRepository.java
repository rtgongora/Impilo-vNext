package zw.gov.mohcc.impilo.live.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.live.persistence.entity.LiveEventAnalyticsSnapshotEntity;

import java.util.List;
import java.util.UUID;

@Repository
public interface LiveEventAnalyticsSnapshotRepository extends JpaRepository<LiveEventAnalyticsSnapshotEntity, UUID> {

    List<LiveEventAnalyticsSnapshotEntity> findByEventIdAndMetricNameOrderByCapturedAtDesc(UUID eventId, String metricName);

    List<LiveEventAnalyticsSnapshotEntity> findByEventIdOrderByCapturedAtDesc(UUID eventId);
}
