package zw.gov.mohcc.impilo.learning.fundo;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.learning.integration.MoodleWebServiceClient;
import zw.gov.mohcc.impilo.learning.persistence.entity.CourseEntity;
import zw.gov.mohcc.impilo.learning.persistence.repository.CourseRepository;

/**
 * Live-caught regression (2026-07-19 browser-bar wave): the shell enrols
 * learners as USER_HEALTH_ID / PROVIDER_PUBLIC_ID, but the V006 check
 * constraint only admitted the legacy cohort classes — every browser
 * self-enrolment died on insert as a raw 500. V030 widens the constraint;
 * this suite pins the service-level contract and its lockstep with the
 * migration.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class FundoEnrolmentSubjectTypeTest {

    private static final UUID TENANT = UUID.fromString("30303030-0000-4000-8000-000000000001");

    @Autowired private FundoEnrolmentService enrolments;
    @Autowired private CourseRepository courseRepository;
    @MockBean private MoodleWebServiceClient moodleWebServiceClient;

    private CourseEntity publishedCourse() {
        CourseEntity c = new CourseEntity();
        c.setTenantId(TENANT);
        c.setCode("SUBJ-TYPE-" + UUID.randomUUID().toString().substring(0, 8));
        c.setTitle("Subject-type contract course");
        c.setStatus("PUBLISHED");
        c.setLanguage("en");
        return courseRepository.save(c);
    }

    @Test
    void citizenHealthIdSubjectCanSelfEnrol() {
        CourseEntity c = publishedCourse();
        Map<String, Object> view = enrolments.create(new FundoEnrolmentService.EnrolmentRequest(
                TENANT, "USER_HEALTH_ID", "c0000000-0000-4000-8000-000000000012",
                c.getId(), null, "SELF", null, null));
        assertThat(view.get("id")).isNotNull();
        assertThat(view.get("status")).isEqualTo("ENROLLED");
    }

    @Test
    void providerPublicIdSubjectCanSelfEnrol() {
        CourseEntity c = publishedCourse();
        Map<String, Object> view = enrolments.create(new FundoEnrolmentService.EnrolmentRequest(
                TENANT, "PROVIDER_PUBLIC_ID", "PROV-ZW-00011",
                c.getId(), null, "SELF", null, null));
        assertThat(view.get("id")).isNotNull();
    }

    @Test
    void allowedSubjectTypesStayInLockstepWithTheV030Constraint() throws Exception {
        String migration = new String(
                new ClassPathResource("db/migration/V030__learning_enrolment_subject_types_identity_classes.sql")
                        .getInputStream().readAllBytes(),
                StandardCharsets.UTF_8);
        for (String subjectType : FundoEnrolmentService.ALLOWED_SUBJECT_TYPES) {
            assertThat(migration)
                    .as("V030 constraint must admit %s (service ALLOWED_SUBJECT_TYPES lockstep)", subjectType)
                    .contains("'" + subjectType + "'");
        }
    }
}
