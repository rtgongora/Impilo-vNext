package zw.gov.mohcc.impilo.learning.fundo;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.learning.integration.MoodleWebServiceClient;
import zw.gov.mohcc.impilo.learning.persistence.entity.CourseEntity;
import zw.gov.mohcc.impilo.learning.persistence.entity.FundoPathwayEntity;
import zw.gov.mohcc.impilo.learning.persistence.entity.FundoPathwayItemEntity;
import zw.gov.mohcc.impilo.learning.persistence.repository.CourseRepository;
import zw.gov.mohcc.impilo.learning.persistence.repository.FundoPathwayItemRepository;
import zw.gov.mohcc.impilo.learning.persistence.repository.FundoPathwayRepository;

/**
 * Phase 6C — cohort completion report integration test. Exercises
 * {@link FundoCohortReportService} end-to-end through the real service
 * graph (enrolment + progress + certificate emission) and asserts the
 * shape of the aggregated report.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class FundoCohortReportIT {

    private static final UUID TENANT = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");

    @Autowired private FundoCohortReportService reportService;
    @Autowired private FundoEnrolmentService enrolmentService;
    @Autowired private FundoProgressService progressService;
    @Autowired private FundoCertificateService certificateService;
    @Autowired private CourseRepository courseRepository;
    @Autowired private FundoPathwayRepository pathwayRepository;
    @Autowired private FundoPathwayItemRepository pathwayItemRepository;
    @MockBean private MoodleWebServiceClient moodleWebServiceClient;

    private CourseEntity courseA;
    private CourseEntity courseB;
    private FundoPathwayEntity pathway;

    @BeforeEach
    void seedCohortData() {
        courseA = newPublishedCourse("FUNDO-COHORT-A", "Cohort A", true);
        courseB = newPublishedCourse("FUNDO-COHORT-B", "Cohort B", false);
        courseRepository.save(courseA);
        courseRepository.save(courseB);

        pathway = new FundoPathwayEntity();
        pathway.setTenantId(TENANT);
        pathway.setCode("FUNDO-COHORT-PATH");
        pathway.setTitle("Cohort Pathway");
        pathway.setStatus("PUBLISHED");
        pathwayRepository.save(pathway);
        addPathwayItem(pathway, courseA, 1);
        addPathwayItem(pathway, courseB, 2);

        // 3 enrolments in courseA: 1 still enrolled, 1 completed (with cert), 1 cancelled.
        UUID enrolment1 = createEnrolment(courseA, "TRAINEE-1");
        UUID enrolment2 = createEnrolment(courseA, "TRAINEE-2");
        UUID enrolment3 = createEnrolment(courseA, "TRAINEE-3");
        completeEnrolment(enrolment2);
        certificateService.issueForEnrolment(TENANT, enrolment2);
        enrolmentService.cancel(TENANT, enrolment3, "no-longer-required");

        // 2 enrolments in courseB: both completed (one with a cert).
        UUID enrolment4 = createEnrolment(courseB, "TRAINEE-4");
        UUID enrolment5 = createEnrolment(courseB, "TRAINEE-5");
        completeEnrolment(enrolment4);
        completeEnrolment(enrolment5);
        certificateService.issueForEnrolment(TENANT, enrolment4);
    }

    private CourseEntity newPublishedCourse(String code, String title, boolean cpd) {
        CourseEntity c = new CourseEntity();
        c.setTenantId(TENANT);
        c.setCode(code);
        c.setTitle(title);
        c.setStatus("PUBLISHED");
        c.setCategory("ORIENTATION");
        c.setLevel("INTRODUCTORY");
        c.setLanguage("en");
        c.setMandatory(false);
        c.setCpdEligible(cpd);
        c.setCpdPoints(cpd ? 1 : null);
        c.setVersion(1);
        return c;
    }

    private void addPathwayItem(FundoPathwayEntity p, CourseEntity course, int seq) {
        FundoPathwayItemEntity it = new FundoPathwayItemEntity();
        it.setPathwayId(p.getId());
        it.setCourseId(course.getId());
        it.setSequenceNo(seq);
        it.setRequired(true);
        pathwayItemRepository.save(it);
    }

    private UUID createEnrolment(CourseEntity course, String subjectId) {
        Map<String, Object> r = enrolmentService.create(
                new FundoEnrolmentService.EnrolmentRequest(
                        TENANT, "PROVIDER", subjectId, course.getId(), null, "SELF", null, null));
        return UUID.fromString((String) r.get("id"));
    }

    private void completeEnrolment(UUID enrolmentId) {
        progressService.recordProgress(TENANT,
                new FundoProgressService.ProgressUpdate(enrolmentId, null, null, 100, null));
    }

    @Test
    @DisplayName("Tenant-wide report rolls up enrolment, completion and certificate counts")
    void tenantWideReport() {
        Map<String, Object> data = reportService.cohortCompletions(
                TENANT, new FundoCohortReportService.CohortReportFilter(null, null));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) data.get("items");
        assertThat(items).hasSizeGreaterThanOrEqualTo(2);
        Map<String, Object> totals = cast(data.get("totals"));
        assertThat(totals.get("completedCount")).isEqualTo(3);
        assertThat(totals.get("cancelledCount")).isEqualTo(1);
        assertThat(totals.get("certificatesIssued")).isEqualTo(2);
    }

    @Test
    @DisplayName("Pathway filter narrows the report to the pathway's courses")
    void pathwayFilter() {
        Map<String, Object> data = reportService.cohortCompletions(
                TENANT, new FundoCohortReportService.CohortReportFilter(pathway.getId(), null));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) data.get("items");
        assertThat(items).hasSize(2);
        Map<String, Object> filter = cast(data.get("filter"));
        assertThat(filter.get("pathwayId")).isEqualTo(pathway.getId().toString());
    }

    @Test
    @DisplayName("Single-course filter returns one item with correct counts")
    void singleCourseFilter() {
        Map<String, Object> data = reportService.cohortCompletions(
                TENANT, new FundoCohortReportService.CohortReportFilter(null, courseA.getId()));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) data.get("items");
        assertThat(items).hasSize(1);
        Map<String, Object> row = items.get(0);
        assertThat(row.get("courseId")).isEqualTo(courseA.getId().toString());
        assertThat(row.get("totalEnrolments")).isEqualTo(3);
        assertThat(row.get("completedCount")).isEqualTo(1);
        assertThat(row.get("cancelledCount")).isEqualTo(1);
        assertThat(row.get("certificatesIssued")).isEqualTo(1);
    }

    @SuppressWarnings("unchecked")
    private static <T> T cast(Object o) { return (T) o; }
}
