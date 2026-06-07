package zw.gov.mohcc.impilo.live.core;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.live.persistence.entity.LiveEventAnalyticsSnapshotEntity;
import zw.gov.mohcc.impilo.live.persistence.entity.LiveEventEntity;
import zw.gov.mohcc.impilo.live.persistence.repository.LiveEventAnalyticsSnapshotRepository;
import zw.gov.mohcc.impilo.live.persistence.repository.LiveEventAttendanceRepository;
import zw.gov.mohcc.impilo.live.persistence.repository.LiveEventChatMessageRepository;
import zw.gov.mohcc.impilo.live.persistence.repository.LiveEventRegistrationRepository;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class AnalyticsService {

    private final LiveEventService eventService;
    private final LiveEventAnalyticsSnapshotRepository snapshotRepository;
    private final LiveEventRegistrationRepository registrationRepository;
    private final LiveEventAttendanceRepository attendanceRepository;
    private final LiveEventChatMessageRepository chatRepository;

    public AnalyticsService(LiveEventService eventService,
                            LiveEventAnalyticsSnapshotRepository snapshotRepository,
                            LiveEventRegistrationRepository registrationRepository,
                            LiveEventAttendanceRepository attendanceRepository,
                            LiveEventChatMessageRepository chatRepository) {
        this.eventService = eventService;
        this.snapshotRepository = snapshotRepository;
        this.registrationRepository = registrationRepository;
        this.attendanceRepository = attendanceRepository;
        this.chatRepository = chatRepository;
    }

    @Transactional
    public void captureSnapshot(UUID tenantId, UUID eventId) {
        LiveEventEntity event = eventService.get(tenantId, eventId);
        long registrations = registrationRepository.countByEventIdAndRegistrationStatus(eventId, "REGISTERED");
        long attendees = attendanceRepository.findByEventId(eventId).size();
        long cpdEligible = attendanceRepository.countByEventIdAndEligibleForCpdTrue(eventId);
        long chatMessages = chatRepository.findByEventIdOrderByCreatedAtAsc(eventId).size();

        saveMetric(eventId, "registrations", registrations);
        saveMetric(eventId, "attendees", attendees);
        saveMetric(eventId, "cpd_eligible", cpdEligible);
        saveMetric(eventId, "chat_messages", chatMessages);
        saveMetric(eventId, "cpd_points", event.getCpdPoints() != null
                ? event.getCpdPoints() : BigDecimal.ZERO);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getDashboardMetrics(UUID tenantId, UUID eventId) {
        eventService.get(tenantId, eventId);
        List<LiveEventAnalyticsSnapshotEntity> snapshots =
                snapshotRepository.findByEventIdOrderByCapturedAtDesc(eventId);
        Map<String, Object> metrics = new LinkedHashMap<>();
        for (LiveEventAnalyticsSnapshotEntity snap : snapshots) {
            if (!metrics.containsKey(snap.getMetricName())) {
                metrics.put(snap.getMetricName(), snap.getMetricValue());
            }
        }
        if (metrics.isEmpty()) {
            metrics.put("registrations", registrationRepository.countByEventIdAndRegistrationStatus(eventId, "REGISTERED"));
            metrics.put("attendees", attendanceRepository.findByEventId(eventId).size());
            metrics.put("cpd_eligible", attendanceRepository.countByEventIdAndEligibleForCpdTrue(eventId));
        }
        return metrics;
    }

    @Transactional(readOnly = true)
    public List<LiveEventAnalyticsSnapshotEntity> getHistory(UUID tenantId, UUID eventId, String metricName) {
        eventService.get(tenantId, eventId);
        return snapshotRepository.findByEventIdAndMetricNameOrderByCapturedAtDesc(eventId, metricName);
    }

    private void saveMetric(UUID eventId, String name, long value) {
        LiveEventAnalyticsSnapshotEntity snap = new LiveEventAnalyticsSnapshotEntity();
        snap.setEventId(eventId);
        snap.setMetricName(name);
        snap.setMetricValue(BigDecimal.valueOf(value));
        snapshotRepository.save(snap);
    }

    private void saveMetric(UUID eventId, String name, BigDecimal value) {
        LiveEventAnalyticsSnapshotEntity snap = new LiveEventAnalyticsSnapshotEntity();
        snap.setEventId(eventId);
        snap.setMetricName(name);
        snap.setMetricValue(value);
        snapshotRepository.save(snap);
    }
}
