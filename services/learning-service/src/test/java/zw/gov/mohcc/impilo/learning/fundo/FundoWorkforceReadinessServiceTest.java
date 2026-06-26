package zw.gov.mohcc.impilo.learning.fundo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import zw.gov.mohcc.impilo.learning.persistence.entity.CertificateEntity;
import zw.gov.mohcc.impilo.learning.persistence.entity.CourseEntity;
import zw.gov.mohcc.impilo.learning.persistence.entity.EnrolmentEntity;
import zw.gov.mohcc.impilo.learning.persistence.repository.CertificateRepository;
import zw.gov.mohcc.impilo.learning.persistence.repository.CourseRepository;
import zw.gov.mohcc.impilo.learning.persistence.repository.EnrolmentRepository;

/**
 * Unit coverage for the Fundo training-evidence / workforce-readiness summary that
 * fulfils the vashandi {@code /v1/internal/fundo/learners/{id}/cpd-summary} contract.
 */
@ExtendWith(MockitoExtension.class)
class FundoWorkforceReadinessServiceTest {

    private static final UUID TENANT = UUID.fromString("00000000-0000-4000-8000-000000000001");
    private static final String SUBJECT_TYPE = "PROVIDER";
    private static final String WORKER = "PRV-1001";

    @Mock private EnrolmentRepository enrolmentRepository;
    @Mock private CourseRepository courseRepository;
    @Mock private CertificateRepository certificateRepository;

    private FundoWorkforceReadinessService service;

    @BeforeEach
    void setUp() {
        service = new FundoWorkforceReadinessService(
                enrolmentRepository, courseRepository, certificateRepository);
    }

    private CourseEntity course(UUID id, String code, boolean mandatory) {
        CourseEntity c = new CourseEntity();
        c.setId(id);
        c.setTenantId(TENANT);
        c.setCode(code);
        c.setTitle(code + " title");
        c.setMandatory(mandatory);
        c.setCpdEligible(true);
        c.setCpdPoints(5);
        return c;
    }

    private EnrolmentEntity enrolment(UUID courseId, String type, String status, OffsetDateTime dueAt) {
        EnrolmentEntity e = new EnrolmentEntity();
        e.setId(UUID.randomUUID());
        e.setTenantId(TENANT);
        e.setSubjectType(SUBJECT_TYPE);
        e.setSubjectId(WORKER);
        e.setCourseId(courseId);
        e.setEnrolmentType(type);
        e.setStatus(status);
        e.setDueAt(dueAt);
        return e;
    }

    @Test
    void noEnrolmentsYieldsNoRequirements() {
        when(enrolmentRepository.findByTenantIdAndSubjectTypeAndSubjectIdOrderByCreatedAtDesc(
                        eq(TENANT), eq(SUBJECT_TYPE), eq(WORKER), any(Pageable.class)))
                .thenReturn(List.of());
        when(courseRepository.findAllById(any())).thenReturn(List.of());
        when(certificateRepository.findByTenantIdAndSubjectTypeAndSubjectId(TENANT, SUBJECT_TYPE, WORKER))
                .thenReturn(List.of());

        Map<String, Object> out = service.cpdSummary(TENANT, SUBJECT_TYPE, WORKER);

        assertThat(out.get("trainingReadiness")).isEqualTo("NO_REQUIREMENTS");
        assertThat(out.get("completedCourseCount")).isEqualTo(0);
        assertThat(out.get("cpdLedgerAuthority")).isEqualTo("varapi-service");
    }

    @Test
    void allMandatoryCompletedIsReady() {
        UUID cid = UUID.randomUUID();
        CourseEntity course = course(cid, "REG-101", true);
        when(enrolmentRepository.findByTenantIdAndSubjectTypeAndSubjectIdOrderByCreatedAtDesc(
                        eq(TENANT), eq(SUBJECT_TYPE), eq(WORKER), any(Pageable.class)))
                .thenReturn(List.of(enrolment(cid, "ASSIGNED", "COMPLETED", null)));
        when(courseRepository.findAllById(any())).thenReturn(List.of(course));
        when(certificateRepository.findByTenantIdAndSubjectTypeAndSubjectId(TENANT, SUBJECT_TYPE, WORKER))
                .thenReturn(List.of());

        Map<String, Object> out = service.cpdSummary(TENANT, SUBJECT_TYPE, WORKER);

        assertThat(out.get("trainingReadiness")).isEqualTo("READY");
        assertThat(out.get("completedCourseCount")).isEqualTo(1);
        @SuppressWarnings("unchecked")
        Map<String, Object> required = (Map<String, Object>) out.get("requiredLearning");
        assertThat(required.get("total")).isEqualTo(1);
        assertThat(required.get("satisfied")).isEqualTo(1);
    }

