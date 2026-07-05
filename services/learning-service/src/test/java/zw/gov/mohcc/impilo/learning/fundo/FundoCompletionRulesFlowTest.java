package zw.gov.mohcc.impilo.learning.fundo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.learning.core.LearningV11NativeService;
import zw.gov.mohcc.impilo.learning.integration.LiveSessionIntegration;
import zw.gov.mohcc.impilo.learning.integration.MoodleWebServiceClient;
import zw.gov.mohcc.impilo.learning.persistence.entity.CourseEntity;
import zw.gov.mohcc.impilo.learning.persistence.entity.EnrolmentEntity;
import zw.gov.mohcc.impilo.learning.persistence.entity.ScheduledLearningSessionEntity;
import zw.gov.mohcc.impilo.learning.persistence.entity.SessionAttendanceEntity;
import zw.gov.mohcc.impilo.learning.persistence.repository.CertificateRepository;
import zw.gov.mohcc.impilo.learning.persistence.repository.CourseRepository;
import zw.gov.mohcc.impilo.learning.persistence.repository.EnrolmentRepository;
import zw.gov.mohcc.impilo.learning.persistence.repository.ScheduledLearningSessionRepository;
import zw.gov.mohcc.impilo.learning.persistence.repository.SessionAttendanceRepository;

