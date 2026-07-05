package zw.gov.mohcc.impilo.learning.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import zw.gov.mohcc.impilo.learning.fundo.FundoLiveCompletionService;
import zw.gov.mohcc.impilo.learning.persistence.entity.EnrolmentEntity;
import zw.gov.mohcc.impilo.learning.persistence.entity.ScheduledLearningSessionEntity;
import zw.gov.mohcc.impilo.learning.persistence.entity.SessionAttendanceEntity;
import zw.gov.mohcc.impilo.learning.persistence.repository.EnrolmentRepository;
import zw.gov.mohcc.impilo.learning.persistence.repository.ScheduledLearningSessionRepository;
import zw.gov.mohcc.impilo.learning.persistence.repository.SessionAttendanceRepository;

/**
 * Consumes live-service media-truth events so webhook-accurate attendance and
 * event lifecycle drive Fundo learning truth (LEARNING_LIVE W3):
 *
 * <ul>
 *   <li>{@code impilo.live.attendance.updated.v1} → upsert the participant's
 *       {@code lrn_session_attendance} row (check-in method IMPILO_LIVE) with the
 *       absolute watch minutes reported by live-service. Only sessions linked to a
 *       live event via {@code lrn_scheduled_learning_session.live_event_id} (V027)
 *       are touched — standalone live events are ignored.</li>
 *   <li>{@code impilo.live.event.ended.v1} → run the completion-policy pass over
 *       the linked session's attendees; enrolments whose configured rules all pass
 *       are completed (certificate via the existing service). Courses without
 *       rules are untouched (legacy explicit webhook path).</li>
 * </ul>
 *
 * <p><strong>Envelope:</strong> live-service's outbox emitter wraps payloads as
 * {@code {"eventType":…, "aggregateType":…, "aggregateId":…, "occurredAt":…,
 * "payload":{…}}}; this consumer unwraps {@code payload} and also tolerates
 * flat payloads defensively.</p>
 *
 * <p><strong>Subject-resolution assumption:</strong> the live attendance
 * {@code participantId} is the same identity string as
 * {@code lrn_enrolment.subject_id} (both carry the actor's public id — see
 * live-service registrations and the Fundo webinar audience contract). The
 * attendance row's {@code subject_type} is taken from the matched enrolment;
 * when no enrolment matches, the row is recorded with subject type
 * {@code PARTICIPANT} so the attendance truth is not dropped.</p>
 *
 * <p>Errors are swallowed and logged so a poison message never kills the listener
 * (varapi estate consumer convention).</p>
 *
 * <p>TODO(W6): consume {@code impilo.live.poll.results.v1} (emitted on event end
 * since W3) and bridge poll results into {@code lrn_interactive_response}. The
 * bridge needs a governed {@code lrn_interactive_activity} row per poll (FK), so
 * it is deferred rather than half-built here.</p>
 */
@Component
public class LiveAttendanceConsumer {

    private static final Logger log = LoggerFactory.getLogger(LiveAttendanceConsumer.class);

    static final String CHECK_IN_METHOD_IMPILO_LIVE = "IMPILO_LIVE";
    static final String RECORDED_BY_IMPILO_LIVE = "impilo-live";
    static final String FALLBACK_SUBJECT_TYPE = "PARTICIPANT";
    private static final List<String> ACTIVE_STATUSES = List.of("ENROLLED", "IN_PROGRESS");

    private final ObjectMapper objectMapper;
    private final ScheduledLearningSessionRepository sessionRepository;
    private final SessionAttendanceRepository attendanceRepository;
    private final EnrolmentRepository enrolmentRepository;
    private final FundoLiveCompletionService liveCompletionService;

    public LiveAttendanceConsumer(ObjectMapper objectMapper,
                                  ScheduledLearningSessionRepository sessionRepository,
                                  SessionAttendanceRepository attendanceRepository,
                                  EnrolmentRepository enrolmentRepository,
                                  FundoLiveCompletionService liveCompletionService) {
        this.objectMapper = objectMapper;
        this.sessionRepository = sessionRepository;
        this.attendanceRepository = attendanceRepository;
        this.enrolmentRepository = enrolmentRepository;
        this.liveCompletionService = liveCompletionService;
    }

    @KafkaListener(
            topics = "${learning.live.attendance-updated-topic:impilo.live.attendance.updated.v1}",
            groupId = "${spring.kafka.consumer.group-id:learning-service}")
    public void onAttendanceUpdated(String message) {
        try {
            JsonNode n = unwrap(message);
            UUID liveEventId = uuidOrNull(text(n, "eventId"));
            String participantId = text(n, "participantId");
            if (liveEventId == null || participantId == null) {
                log.debug("live attendance.updated without eventId/participantId — skipped");
                return;
            }
            ScheduledLearningSessionEntity session = sessionRepository
                    .findFirstByLiveEventIdOrderByCreatedAtDesc(liveEventId)
                    .orElse(null);
            if (session == null) {
                // Standalone live event with no scheduled learning session — not ours.
                log.debug("live attendance.updated for unlinked event {} — skipped", liveEventId);
                return;
            }
            Integer watchMinutes = intOrNull(n, "totalWatchMinutes");
            if (watchMinutes == null) {
                watchMinutes = intOrNull(n, "liveWatchMinutes");
            }
            upsertAttendance(session, participantId, watchMinutes);
            log.info("Live attendance mapped: event {} participant {} -> session {} ({} min)",
                    liveEventId, participantId, session.getId(), watchMinutes);
        } catch (Exception e) {
            // Never poison the listener: log and move on (estate consumer convention).
            log.warn("Failed to handle impilo.live attendance.updated event: {}", e.getMessage());
        }
    }