    @Test
    void overdueMandatoryIsNotReady() {
        UUID cid = UUID.randomUUID();
        CourseEntity course = course(cid, "DHO", true);
        when(enrolmentRepository.findByTenantIdAndSubjectTypeAndSubjectIdOrderByCreatedAtDesc(
                        eq(TENANT), eq(SUBJECT_TYPE), eq(WORKER), any(Pageable.class)))
                .thenReturn(List.of(enrolment(cid, "ASSIGNED", "IN_PROGRESS",
                        OffsetDateTime.now().minusDays(3))));
        when(courseRepository.findAllById(any())).thenReturn(List.of(course));
        when(certificateRepository.findByTenantIdAndSubjectTypeAndSubjectId(TENANT, SUBJECT_TYPE, WORKER))
                .thenReturn(List.of());

        Map<String, Object> out = service.cpdSummary(TENANT, SUBJECT_TYPE, WORKER);

        assertThat(out.get("trainingReadiness")).isEqualTo("NOT_READY");
        @SuppressWarnings("unchecked")
        Map<String, Object> required = (Map<String, Object>) out.get("requiredLearning");
        assertThat((List<?>) required.get("overdue")).hasSize(1);
    }

    @Test
    void outstandingButNotOverdueIsAtRisk() {
        UUID cid = UUID.randomUUID();
        CourseEntity course = course(cid, "EHR-101", true);
        when(enrolmentRepository.findByTenantIdAndSubjectTypeAndSubjectIdOrderByCreatedAtDesc(
                        eq(TENANT), eq(SUBJECT_TYPE), eq(WORKER), any(Pageable.class)))
                .thenReturn(List.of(enrolment(cid, "ASSIGNED", "IN_PROGRESS",
                        OffsetDateTime.now().plusDays(10))));
        when(courseRepository.findAllById(any())).thenReturn(List.of(course));
        when(certificateRepository.findByTenantIdAndSubjectTypeAndSubjectId(TENANT, SUBJECT_TYPE, WORKER))
                .thenReturn(List.of());

        Map<String, Object> out = service.cpdSummary(TENANT, SUBJECT_TYPE, WORKER);

        assertThat(out.get("trainingReadiness")).isEqualTo("AT_RISK");
    }

    @Test
    void expiredCertificateForcesNotReadyAndCpdCandidatesOnlyFromActive() {
        UUID cid = UUID.randomUUID();
        CourseEntity course = course(cid, "DQ-201", false);
        // self-enrolled completed (not required) -> NO_REQUIREMENTS unless cert expiry intervenes
        when(enrolmentRepository.findByTenantIdAndSubjectTypeAndSubjectIdOrderByCreatedAtDesc(
                        eq(TENANT), eq(SUBJECT_TYPE), eq(WORKER), any(Pageable.class)))
                .thenReturn(List.of(enrolment(cid, "SELF", "COMPLETED", null)));
        when(courseRepository.findAllById(any())).thenReturn(List.of(course));

        CertificateEntity expired = certificate(cid, "EXPIRED-CERT", OffsetDateTime.now().minusDays(1), true);
        CertificateEntity active = certificate(cid, "ACTIVE-CERT", OffsetDateTime.now().plusDays(200), true);
        when(certificateRepository.findByTenantIdAndSubjectTypeAndSubjectId(TENANT, SUBJECT_TYPE, WORKER))
                .thenReturn(List.of(expired, active));

        Map<String, Object> out = service.cpdSummary(TENANT, SUBJECT_TYPE, WORKER);

        assertThat(out.get("trainingReadiness")).isEqualTo("NOT_READY");
        assertThat(out.get("activeCertificateCount")).isEqualTo(1);
        assertThat(out.get("expiredCertificateCount")).isEqualTo(1);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> candidates = (List<Map<String, Object>>) out.get("cpdCandidates");
        assertThat(candidates).hasSize(1);
        assertThat(candidates.get(0).get("title")).isEqualTo("ACTIVE-CERT");
    }

    private CertificateEntity certificate(UUID courseId, String title, OffsetDateTime validUntil, boolean cpd) {
        CertificateEntity c = new CertificateEntity();
        c.setId(UUID.randomUUID());
        c.setTenantId(TENANT);
        c.setCourseId(courseId);
        c.setSubjectType(SUBJECT_TYPE);
        c.setSubjectId(WORKER);
        c.setCertificateNumber("CERT-" + title);
        c.setTitle(title);
        c.setIssuedAt(OffsetDateTime.now().minusDays(30));
        c.setValidUntil(validUntil);
        c.setStatus("ISSUED");
        c.setCpdEligible(cpd);
        c.setCpdPoints(5);
        return c;
    }
}
