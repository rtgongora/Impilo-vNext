package zw.gov.mohcc.impilo.learning.fundo;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.learning.persistence.entity.CertificateEntity;
import zw.gov.mohcc.impilo.learning.persistence.entity.CourseEntity;
import zw.gov.mohcc.impilo.learning.persistence.repository.CertificateRepository;
import zw.gov.mohcc.impilo.learning.persistence.repository.CourseRepository;
import zw.gov.mohcc.impilo.learning.persistence.repository.EnrolmentRepository;

/**
 * Evaluates whether a learner has satisfied a set of required courses — the
 * read-model behind Tshepo training-gated access (role/workspace enablement that
 * depends on completed learning).
 *
 * <p>Boundary discipline: this service is the <em>data source</em> for the policy
 * decision, not the decision itself. Tshepo {@code PolicyEngine} / OPA rego owns the
 * gate; learning-service only answers "is requirement R satisfied for subject S?".
 * A requirement is satisfied when the subject has a COMPLETED enrolment for the
 * course <em>and</em> (when the course issues certificates) a non-expired certificate.
 */
@Service
public class FundoTrainingGateService {

    private static final List<String> COMPLETED = List.of("COMPLETED");

    private final CourseRepository courseRepository;
    private final EnrolmentRepository enrolmentRepository;
    private final CertificateRepository certificateRepository;

    public FundoTrainingGateService(
            CourseRepository courseRepository,
            EnrolmentRepository enrolmentRepository,
            CertificateRepository certificateRepository) {
        this.courseRepository = courseRepository;
        this.enrolmentRepository = enrolmentRepository;
        this.certificateRepository = certificateRepository;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> evaluate(
            UUID tenantId, String subjectType, String subjectId, List<String> courseCodes) {
        OffsetDateTime now = OffsetDateTime.now();
        List<Map<String, Object>> requirements = new ArrayList<>();
        List<String> outstanding = new ArrayList<>();
        boolean allSatisfied = true;

        for (String rawCode : courseCodes) {
            String code = rawCode == null ? "" : rawCode.trim();
            Map<String, Object> req = new LinkedHashMap<>();
            req.put("courseCode", code);
            if (code.isEmpty()) {
                continue;
            }
            Optional<CourseEntity> course = courseRepository.findByTenantIdAndCode(tenantId, code);
            if (course.isEmpty()) {
                // Unknown course: cannot be satisfied. Surfaced honestly, not silently passed.
                req.put("courseFound", false);
                req.put("satisfied", false);
                req.put("basis", "course_not_found");
                requirements.add(req);
                outstanding.add(code);
                allSatisfied = false;
                continue;
            }
            CourseEntity c = course.get();
            req.put("courseFound", true);
            req.put("courseId", c.getId().toString());
            req.put("courseTitle", c.getTitle());

            boolean completed = enrolmentRepository
                    .findFirstByTenantIdAndSubjectTypeAndSubjectIdAndCourseIdAndStatusIn(
                            tenantId, subjectType, subjectId, c.getId(), COMPLETED)
                    .isPresent();
            boolean certValid = hasValidCertificate(tenantId, subjectType, subjectId, c.getId(), now);
            // If the course issues no certificate, completion alone satisfies; if a
            // certificate exists it must still be valid (expiry revokes satisfaction).
            boolean satisfied = completed && certValid;
            req.put("completed", completed);
            req.put("certificateValid", certValid);
            req.put("satisfied", satisfied);
            req.put("basis", satisfied
                    ? "completed_enrolment"
                    : (completed ? "certificate_expired_or_missing" : "not_completed"));
            requirements.add(req);
            if (!satisfied) {
                outstanding.add(code);
                allSatisfied = false;
            }
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("subjectType", subjectType);
        out.put("subjectId", subjectId);
        out.put("tenantId", tenantId.toString());
        out.put("satisfied", allSatisfied);
        out.put("requirements", requirements);
        out.put("outstanding", outstanding);
        out.put("evaluatedAt", now.toString());
        out.put("decisionAuthority", "tshepo-authz-service");
        return out;
    }

    /**
     * True when there is no certificate for the course (completion alone suffices)
     * or at least one issued, non-expired certificate exists for the subject+course.
     */
    private boolean hasValidCertificate(
            UUID tenantId, String subjectType, String subjectId, UUID courseId, OffsetDateTime now) {
        List<CertificateEntity> certs = certificateRepository
                .findByTenantIdAndSubjectTypeAndSubjectId(tenantId, subjectType, subjectId);
        List<CertificateEntity> forCourse = certs.stream()
                .filter(c -> courseId.equals(c.getCourseId()))
                .toList();
        if (forCourse.isEmpty()) {
            return true; // course does not gate on a certificate
        }
        return forCourse.stream().anyMatch(c ->
                !"REVOKED".equalsIgnoreCase(c.getStatus())
                        && !"EXPIRED".equalsIgnoreCase(c.getStatus())
                        && (c.getValidUntil() == null || c.getValidUntil().isAfter(now)));
    }
}
