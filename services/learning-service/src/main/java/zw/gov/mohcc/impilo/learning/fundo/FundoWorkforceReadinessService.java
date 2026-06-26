package zw.gov.mohcc.impilo.learning.fundo;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.learning.persistence.entity.CertificateEntity;
import zw.gov.mohcc.impilo.learning.persistence.entity.CourseEntity;
import zw.gov.mohcc.impilo.learning.persistence.entity.EnrolmentEntity;
import zw.gov.mohcc.impilo.learning.persistence.repository.CertificateRepository;
import zw.gov.mohcc.impilo.learning.persistence.repository.CourseRepository;
import zw.gov.mohcc.impilo.learning.persistence.repository.EnrolmentRepository;

/**
 * Computes a compact, Fundo-owned training-evidence / workforce-readiness summary
 * for a single learner (provider worker). This fulfils the cross-service contract
 * already expected by vashandi-workforce-service
 * ({@code FundoIntegrationClient#fetchTrainingEvidence}), which polls
 * {@code GET /v1/internal/fundo/learners/{providerWorkerId}/cpd-summary}.
 *
 * <p>Boundary discipline: Fundo is the source of truth for course completion and
 * certificate issuance. It emits a <em>readiness signal</em> (mandatory learning
 * satisfied / at-risk / overdue, certificate validity). Vashandi remains the
 * source of truth for workforce readiness decisions and Varapi remains the CPD
 * ledger authority — this endpoint returns CPD-eligible completions as
 * <em>candidates</em> only and never awards regulated CPD points.
 */
@Service
public class FundoWorkforceReadinessService {

    /** Certificates within this window of {@code valid_until} are flagged "expiring soon". */
    private static final long EXPIRING_SOON_DAYS = 30L;

    private final EnrolmentRepository enrolmentRepository;
    private final CourseRepository courseRepository;
    private final CertificateRepository certificateRepository;

    public FundoWorkforceReadinessService(
            EnrolmentRepository enrolmentRepository,
            CourseRepository courseRepository,
            CertificateRepository certificateRepository) {
        this.enrolmentRepository = enrolmentRepository;
        this.courseRepository = courseRepository;
        this.certificateRepository = certificateRepository;
    }

