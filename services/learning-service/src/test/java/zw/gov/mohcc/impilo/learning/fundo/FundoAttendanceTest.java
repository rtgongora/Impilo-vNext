package zw.gov.mohcc.impilo.learning.fundo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.learning.integration.MoodleWebServiceClient;
import zw.gov.mohcc.impilo.learning.persistence.entity.CourseEntity;
import zw.gov.mohcc.impilo.learning.persistence.entity.ScheduledLearningSessionEntity;
import zw.gov.mohcc.impilo.learning.persistence.repository.CourseRepository;
import zw.gov.mohcc.impilo.learning.persistence.repository.ScheduledLearningSessionRepository;

/** A2 — attendance + check-in end-to-end against H2. */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class FundoAttendanceTest {

    private static final UUID TENANT = UUID.fromString("a2a2a2a2-0000-4000-8000-000000000001");

    @Autowired private FundoAttendanceService attendance;
    @Autowired private CourseRepository courseRepository;
    @Autowired private ScheduledLearningSessionRepository sessionRepository;
    @MockBean private MoodleWebServiceClient moodleWebServiceClient;

    private UUID newSession() {
        CourseEntity course = new CourseEntity();
        course.setTenantId(TENANT);
        course.setCode("A2-COURSE-" + UUID.randomUUID());
        course.setTitle("A2 Course");
        course.setStatus("PUBLISHED");
        course.setLanguage("en");
        courseRepository.save(course);
        ScheduledLearningSessionEntity s = new ScheduledLearningSessionEntity();
        s.setTenantId(TENANT);
        s.setCourseId(course.getId());
        s.setTitle("A2 Session");
        s.setSessionType("CLASSROOM");
        s.setStartsAt(OffsetDateTime.now());
        s.setCreatedBy("tester");
        sessionRepository.save(s);
        return s.getId();
    }

    @SuppressWarnings("unchecked")
    @Test
    void facilitatorMarkAndSelfCheckin() {
        String sessionId = newSession().toString();

        // facilitator bulk-mark
        Map<String, Object> marked = attendance.markAttendance(TENANT, sessionId, "trainer", Map.of(
                "entries", List.of(
                        Map.of("subjectType", "PROVIDER", "subjectId", "PRV-1", "status", "PRESENT"),
                        Map.of("subjectType", "PROVIDER", "subjectId", "PRV-2", "status", "ABSENT"))));
        assertThat(marked.get("marked")).isEqualTo(2);

        // re-mark PRV-2 to LATE (upsert, not duplicate)
        attendance.markAttendance(TENANT, sessionId, "trainer", Map.of(
                "entries", List.of(Map.of("subjectType", "PROVIDER", "subjectId", "PRV-2", "status", "LATE"))));
        List<Map<String, Object>> items = (List<Map<String, Object>>) attendance.listAttendance(TENANT, sessionId).get("items");
        assertThat(items).hasSize(2);

        // token + self check-in
        Map<String, Object> token = attendance.createCheckinToken(TENANT, sessionId, "trainer", Map.of("singleUse", true));
        String code = (String) token.get("token");
        Map<String, Object> checked = attendance.selfCheckin(TENANT, sessionId, Map.of(
                "token", code, "subjectType", "PROVIDER", "subjectId", "PRV-3", "method", "QR"));
        assertThat(checked.get("status")).isEqualTo("PRESENT");

        // single-use token cannot be reused
        assertThatThrownBy(() -> attendance.selfCheckin(TENANT, sessionId, Map.of(
                "token", code, "subjectType", "PROVIDER", "subjectId", "PRV-4")))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat((List<Map<String, Object>>) attendance.listAttendance(TENANT, sessionId).get("items")).hasSize(3);
    }

    @Test
    void expiredTokenRejected() {
        String sessionId = newSession().toString();
        Map<String, Object> token = attendance.createCheckinToken(TENANT, sessionId, "trainer", Map.of("ttlMinutes", 0));
        String code = (String) token.get("token");
        assertThatThrownBy(() -> attendance.selfCheckin(TENANT, sessionId, Map.of(
                "token", code, "subjectType", "PROVIDER", "subjectId", "PRV-9")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
