package zw.gov.mohcc.impilo.learning.fundo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.learning.persistence.entity.CertificateEntity;
import zw.gov.mohcc.impilo.learning.persistence.entity.CourseEntity;
import zw.gov.mohcc.impilo.learning.persistence.entity.EnrolmentEntity;
import zw.gov.mohcc.impilo.learning.persistence.repository.CertificateRepository;
import zw.gov.mohcc.impilo.learning.persistence.repository.CourseRepository;
import zw.gov.mohcc.impilo.learning.persistence.repository.EnrolmentRepository;

@ExtendWith(MockitoExtension.class)
class FundoTrainingGateServiceTest {

    private static final UUID TENANT = UUID.fromString("00000000-0000-4000-8000-000000000001");
    private static final String TYPE = "PROVIDER";
    private static final String SUBJECT = "PRV-1";

    @Mock private CourseRepository courseRepository;
    @Mock private EnrolmentRepository enrolmentRepository;
    @Mock private CertificateRepository certificateRepository;

    private FundoTrainingGateService service;

    @BeforeEach
    void setUp() {
        service = new FundoTrainingGateService(courseRepository, enrolmentRepository, certificateRepository);
    }

    private CourseEntity course(UUID id, String code) {
        CourseEntity c = new CourseEntity();
        c.setId(id);
        c.setTenantId(TENANT);
        c.setCode(code);
        c.setTitle(code);
        return c;
    }

    private EnrolmentEntity completed(UUID courseId) {
        EnrolmentEntity e = new EnrolmentEntity();
        e.setId(UUID.randomUUID());
        e.setCourseId(courseId);
        e.setStatus("COMPLETED");
        return e;
    }

    @Test
    void satisfiedWhenCompletedAndNoCertificateGate() {
        UUID cid = UUID.randomUUID();
        when(courseRepository.findByTenantIdAndCode(TENANT, "REG-101")).thenReturn(Optional.of(course(cid, "REG-101")));
        when(enrolmentRepository.findFirstByTenantIdAndSubjectTypeAndSubjectIdAndCourseIdAndStatusIn(
                        TENANT, TYPE, SUBJECT, cid, List.of("COMPLETED")))
                .thenReturn(Optional.of(completed(cid)));
        when(certificateRepository.findByTenantIdAndSubjectTypeAndSubjectId(TENANT, TYPE, SUBJECT))
                .thenReturn(List.of());

        Map<String, Object> out = service.evaluate(TENANT, TYPE, SUBJECT, List.of("REG-101"));

        assertThat(out.get("satisfied")).isEqualTo(true);
        assertThat((List<?>) out.get("outstanding")).isEmpty();
        assertThat(out.get("decisionAuthority")).isEqualTo("tshepo-authz-service");
    }

    @Test
    void notSatisfiedWhenNotCompleted() {
        UUID cid = UUID.randomUUID();
        when(courseRepository.findByTenantIdAndCode(TENANT, "EHR-101")).thenReturn(Optional.of(course(cid, "EHR-101")));
        when(enrolmentRepository.findFirstByTenantIdAndSubjectTypeAndSubjectIdAndCourseIdAndStatusIn(
                        TENANT, TYPE, SUBJECT, cid, List.of("COMPLETED")))
                .thenReturn(Optional.empty());
        lenient().when(certificateRepository.findByTenantIdAndSubjectTypeAndSubjectId(TENANT, TYPE, SUBJECT))
                .thenReturn(List.of());

        Map<String, Object> out = service.evaluate(TENANT, TYPE, SUBJECT, List.of("EHR-101"));

        assertThat(out.get("satisfied")).isEqualTo(false);
        @SuppressWarnings("unchecked")
        List<String> outstanding = (List<String>) out.get("outstanding");
        assertThat(outstanding).containsExactly("EHR-101");
    }

    @Test
    void notSatisfiedWhenCompletedButCertificateExpired() {
        UUID cid = UUID.randomUUID();
        when(courseRepository.findByTenantIdAndCode(TENANT, "DHO")).thenReturn(Optional.of(course(cid, "DHO")));
        when(enrolmentRepository.findFirstByTenantIdAndSubjectTypeAndSubjectIdAndCourseIdAndStatusIn(
                        TENANT, TYPE, SUBJECT, cid, List.of("COMPLETED")))
                .thenReturn(Optional.of(completed(cid)));
        CertificateEntity expired = new CertificateEntity();
        expired.setId(UUID.randomUUID());
        expired.setCourseId(cid);
        expired.setStatus("EXPIRED");
        expired.setValidUntil(OffsetDateTime.now().minusDays(1));
        when(certificateRepository.findByTenantIdAndSubjectTypeAndSubjectId(TENANT, TYPE, SUBJECT))
                .thenReturn(List.of(expired));

        Map<String, Object> out = service.evaluate(TENANT, TYPE, SUBJECT, List.of("DHO"));

        assertThat(out.get("satisfied")).isEqualTo(false);
    }

    @Test
    void unknownCourseIsNotSilentlyPassed() {
        when(courseRepository.findByTenantIdAndCode(TENANT, "NOPE")).thenReturn(Optional.empty());

        Map<String, Object> out = service.evaluate(TENANT, TYPE, SUBJECT, List.of("NOPE"));

        assertThat(out.get("satisfied")).isEqualTo(false);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> reqs = (List<Map<String, Object>>) out.get("requirements");
        assertThat(reqs.get(0).get("basis")).isEqualTo("course_not_found");
    }
}
