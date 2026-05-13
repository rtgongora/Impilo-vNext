package zw.gov.mohcc.impilo.learning.fundo;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.learning.persistence.entity.CertificateEntity;
import zw.gov.mohcc.impilo.learning.persistence.entity.CourseEntity;
import zw.gov.mohcc.impilo.learning.persistence.entity.EnrolmentEntity;
import zw.gov.mohcc.impilo.learning.persistence.repository.CertificateRepository;
import zw.gov.mohcc.impilo.learning.persistence.repository.CourseRepository;
import zw.gov.mohcc.impilo.learning.persistence.repository.EnrolmentRepository;

/**
 * Native Fundo certificate metadata issuance (Phase 5C).
 *
 * <p>Conservative issuance: only enrolments with status {@code COMPLETED}
 * may produce certificates. Repeat issuance for the same enrolment returns
 * the existing certificate idempotently (the {@code uq_lrn_certificate_enrolment}
 * unique constraint backs this). This service produces ordinary metadata
 * only — no signed credentials, no PDFs, no regulator-verifiable artefacts.
 */
@Service
public class FundoCertificateService {

    private final CertificateRepository certificateRepository;
    private final EnrolmentRepository enrolmentRepository;
    private final CourseRepository courseRepository;
    private final FundoOutboxAppender outbox;

    public FundoCertificateService(
            CertificateRepository certificateRepository,
            EnrolmentRepository enrolmentRepository,
            CourseRepository courseRepository,
            FundoOutboxAppender outbox) {
        this.certificateRepository = certificateRepository;
        this.enrolmentRepository = enrolmentRepository;
        this.courseRepository = courseRepository;
        this.outbox = outbox;
    }

    @Transactional
    public CertificateIssueResult issueForEnrolment(UUID tenantId, UUID enrolmentId) {
        EnrolmentEntity enrolment = enrolmentRepository.findByTenantIdAndId(tenantId, enrolmentId)
                .orElseThrow(() -> new IllegalArgumentException("enrolment_not_found"));
        if (!"COMPLETED".equals(enrolment.getStatus())) {
            throw new IllegalStateException("enrolment_not_completed");
        }
        Optional<CertificateEntity> existing = certificateRepository.findByEnrolmentId(enrolmentId);
        if (existing.isPresent()) {
            return new CertificateIssueResult(toView(existing.get()), true);
        }
        CourseEntity course = courseRepository.findById(enrolment.getCourseId())
                .orElseThrow(() -> new IllegalArgumentException("course_not_found"));

        CertificateEntity cert = new CertificateEntity();
        cert.setTenantId(tenantId);
        cert.setEnrolmentId(enrolmentId);
        cert.setCourseId(enrolment.getCourseId());
        cert.setSubjectType(enrolment.getSubjectType());
        cert.setSubjectId(enrolment.getSubjectId());
        cert.setCertificateNumber(generateCertificateNumber(course.getCode()));
        cert.setTitle(course.getTitle());
        cert.setIssuedAt(OffsetDateTime.now());
        cert.setStatus("ISSUED");
        cert.setCpdEligible(course.isCpdEligible());
        cert.setCpdPoints(course.isCpdEligible() ? course.getCpdPoints() : null);
        certificateRepository.save(cert);

        outbox.append("FundoCertificate", cert.getId().toString(),
                FundoNativeEventTypes.CERTIFICATE_ISSUED,
                Map.of(
                        "tenantId", tenantId.toString(),
                        "enrolmentId", enrolmentId.toString(),
                        "courseId", enrolment.getCourseId().toString(),
                        "subjectType", enrolment.getSubjectType(),
                        "subjectId", enrolment.getSubjectId(),
                        "certificateNumber", cert.getCertificateNumber(),
                        "cpdEligible", cert.isCpdEligible()));

        return new CertificateIssueResult(toView(cert), false);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listForSubject(UUID tenantId, String subjectType, String subjectId) {
        return certificateRepository.findByTenantIdAndSubjectTypeAndSubjectId(tenantId, subjectType, subjectId)
                .stream().map(FundoCertificateService::toView).toList();
    }

    @Transactional(readOnly = true)
    public Optional<Map<String, Object>> get(UUID tenantId, UUID certificateId) {
        return certificateRepository.findByTenantIdAndId(tenantId, certificateId).map(FundoCertificateService::toView);
    }

    public static Map<String, Object> toView(CertificateEntity c) {
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("id", c.getId().toString());
        v.put("enrolmentId", c.getEnrolmentId().toString());
        v.put("courseId", c.getCourseId().toString());
        v.put("subjectType", c.getSubjectType());
        v.put("subjectId", c.getSubjectId());
        v.put("certificateNumber", c.getCertificateNumber());
        v.put("title", c.getTitle());
        v.put("issuedAt", c.getIssuedAt() == null ? null : c.getIssuedAt().toString());
        v.put("validUntil", c.getValidUntil() == null ? null : c.getValidUntil().toString());
        v.put("status", c.getStatus());
        v.put("cpdEligible", c.isCpdEligible());
        v.put("cpdPoints", c.getCpdPoints());
        return v;
    }

    private static String generateCertificateNumber(String courseCode) {
        String prefix = (courseCode == null ? "FUNDO" : courseCode).replaceAll("[^A-Za-z0-9]", "");
        if (prefix.length() > 16) prefix = prefix.substring(0, 16);
        return "FUNDO-" + prefix + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    public record CertificateIssueResult(Map<String, Object> view, boolean idempotent) {}
}
