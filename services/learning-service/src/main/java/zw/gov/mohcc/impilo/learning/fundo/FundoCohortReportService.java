package zw.gov.mohcc.impilo.learning.fundo;

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
import zw.gov.mohcc.impilo.learning.persistence.entity.EnrolmentEntity;
import zw.gov.mohcc.impilo.learning.persistence.entity.FundoPathwayItemEntity;
import zw.gov.mohcc.impilo.learning.persistence.repository.CertificateRepository;
import zw.gov.mohcc.impilo.learning.persistence.repository.CourseRepository;
import zw.gov.mohcc.impilo.learning.persistence.repository.EnrolmentRepository;
import zw.gov.mohcc.impilo.learning.persistence.repository.FundoPathwayItemRepository;
import zw.gov.mohcc.impilo.learning.persistence.repository.FundoPathwayRepository;

/**
 * Phase 6C — trainer / supervisor cohort completion report.
 *
 * <p>Given a tenant and an optional pathway filter, aggregates per-course
 * enrolment, in-progress, completed, cancelled, and certificate counts.
 * Read-only and standalone-capable. The report is intentionally derived
 * from the native Phase 5 enrolment + certificate tables — it does
 * <strong>not</strong> consult Moodle and does not award CPD points.</p>
 *
 * <p>If the cohort needs to be restricted to a specific cadre / role /
 * facility-level, callers should rely on the pathway filter — pathway
 * targeting is the canonical place where cadre / role / facility-level
 * targeting is recorded in Phase 5A's domain model.</p>
 */
@Service
public class FundoCohortReportService {

    private final CourseRepository courseRepository;
    private final EnrolmentRepository enrolmentRepository;
    private final CertificateRepository certificateRepository;
    private final FundoPathwayRepository pathwayRepository;
    private final FundoPathwayItemRepository pathwayItemRepository;

    public FundoCohortReportService(
            CourseRepository courseRepository,
            EnrolmentRepository enrolmentRepository,
            CertificateRepository certificateRepository,
            FundoPathwayRepository pathwayRepository,
            FundoPathwayItemRepository pathwayItemRepository) {
        this.courseRepository = courseRepository;
        this.enrolmentRepository = enrolmentRepository;
        this.certificateRepository = certificateRepository;
        this.pathwayRepository = pathwayRepository;
        this.pathwayItemRepository = pathwayItemRepository;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> cohortCompletions(UUID tenantId, CohortReportFilter filter) {
        List<UUID> targetCourseIds = resolveCourseIds(tenantId, filter);

        List<Map<String, Object>> rows = new ArrayList<>();
        int totalEnrolled = 0;
        int totalInProgress = 0;
        int totalCompleted = 0;
        int totalCancelled = 0;
        int totalCertificatesIssued = 0;

        for (UUID courseId : targetCourseIds) {
            Optional<CourseEntity> course = courseRepository.findById(courseId)
                    .filter(c -> tenantId == null || c.getTenantId().equals(tenantId));
            if (course.isEmpty()) continue;
            UUID rowTenantId = course.get().getTenantId();

            List<EnrolmentEntity> enrolments = enrolmentRepository.findByTenantIdAndCourseId(rowTenantId, courseId);
            int enrolled = 0;
            int inProgress = 0;
            int completed = 0;
            int cancelled = 0;
            for (EnrolmentEntity e : enrolments) {
                switch (e.getStatus()) {
                    case "ENROLLED" -> enrolled++;
                    case "IN_PROGRESS" -> inProgress++;
                    case "COMPLETED" -> completed++;
                    case "CANCELLED" -> cancelled++;
                    default -> { /* EXPIRED or future states ignored */ }
                }
            }
            List<CertificateEntity> certs =
                    certificateRepository.findByTenantIdAndCourseId(rowTenantId, courseId);
            long issuedCount = certs.stream().filter(c -> "ISSUED".equals(c.getStatus())).count();

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("tenantId", rowTenantId.toString());
            row.put("courseId", courseId.toString());
            row.put("courseCode", course.get().getCode());
            row.put("courseTitle", course.get().getTitle());
            row.put("category", course.get().getCategory());
            row.put("cpdEligible", course.get().isCpdEligible());
            row.put("mandatory", course.get().isMandatory());
            row.put("totalEnrolments", enrolments.size());
            row.put("enrolledCount", enrolled);
            row.put("inProgressCount", inProgress);
            row.put("completedCount", completed);
            row.put("cancelledCount", cancelled);
            row.put("certificatesIssued", (int) issuedCount);
            int denom = enrolments.size() - cancelled;
            double completionRate = denom > 0 ? (completed * 100.0) / denom : 0.0;
            row.put("completionRatePercent", round(completionRate));
            rows.add(row);

            totalEnrolled += enrolled;
            totalInProgress += inProgress;
            totalCompleted += completed;
            totalCancelled += cancelled;
            totalCertificatesIssued += (int) issuedCount;
        }

        Map<String, Object> totals = new LinkedHashMap<>();
        totals.put("courses", rows.size());
        totals.put("enrolledCount", totalEnrolled);
        totals.put("inProgressCount", totalInProgress);
        totals.put("completedCount", totalCompleted);
        totals.put("cancelledCount", totalCancelled);
        totals.put("certificatesIssued", totalCertificatesIssued);

        Map<String, Object> data = new LinkedHashMap<>();
        Map<String, Object> filterEcho = new LinkedHashMap<>();
        filterEcho.put("pathwayId", filter.pathwayId() == null ? null : filter.pathwayId().toString());
        filterEcho.put("courseId", filter.courseId() == null ? null : filter.courseId().toString());
        data.put("filter", filterEcho);
        data.put("totals", totals);
        data.put("items", rows);
        return data;
    }

    private List<UUID> resolveCourseIds(UUID tenantId, CohortReportFilter filter) {
        if (filter.courseId() != null) {
            return courseRepository.findById(filter.courseId())
                    .filter(c -> tenantId == null || c.getTenantId().equals(tenantId))
                    .map(c -> List.of(c.getId()))
                    .orElseGet(List::of);
        }
        if (filter.pathwayId() != null) {
            if (tenantId == null) return List.of();
            return pathwayRepository.findByTenantIdAndId(tenantId, filter.pathwayId())
                    .map(p -> pathwayItemRepository.findByPathwayIdOrderBySequenceNoAsc(p.getId()).stream()
                            .map(FundoPathwayItemEntity::getCourseId)
                            .toList())
                    .orElseGet(List::of);
        }
        // No filter — operate on the published catalogue.
        return (tenantId == null
                        ? courseRepository.findByStatus("PUBLISHED", org.springframework.data.domain.PageRequest.of(0, 500))
                        : courseRepository.findByTenantIdAndStatus(
                                tenantId, "PUBLISHED", org.springframework.data.domain.PageRequest.of(0, 50)))
                .stream()
                .map(CourseEntity::getId)
                .toList();
    }

    private static double round(double v) {
        return Math.round(v * 10.0) / 10.0;
    }

    /** Cohort filter inputs (both nullable). */
    public record CohortReportFilter(UUID pathwayId, UUID courseId) {}
}
