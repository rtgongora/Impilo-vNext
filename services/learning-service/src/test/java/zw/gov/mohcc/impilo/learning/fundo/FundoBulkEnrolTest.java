package zw.gov.mohcc.impilo.learning.fundo;

import static org.assertj.core.api.Assertions.assertThat;

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
import zw.gov.mohcc.impilo.learning.persistence.repository.CourseRepository;

/** B6 — bulk-enrol contract used by the campaign/surveillance bridges. */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class FundoBulkEnrolTest {

    private static final UUID TENANT = UUID.fromString("b6b6b6b6-0000-4000-8000-000000000001");

    @Autowired private FundoEnrolmentService enrolments;
    @Autowired private CourseRepository courseRepository;
    @MockBean private MoodleWebServiceClient moodleWebServiceClient;

    @Test
    void bulkEnrolIsIdempotentPerSubject() {
        CourseEntity c = new CourseEntity();
        c.setTenantId(TENANT);
        c.setCode("OUTBREAK-101");
        c.setTitle("Contact tracing");
        c.setStatus("PUBLISHED");
        c.setLanguage("en");
        courseRepository.save(c);

        List<Map<String, Object>> subjects = List.of(
                Map.of("subjectType", "PROVIDER", "subjectId", "TRACER-1"),
                Map.of("subjectType", "PROVIDER", "subjectId", "TRACER-2"));

        Map<String, Object> first = enrolments.bulkEnrol(TENANT, c.getId(), subjects, "SYSTEM", "surveillance-bridge");
        assertThat(first.get("created")).isEqualTo(2);
        assertThat(first.get("alreadyEnrolled")).isEqualTo(0);

        // re-run → idempotent (no new active enrolments)
        Map<String, Object> second = enrolments.bulkEnrol(TENANT, c.getId(), subjects, "SYSTEM", "surveillance-bridge");
        assertThat(second.get("created")).isEqualTo(0);
        assertThat(second.get("alreadyEnrolled")).isEqualTo(2);
    }
}