/**
 * W3 end-to-end against H2 (runs in the default surefire suite — the repo's
 * {@code *IT} classes are excluded by surefire's default includes): completion-rule
 * configuration, V027 live-linkage column writes on session scheduling, and the
 * event-ended completion pass (attendance threshold → COMPLETED + certificate).
 *
 * <p>{@link FundoNotificationIntentWriter} is mocked because it inserts into the
 * jdbc-only {@code lrn_learning_notification} table, which does not exist in the
 * entity-derived H2 schema (same gap that keeps {@code FundoNativeLmsIT} from
 * running standalone).</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class FundoCompletionRulesFlowTest {

    private static final UUID TENANT = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Autowired private FundoCompletionPolicyService policyService;
    @Autowired private FundoLiveCompletionService liveCompletionService;
    @Autowired private LearningV11NativeService nativeService;
    @Autowired private CourseRepository courseRepository;
    @Autowired private EnrolmentRepository enrolmentRepository;
    @Autowired private ScheduledLearningSessionRepository sessionRepository;
    @Autowired private SessionAttendanceRepository attendanceRepository;
    @Autowired private CertificateRepository certificateRepository;
    @MockBean private MoodleWebServiceClient moodleWebServiceClient;
    @MockBean private LiveSessionIntegration liveSessionIntegration;
    @MockBean private FundoNotificationIntentWriter notificationIntentWriter;

    private CourseEntity newCourse() {
        CourseEntity course = new CourseEntity();
        course.setTenantId(TENANT);
        course.setCode("W3-COURSE-" + UUID.randomUUID());
        course.setTitle("W3 Live Course");
        course.setStatus("PUBLISHED");
        course.setLanguage("en");
        return courseRepository.save(course);
    }

    private EnrolmentEntity newEnrolment(UUID courseId, String subjectId) {
        EnrolmentEntity e = new EnrolmentEntity();
        e.setTenantId(TENANT);
        e.setCourseId(courseId);
        e.setSubjectType("PROVIDER");
        e.setSubjectId(subjectId);
        e.setEnrolmentType("LIVE_EVENT");
        e.setStatus("IN_PROGRESS");
        return enrolmentRepository.save(e);
    }

    @Test
    void completionRuleCrudRoundTrip() {
        CourseEntity course = newCourse();

        Map<String, Object> created = policyService.upsertRule(TENANT, course.getId(), "tester", Map.of(
                "ruleType", "attendance_threshold",
                "thresholdValue", "30"));
        assertThat(created.get("ruleType")).isEqualTo("ATTENDANCE_THRESHOLD");
        assertThat(created.get("required")).isEqualTo(true);

        // Upsert on the same rule type updates in place (V028 unique constraint).
        Map<String, Object> updated = policyService.upsertRule(TENANT, course.getId(), "tester", Map.of(
                "ruleType", "ATTENDANCE_THRESHOLD",
                "thresholdValue", "45"));
        assertThat(updated.get("id")).isEqualTo(created.get("id"));

        Map<String, Object> listed = policyService.listRules(TENANT, course.getId());
        assertThat((List<?>) listed.get("items")).hasSize(1);

        policyService.deleteRule(TENANT, UUID.fromString(created.get("id").toString()));
        assertThat((List<?>) policyService.listRules(TENANT, course.getId()).get("items")).isEmpty();
    }

    @Test
    void scheduledSessionWritesLiveLinkageColumnsAlongsideMetadata() {
        CourseEntity course = newCourse();
        String liveEventId = UUID.randomUUID().toString();
        when(liveSessionIntegration.isEnabled()).thenReturn(true);
        when(liveSessionIntegration.buildFundoWebinarPayload(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenCallRealMethod();
        when(liveSessionIntegration.scheduleFundoWebinar(any()))
                .thenReturn(Optional.of(Map.of("id", liveEventId)));

        Map<String, Object> out = nativeService.createScheduledSession(TENANT, "tester", Map.of(
                "courseId", course.getId().toString(),
                "title", "W3 Webinar",
                "sessionType", "VIRTUAL",
                "startsAt", OffsetDateTime.now().plusDays(1).toString()));

        @SuppressWarnings("unchecked")
        Map<String, Object> session = (Map<String, Object>) out.get("session");
        assertThat(session.get("sessionMode")).isEqualTo("LIVE");
        assertThat(session.get("liveEventId")).isEqualTo(liveEventId);
        assertThat(session.get("joinPath")).isEqualTo("/live/event/" + liveEventId);

        // Columns are the join surface (V027) — verify the row, not just the response.
        ScheduledLearningSessionEntity row = sessionRepository
                .findByTenantIdAndId(TENANT, UUID.fromString(session.get("id").toString()))
                .orElseThrow();
        assertThat(row.getSessionMode()).isEqualTo("LIVE");
        assertThat(row.getLiveEventId()).isEqualTo(UUID.fromString(liveEventId));
        assertThat(row.getJoinPath()).isEqualTo("/live/event/" + liveEventId);
        // metadata_json keeps the compat keys (dual-write).
        assertThat(row.getMetadataJson()).contains("impiloLiveEventId").contains(liveEventId);

        // list surface exposes the fields for the BFF passthrough.
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items =
                (List<Map<String, Object>>) nativeService.listScheduledSessions(TENANT, 50).get("items");
        Map<String, Object> listed = items.stream()
                .filter(i -> i.get("id").equals(session.get("id")))
                .findFirst().orElseThrow();
        assertThat(listed.get("sessionMode")).isEqualTo("LIVE");
        assertThat(listed.get("liveEventId")).isEqualTo(liveEventId);
        assertThat(listed.get("joinPath")).isEqualTo("/live/event/" + liveEventId);
    }

    @Test
    void nonLiveSessionDefaultsToInPerson() {
        CourseEntity course = newCourse();
        when(liveSessionIntegration.isEnabled()).thenReturn(false);

        Map<String, Object> out = nativeService.createScheduledSession(TENANT, "tester", Map.of(
                "courseId", course.getId().toString(),
                "title", "Classroom Session",
                "sessionType", "CLASSROOM",
                "startsAt", OffsetDateTime.now().plusDays(1).toString()));

        @SuppressWarnings("unchecked")
        Map<String, Object> session = (Map<String, Object>) out.get("session");
        ScheduledLearningSessionEntity row = sessionRepository
                .findByTenantIdAndId(TENANT, UUID.fromString(session.get("id").toString()))
                .orElseThrow();
        assertThat(row.getSessionMode()).isEqualTo("IN_PERSON");
        assertThat(row.getLiveEventId()).isNull();
    }

    @Test
    void eventEndedPassCompletesEnrolmentWhoseAttendanceRulePasses() {
        CourseEntity course = newCourse();
        policyService.upsertRule(TENANT, course.getId(), "tester", Map.of(
                "ruleType", "ATTENDANCE_THRESHOLD",
                "thresholdValue", new BigDecimal(30)));

        EnrolmentEntity qualifying = newEnrolment(course.getId(), "provider-attended");
        EnrolmentEntity shortfall = newEnrolment(course.getId(), "provider-short");

        ScheduledLearningSessionEntity session = new ScheduledLearningSessionEntity();
        session.setTenantId(TENANT);
        session.setCourseId(course.getId());
        session.setTitle("W3 Live Session");
        session.setSessionType("VIRTUAL");
        session.setSessionMode("LIVE");
        session.setLiveEventId(UUID.randomUUID());
        session.setStartsAt(OffsetDateTime.now().minusHours(2));
        session.setCreatedBy("tester");
        sessionRepository.save(session);

        attendanceRow(session, qualifying, 45);
        attendanceRow(session, shortfall, 10);

        Map<String, Object> summary = liveCompletionService.reconcileSessionCompletion(TENANT, session);

        assertThat(summary.get("completed")).isEqualTo(1);
        assertThat(summary.get("certificatesIssued")).isEqualTo(1);
        assertThat(enrolmentRepository.findByTenantIdAndId(TENANT, qualifying.getId()).orElseThrow().getStatus())
                .isEqualTo("COMPLETED");
        assertThat(enrolmentRepository.findByTenantIdAndId(TENANT, shortfall.getId()).orElseThrow().getStatus())
                .isEqualTo("IN_PROGRESS");
        assertThat(certificateRepository.findByEnrolmentId(qualifying.getId())).isPresent();
        assertThat(certificateRepository.findByEnrolmentId(shortfall.getId())).isEmpty();

        // Idempotent: a second pass changes nothing.
        Map<String, Object> second = liveCompletionService.reconcileSessionCompletion(TENANT, session);
        assertThat(second.get("completed")).isEqualTo(0);
    }

    @Test
    void facilitatorConfirmSatisfiesConfirmRule() {
        CourseEntity course = newCourse();
        policyService.upsertRule(TENANT, course.getId(), "tester", Map.of("ruleType", "FACILITATOR_CONFIRM"));
        EnrolmentEntity enrolment = newEnrolment(course.getId(), "provider-confirm");

        assertThat(policyService.evaluate(TENANT, enrolment).complete()).isFalse();

        Map<String, Object> confirmed = policyService.facilitatorConfirm(TENANT, enrolment.getId(), "facilitator-1");
        assertThat(confirmed.get("idempotent")).isEqualTo(false);
        assertThat(policyService.evaluate(TENANT, enrolment).complete()).isTrue();

        // Repeat confirm is idempotent.
        assertThat(policyService.facilitatorConfirm(TENANT, enrolment.getId(), "facilitator-2").get("idempotent"))
                .isEqualTo(true);
    }

    private void attendanceRow(ScheduledLearningSessionEntity session, EnrolmentEntity enrolment, int minutes) {
        SessionAttendanceEntity a = new SessionAttendanceEntity();
        a.setTenantId(TENANT);
        a.setSessionId(session.getId());
        a.setSubjectType(enrolment.getSubjectType());
        a.setSubjectId(enrolment.getSubjectId());
        a.setEnrolmentId(enrolment.getId());
        a.setStatus("PRESENT");
        a.setCheckInMethod("IMPILO_LIVE");
        a.setCheckInAt(OffsetDateTime.now().minusHours(1));
        a.setWatchMinutes(minutes);
        a.setRecordedBy("impilo-live");
        attendanceRepository.save(a);
    }
}
