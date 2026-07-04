package zw.gov.mohcc.impilo.learning.fundo;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.HexFormat;
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
    private final FundoNotificationIntentWriter notifications;

    public FundoCertificateService(
            CertificateRepository certificateRepository,
            EnrolmentRepository enrolmentRepository,
            CourseRepository courseRepository,
            FundoOutboxAppender outbox,
            FundoNotificationIntentWriter notifications) {
        this.certificateRepository = certificateRepository;
        this.enrolmentRepository = enrolmentRepository;
        this.courseRepository = courseRepository;
        this.outbox = outbox;
        this.notifications = notifications;
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
        cert.setVerificationDigest(computeVerificationDigest(
                tenantId,
                enrolmentId,
                enrolment.getCourseId(),
                enrolment.getSubjectType(),
                enrolment.getSubjectId(),
                cert.getCertificateNumber(),
                cert.getIssuedAt()));
        certificateRepository.save(cert);

        // Payload carries the full CPD-candidate shape so a downstream CPD consumer
        // (varapi-service — the CPD ledger authority) can ingest a candidate WITHOUT
        // Fundo duplicating the ledger or awarding regulated points. See
        // docs/policy/fundo-cpd-evidence-egress.md.
        Map<String, Object> issuedPayload = new LinkedHashMap<>();
        issuedPayload.put("tenantId", tenantId.toString());
        issuedPayload.put("certificateId", cert.getId().toString());
        issuedPayload.put("enrolmentId", enrolmentId.toString());
        issuedPayload.put("courseId", enrolment.getCourseId().toString());
        issuedPayload.put("subjectType", enrolment.getSubjectType());
        issuedPayload.put("subjectId", enrolment.getSubjectId());
        issuedPayload.put("certificateNumber", cert.getCertificateNumber());
        issuedPayload.put("title", cert.getTitle());
        issuedPayload.put("issuedAt", cert.getIssuedAt() == null ? null : cert.getIssuedAt().toString());
        issuedPayload.put("validUntil", cert.getValidUntil() == null ? null : cert.getValidUntil().toString());
        issuedPayload.put("cpdEligible", cert.isCpdEligible());
        issuedPayload.put("suggestedCpdPoints", cert.getCpdPoints());
        issuedPayload.put("verificationDigest", cert.getVerificationDigest());
        outbox.append("FundoCertificate", cert.getId().toString(),
                FundoNativeEventTypes.CERTIFICATE_ISSUED, issuedPayload);

        notifications.record(
                tenantId, cert.getSubjectType(), cert.getSubjectId(),
                "learning.certificate.issued",
                "Certificate issued: " + cert.getTitle(),
                "Your certificate \"" + cert.getTitle() + "\" (" + cert.getCertificateNumber()
                        + ") has been issued.",
                "IN_APP", cert.getValidUntil(), issuedPayload);

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
        v.put("verificationDigest", c.getVerificationDigest());
        return v;
    }

    static String computeVerificationDigest(
            UUID tenantId,
            UUID enrolmentId,
            UUID courseId,
            String subjectType,
            String subjectId,
            String certificateNumber,
            OffsetDateTime issuedAt) {
        String canonical = String.join("|",
                tenantId.toString(),
                enrolmentId.toString(),
                courseId.toString(),
                subjectType,
                subjectId,
                certificateNumber,
                issuedAt == null ? "" : issuedAt.toString());
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new IllegalStateException("verification_digest_failed", e);
        }
    }

    private static String generateCertificateNumber(String courseCode) {
        String prefix = (courseCode == null ? "FUNDO" : courseCode).replaceAll("[^A-Za-z0-9]", "");
        if (prefix.length() > 16) prefix = prefix.substring(0, 16);
        return "FUNDO-" + prefix + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    public record CertificateIssueResult(Map<String, Object> view, boolean idempotent) {}
}