    /**
     * Build the CPD / training-evidence summary for a provider worker. The
     * {@code providerWorkerId} is treated as the Fundo {@code subjectId} under the
     * {@code PROVIDER} subject type — the same key learners enrol under.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> cpdSummary(UUID tenantId, String subjectType, String providerWorkerId) {
        OffsetDateTime now = OffsetDateTime.now();

        List<EnrolmentEntity> enrolments = enrolmentRepository
                .findByTenantIdAndSubjectTypeAndSubjectIdOrderByCreatedAtDesc(
                        tenantId, subjectType, providerWorkerId, PageRequest.of(0, 500));

        Set<UUID> courseIds = enrolments.stream()
                .map(EnrolmentEntity::getCourseId)
                .collect(Collectors.toSet());
        Map<UUID, CourseEntity> courses = courseRepository.findAllById(courseIds).stream()
                .filter(c -> tenantId.equals(c.getTenantId()))
                .collect(Collectors.toMap(CourseEntity::getId, c -> c));

        int completedCount = 0;
        int inProgressCount = 0;
        List<Map<String, Object>> outstandingRequired = new ArrayList<>();
        List<Map<String, Object>> overdueRequired = new ArrayList<>();
        int requiredTotal = 0;
        int requiredSatisfied = 0;

        for (EnrolmentEntity e : enrolments) {
            String status = e.getStatus();
            if ("CANCELLED".equals(status)) {
                continue;
            }
            CourseEntity course = courses.get(e.getCourseId());
            boolean required = isRequired(e, course);
            boolean completed = "COMPLETED".equals(status);
            if (completed) {
                completedCount++;
            } else if ("IN_PROGRESS".equals(status) || "ENROLLED".equals(status)) {
                inProgressCount++;
            }
            if (required) {
                requiredTotal++;
                if (completed) {
                    requiredSatisfied++;
                } else {
                    Map<String, Object> row = requiredRow(e, course);
                    outstandingRequired.add(row);
                    if (e.getDueAt() != null && e.getDueAt().isBefore(now)) {
                        overdueRequired.add(row);
                    }
                }
            }
        }

        List<CertificateEntity> certs = certificateRepository
                .findByTenantIdAndSubjectTypeAndSubjectId(tenantId, subjectType, providerWorkerId);
        List<Map<String, Object>> certViews = new ArrayList<>();
        List<Map<String, Object>> cpdCandidates = new ArrayList<>();
        int activeCertificates = 0;
        int expiredCertificates = 0;
        int expiringSoon = 0;
        OffsetDateTime soonThreshold = now.plusDays(EXPIRING_SOON_DAYS);
        for (CertificateEntity c : certs) {
            boolean revoked = "REVOKED".equalsIgnoreCase(c.getStatus());
            boolean expired = c.getValidUntil() != null && c.getValidUntil().isBefore(now);
            boolean active = !revoked && !expired;
            if (active) {
                activeCertificates++;
                if (c.getValidUntil() != null && c.getValidUntil().isBefore(soonThreshold)) {
                    expiringSoon++;
                }
            } else if (expired) {
                expiredCertificates++;
            }
            Map<String, Object> cv = new LinkedHashMap<>();
            cv.put("certificateId", c.getId().toString());
            cv.put("certificateNumber", c.getCertificateNumber());
            cv.put("courseId", c.getCourseId() == null ? null : c.getCourseId().toString());
            cv.put("title", c.getTitle());
            cv.put("issuedAt", str(c.getIssuedAt()));
            cv.put("validUntil", str(c.getValidUntil()));
            cv.put("status", active ? "ACTIVE" : (expired ? "EXPIRED" : c.getStatus()));
            cv.put("verificationDigest", c.getVerificationDigest());
            certViews.add(cv);
            if (active && c.isCpdEligible()) {
                Map<String, Object> cand = new LinkedHashMap<>();
                cand.put("certificateId", c.getId().toString());
                cand.put("courseId", c.getCourseId() == null ? null : c.getCourseId().toString());
                cand.put("title", c.getTitle());
                cand.put("issuedAt", str(c.getIssuedAt()));
                cand.put("suggestedCpdPoints", c.getCpdPoints());
                cpdCandidates.add(cand);
            }
        }

        String readiness = deriveReadiness(requiredTotal, outstandingRequired.size(),
                overdueRequired.size(), expiredCertificates);

        Map<String, Object> required = new LinkedHashMap<>();
        required.put("total", requiredTotal);
        required.put("satisfied", requiredSatisfied);
        required.put("outstanding", outstandingRequired);
        required.put("overdue", overdueRequired);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("providerWorkerId", providerWorkerId);
        out.put("subjectType", subjectType);
        out.put("tenantId", tenantId.toString());
        out.put("trainingReadiness", readiness);
        out.put("completedCourseCount", completedCount);
        out.put("inProgressCount", inProgressCount);
        out.put("requiredLearning", required);
        out.put("activeCertificateCount", activeCertificates);
        out.put("expiredCertificateCount", expiredCertificates);
        out.put("expiringSoonCount", expiringSoon);
        out.put("certificates", certViews);
        // CPD candidates only — Varapi remains the CPD ledger authority.
        out.put("cpdCandidates", cpdCandidates);
        out.put("cpdLedgerAuthority", "varapi-service");
        out.put("generatedAt", str(now));
        return out;
    }

    /** A course is "required" if it is flagged mandatory or was assigned by orchestration. */
    private static boolean isRequired(EnrolmentEntity e, CourseEntity course) {
        if (course != null && course.isMandatory()) {
            return true;
        }
        String type = e.getEnrolmentType();
        return "ASSIGNED".equals(type) || "SYSTEM".equals(type) || "COHORT".equals(type);
    }

    private static Map<String, Object> requiredRow(EnrolmentEntity e, CourseEntity course) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("enrolmentId", e.getId().toString());
        row.put("courseId", e.getCourseId() == null ? null : e.getCourseId().toString());
        row.put("courseCode", course == null ? null : course.getCode());
        row.put("courseTitle", course == null ? null : course.getTitle());
        row.put("status", e.getStatus());
        row.put("enrolmentType", e.getEnrolmentType());
        row.put("dueAt", str(e.getDueAt()));
        return row;
    }

    private static String deriveReadiness(int requiredTotal, int outstanding, int overdue, int expiredCerts) {
        if (overdue > 0 || expiredCerts > 0) {
            return "NOT_READY";
        }
        if (outstanding > 0) {
            return "AT_RISK";
        }
        if (requiredTotal > 0) {
            return "READY";
        }
        return "NO_REQUIREMENTS";
    }

    private static String str(OffsetDateTime t) {
        return t == null ? null : t.toString();
    }
}
