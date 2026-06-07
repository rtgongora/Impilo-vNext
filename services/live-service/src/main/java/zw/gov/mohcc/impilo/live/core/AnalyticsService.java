package zw.gov.mohcc.impilo.live.core;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.live.persistence.entity.LiveEventAnalyticsSnapshotEntity;
import zw.gov.mohcc.impilo.live.persistence.entity.LiveEventAttendanceEntity;
import zw.gov.mohcc.impilo.live.persistence.entity.LiveEventEntity;
import zw.gov.mohcc.impilo.live.persistence.entity.LiveEventFeedbackEntity;
import zw.gov.mohcc.impilo.live.persistence.repository.LiveEventAnalyticsSnapshotRepository;
import zw.gov.mohcc.impilo.live.persistence.repository.LiveEventAttendanceRepository;
import zw.gov.mohcc.impilo.live.persistence.repository.LiveEventChatMessageRepository;
import zw.gov.mohcc.impilo.live.persistence.repository.LiveEventFeedbackRepository;
import zw.gov.mohcc.impilo.live.persistence.repository.LiveEventPollRepository;
import zw.gov.mohcc.impilo.live.persistence.repository.LiveEventQuestionRepository;
import zw.gov.mohcc.impilo.live.persistence.repository.LiveEventRegistrationRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
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
    private final LiveEventQuestionRepository questionRepository;
    private final LiveEventPollRepository pollRepository;
    private final LiveEventFeedbackRepository feedbackRepository;

    public AnalyticsService(LiveEventService eventService,
                            LiveEventAnalyticsSnapshotRepository snapshotRepository,
                            LiveEventRegistrationRepository registrationRepository,
                            LiveEventAttendanceRepository attendanceRepository,
                            LiveEventChatMessageRepository chatRepository,
                            LiveEventQuestionRepository questionRepository,
                            LiveEventPollRepository pollRepository,
                            LiveEventFeedbackRepository feedbackRepository) {
        this.eventService = eventService;
        this.snapshotRepository = snapshotRepository;
        this.registrationRepository = registrationRepository;
        this.attendanceRepository = attendanceRepository;
        this.chatRepository = chatRepository;
        this.questionRepository = questionRepository;
        this.pollRepository = pollRepository;
        this.feedbackRepository = feedbackRepository;
    }

    @Transactional
    public void captureSnapshot(UUID tenantId, UUID eventId) {
        LiveEventEntity event = eventService.get(tenantId, eventId);
        Map<String, Object> computed = computeMetrics(event);
        for (Map.Entry<String, Object> entry : computed.entrySet()) {
            if (entry.getValue() instanceof Number number) {
                saveMetric(eventId, entry.getKey(), BigDecimal.valueOf(number.doubleValue()));
            }
        }
        if (event.getCpdPoints() != null) {
            saveMetric(eventId, "cpd_points", event.getCpdPoints());
        }
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getDashboardMetrics(UUID tenantId, UUID eventId) {
        LiveEventEntity event = eventService.get(tenantId, eventId);
        List<LiveEventAnalyticsSnapshotEntity> snapshots =
                snapshotRepository.findByEventIdOrderByCapturedAtDesc(eventId);
        Map<String, Object> metrics = new LinkedHashMap<>();
        for (LiveEventAnalyticsSnapshotEntity snap : snapshots) {
            metrics.putIfAbsent(snap.getMetricName(), snap.getMetricValue());
        }
        if (metrics.isEmpty()) {
            metrics.putAll(computeMetrics(event));
        }
        return metrics;
    }

    @Transactional(readOnly = true)
    public List<LiveEventAnalyticsSnapshotEntity> getHistory(UUID tenantId, UUID eventId, String metricName) {
        eventService.get(tenantId, eventId);
        return snapshotRepository.findByEventIdAndMetricNameOrderByCapturedAtDesc(eventId, metricName);
    }

    private Map<String, Object> computeMetrics(LiveEventEntity event) {
        UUID eventId = event.getId();
        long registrations = registrationRepository.countByEventIdAndRegistrationStatus(eventId, "REGISTERED");
        List<LiveEventAttendanceEntity> attendees = attendanceRepository.findByEventId(eventId);
        long attendeeCount = attendees.size();
        long cpdEligible = attendanceRepository.countByEventIdAndEligibleForCpdTrue(eventId);
        long chatMessages = chatRepository.findByEventIdOrderByCreatedAtAsc(eventId).size();
        long questions = questionRepository.findByEventIdOrderByPinnedDescUpvotesDescCreatedAtAsc(eventId).size();
        long polls = pollRepository.findByEventIdOrderByCreatedAtDesc(eventId).size();
        long replayViews = attendees.stream().filter(a -> a.getReplayWatchMinutes() > 0).count();
        long completed = attendees.stream().filter(a -> "COMPLETED".equals(a.getCompletionStatus())).count();
        long citizenAttendees = attendees.stream().filter(a -> "CITIZEN".equalsIgnoreCase(a.getParticipantType())).count();
        long providerAttendees = attendees.stream().filter(a -> "PROVIDER".equalsIgnoreCase(a.getParticipantType())).count();
        long earlyDropOff = computeEarlyDropOff(attendees, event.getEndTime());
        long peakConcurrent = computePeakConcurrent(attendees);
        double avgWatchMinutes = attendees.stream()
                .mapToInt(LiveEventAttendanceEntity::getTotalWatchMinutes)
                .average()
                .orElse(0);
        double completionRate = attendeeCount == 0 ? 0
                : (completed * 100.0) / attendeeCount;
        double dropOffRate = attendeeCount == 0 ? 0
                : (earlyDropOff * 100.0) / attendeeCount;
        double avgFeedbackRating = feedbackRepository.findByEventId(eventId).stream()
                .map(LiveEventFeedbackEntity::getRating)
                .filter(r -> r != null && r > 0)
                .mapToInt(Integer::intValue)
                .average()
                .orElse(0);

        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("registrations", registrations);
        metrics.put("attendees", attendeeCount);
        metrics.put("cpd_eligible", cpdEligible);
        metrics.put("chat_messages", chatMessages);
        metrics.put("questions_submitted", questions);
        metrics.put("polls_created", polls);
        metrics.put("peak_concurrent_viewers", peakConcurrent);
        metrics.put("replay_views", replayViews);
        metrics.put("avg_watch_minutes", round(avgWatchMinutes));
        metrics.put("completion_rate_pct", round(completionRate));
        metrics.put("drop_off_rate_pct", round(dropOffRate));
        metrics.put("avg_feedback_rating", round(avgFeedbackRating));
        metrics.put("citizen_attendees", citizenAttendees);
        metrics.put("provider_attendees", providerAttendees);
        if (event.getProvinceId() != null) {
            metrics.put("event_province_id", event.getProvinceId());
        }
        if (event.getDistrictId() != null) {
            metrics.put("event_district_id", event.getDistrictId());
        }
        if (event.getLinkedMadiDriveId() != null) {
            metrics.put("madi_drive_linked", 1);
            metrics.put("madi_attendee_conversion", attendeeCount);
            metrics.put("madi_registration_conversion", registrations);
        } else {
            metrics.put("madi_drive_linked", 0);
        }
        return metrics;
    }

    private static long computePeakConcurrent(List<LiveEventAttendanceEntity> attendees) {
        record Point(OffsetDateTime time, int delta) {}
        List<Point> points = new ArrayList<>();
        for (LiveEventAttendanceEntity a : attendees) {
            if (a.getJoinedAt() != null) {
                points.add(new Point(a.getJoinedAt(), 1));
            }
            if (a.getLeftAt() != null) {
                points.add(new Point(a.getLeftAt(), -1));
            }
        }
        points.sort(Comparator.comparing(Point::time));
        long peak = 0;
        long current = 0;
        for (Point p : points) {
            current += p.delta();
            peak = Math.max(peak, current);
        }
        return Math.max(peak, attendees.size());
    }

    private static long computeEarlyDropOff(List<LiveEventAttendanceEntity> attendees, OffsetDateTime eventEnd) {
        if (eventEnd == null) {
            return attendees.stream().filter(a -> a.getLeftAt() != null).count();
        }
        return attendees.stream()
                .filter(a -> a.getLeftAt() != null && a.getLeftAt().isBefore(eventEnd))
                .count();
    }

    private static double round(double value) {
        return BigDecimal.valueOf(value).setScale(1, RoundingMode.HALF_UP).doubleValue();
    }

    private void saveMetric(UUID eventId, String name, long value) {
        saveMetric(eventId, name, BigDecimal.valueOf(value));
    }

    private void saveMetric(UUID eventId, String name, BigDecimal value) {
        LiveEventAnalyticsSnapshotEntity snap = new LiveEventAnalyticsSnapshotEntity();
        snap.setEventId(eventId);
        snap.setMetricName(name);
        snap.setMetricValue(value);
        snapshotRepository.save(snap);
    }
}