    @KafkaListener(
            topics = "${learning.live.event-ended-topic:impilo.live.event.ended.v1}",
            groupId = "${spring.kafka.consumer.group-id:learning-service}")
    public void onEventEnded(String message) {
        try {
            JsonNode n = unwrap(message);
            UUID liveEventId = uuidOrNull(text(n, "eventId"));
            if (liveEventId == null) {
                log.debug("live event.ended without eventId — skipped");
                return;
            }
            ScheduledLearningSessionEntity session = sessionRepository
                    .findFirstByLiveEventIdOrderByCreatedAtDesc(liveEventId)
                    .orElse(null);
            if (session == null) {
                log.debug("live event.ended for unlinked event {} — skipped", liveEventId);
                return;
            }
            Map<String, Object> summary =
                    liveCompletionService.reconcileSessionCompletion(session.getTenantId(), session);
            log.info("Live event {} ended — completion pass over session {}: {}",
                    liveEventId, session.getId(), summary);
        } catch (Exception e) {
            log.warn("Failed to handle impilo.live event.ended event: {}", e.getMessage());
        }
    }

    private void upsertAttendance(ScheduledLearningSessionEntity session,
                                  String participantId, Integer watchMinutes) {
        EnrolmentEntity enrolment = resolveEnrolment(session, participantId);
        String subjectType = enrolment != null ? enrolment.getSubjectType() : FALLBACK_SUBJECT_TYPE;

        SessionAttendanceEntity attendance = attendanceRepository
                .findBySessionIdAndSubjectTypeAndSubjectId(session.getId(), subjectType, participantId)
                .orElseGet(SessionAttendanceEntity::new);
        attendance.setTenantId(session.getTenantId());
        attendance.setSessionId(session.getId());
        attendance.setSubjectType(subjectType);
        attendance.setSubjectId(participantId);
        attendance.setStatus("PRESENT");
        attendance.setCheckInMethod(CHECK_IN_METHOD_IMPILO_LIVE);
        if (attendance.getCheckInAt() == null) {
            attendance.setCheckInAt(OffsetDateTime.now());
        }
        if (watchMinutes != null) {
            // Absolute totals from live-service — overwrite, never accumulate, so a
            // redelivered event is idempotent.
            attendance.setWatchMinutes(watchMinutes);
        }
        if (enrolment != null && attendance.getEnrolmentId() == null) {
            attendance.setEnrolmentId(enrolment.getId());
        }
        attendance.setRecordedBy(RECORDED_BY_IMPILO_LIVE);
        attendance.setUpdatedAt(OffsetDateTime.now());
        attendanceRepository.save(attendance);
    }

    /**
     * Subject resolution: participantId == lrn_enrolment.subject_id on the session's
     * course. Prefers an active (ENROLLED/IN_PROGRESS) enrolment, falls back to any.
     */
    private EnrolmentEntity resolveEnrolment(ScheduledLearningSessionEntity session, String participantId) {
        List<EnrolmentEntity> courseEnrolments = enrolmentRepository
                .findByTenantIdAndCourseId(session.getTenantId(), session.getCourseId());
        return courseEnrolments.stream()
                .filter(e -> participantId.equals(e.getSubjectId()))
                .filter(e -> ACTIVE_STATUSES.contains(e.getStatus()))
                .findFirst()
                .orElseGet(() -> courseEnrolments.stream()
                        .filter(e -> participantId.equals(e.getSubjectId()))
                        .findFirst()
                        .orElse(null));
    }

    private JsonNode unwrap(String message) throws Exception {
        JsonNode root = objectMapper.readTree(message);
        JsonNode payload = root.path("payload");
        return payload.isObject() ? payload : root;
    }

    private static String text(JsonNode n, String field) {
        JsonNode v = n.path(field);
        return v.isMissingNode() || v.isNull() || v.asText().isBlank() ? null : v.asText();
    }

    private static Integer intOrNull(JsonNode n, String field) {
        JsonNode v = n.path(field);
        if (v.isMissingNode() || v.isNull()) {
            return null;
        }
        if (v.isNumber()) {
            return v.asInt();
        }
        try {
            return Integer.parseInt(v.asText().trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static UUID uuidOrNull(String value) {
        if (value == null) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
