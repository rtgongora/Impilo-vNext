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

/** A5 — application → decision → admission, end-to-end against H2. */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class FundoStudentIT {

    private static final UUID TENANT = UUID.fromString("a5a5a5a5-0000-4000-8000-000000000001");

    @Autowired private FundoAcademicService academic;
    @Autowired private FundoStudentService students;
    @MockBean private MoodleWebServiceClient moodleWebServiceClient;

    @SuppressWarnings("unchecked")
    @Test
    void admissionLifecycle() {
        Map<String, Object> program = academic.createProgram(TENANT, "registrar", Map.of(
                "code", "DIP-LAB", "title", "Diploma in Lab Science"));
        String programId = (String) program.get("id");

        Map<String, Object> app = students.submitApplication(TENANT, Map.of(
                "programId", programId, "applicantName", "Tendai Z", "applicantSubjectType", "CITIZEN",
                "applicantSubjectId", "HID-1"));
        String appId = (String) app.get("id");
        assertThat(app.get("status")).isEqualTo("SUBMITTED");

        students.decideApplication(TENANT, appId, "registrar", Map.of("status", "UNDER_REVIEW"));
        assertThat(((List<?>) students.listApplications(TENANT, "UNDER_REVIEW", null, 50).get("items"))).hasSize(1);

        // admit from the application
        Map<String, Object> student = students.admitStudent(TENANT, "registrar", Map.of("applicationId", appId));
        assertThat(student.get("studentNumber").toString()).startsWith("STU-");
        assertThat(student.get("status")).isEqualTo("ADMITTED");
        assertThat(student.get("subjectId")).isEqualTo("HID-1");

        // application auto-accepted on admission
        List<Map<String, Object>> accepted = (List<Map<String, Object>>) students.listApplications(TENANT, "ACCEPTED", null, 50).get("items");
        assertThat(accepted).hasSize(1);

        assertThat(((List<?>) students.listStudents(TENANT, programId, 50).get("items"))).hasSize(1);
    }
}
