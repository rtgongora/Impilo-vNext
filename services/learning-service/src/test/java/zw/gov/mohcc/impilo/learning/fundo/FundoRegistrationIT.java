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

/** A6 — registration (reuses enrolment) + placement sign-off + graduation, against H2. */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class FundoRegistrationIT {

    private static final UUID TENANT = UUID.fromString("a6a6a6a6-0000-4000-8000-000000000001");

    @Autowired private FundoAcademicService academic;
    @Autowired private FundoStudentService students;
    @Autowired private FundoRegistrationService registration;
    @Autowired private CourseRepository courseRepository;
    @MockBean private MoodleWebServiceClient moodleWebServiceClient;

    @SuppressWarnings("unchecked")
    @Test
    void registrationPlacementGraduation() {
        Map<String, Object> program = academic.createProgram(TENANT, "registrar", Map.of("code", "DIP-EH", "title", "Diploma in Environmental Health"));
        String programId = (String) program.get("id");
        Map<String, Object> student = students.admitStudent(TENANT, "registrar", Map.of(
                "programId", programId, "subjectType", "STUDENT", "subjectId", "S-1"));
        String studentId = (String) student.get("id");

        CourseEntity course = new CourseEntity();
        course.setTenantId(TENANT);
        course.setCode("EH-101");
        course.setTitle("Intro EH");
        course.setStatus("PUBLISHED");
        course.setLanguage("en");
        courseRepository.save(course);

        // register (creates an academic enrolment under the hood)
        Map<String, Object> reg = registration.registerCourse(TENANT, studentId, "registrar", Map.of("courseId", course.getId().toString()));
        assertThat(reg.get("enrolmentId")).isNotNull();
        assertThat(((List<?>) registration.listRegistrations(TENANT, studentId).get("items"))).hasSize(1);

        // placement + sign-off
        Map<String, Object> placement = registration.createPlacement(TENANT, studentId, "registrar", Map.of("title", "District rotation"));
        String placementId = (String) placement.get("id");
        Map<String, Object> signed = registration.signoffPlacement(TENANT, placementId, "preceptor-1", Map.of("signoffStatus", "SIGNED_OFF", "signoffNotes", "Competent"));
        assertThat(signed.get("signoffStatus")).isEqualTo("SIGNED_OFF");
        assertThat(signed.get("signedOffBy")).isEqualTo("preceptor-1");

        // graduate
        Map<String, Object> grad = registration.graduate(TENANT, studentId, "registrar", Map.of());
        assertThat(grad.get("registryNumber").toString()).startsWith("GRAD-");
        assertThat(grad.get("qualificationTitle")).isEqualTo("Diploma in Environmental Health");

        // student now graduated, academic record aggregates everything
        Map<String, Object> record = registration.academicRecord(TENANT, studentId);
        assertThat(record.get("status")).isEqualTo("GRADUATED");
        assertThat(((List<?>) record.get("registrations"))).hasSize(1);
        assertThat(((List<?>) record.get("placements"))).hasSize(1);
        assertThat(((List<?>) record.get("graduation"))).hasSize(1);
    }
}
