package zw.gov.mohcc.impilo.learning.fundo;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.learning.persistence.entity.CourseEntity;
import zw.gov.mohcc.impilo.learning.persistence.entity.EnrolmentEntity;
import zw.gov.mohcc.impilo.learning.persistence.repository.CourseRepository;
import zw.gov.mohcc.impilo.learning.persistence.repository.EnrolmentRepository;

/**
 * Native enrolment lifecycle (Phase 5C). Enforces single-active-enrolment
 * per (tenant, subject, course) at the application layer (matching the
 * Postgres partial unique index from V006 for H2 test compatibility) and
 * emits {@code impilo.learning.enrolment.{created,cancelled}.v1} events.
 */
@Service
public class FundoEnrolmentService {

    private static final List<String> ACTIVE_STATUSES = List.of("ENROLLED", "IN_PROGRESS");

    private final EnrolmentRepository enrolmentRepository;
    private final CourseRepository courseRepository;
    private final FundoOutboxAppender outbox;

    public FundoEnrolmentService(
            EnrolmentRepository enrolmentRepository,
            CourseRepository courseRepository,
            FundoOutboxAppender outbox) {
        this.enrolmentRepository = enrolmentRepository;
        this.courseRepository = courseRepository;
        this.outbox = outbox;
    }

    @Transactional
    public Map<String, Object> create(EnrolmentRequest req) {
        CourseEntity course = courseRepository.findById(req.courseId())
                .filter(c -> c.getTenantId().equals(req.tenantId()))
                .orElseThrow(() -> new IllegalArgumentException("course_not_found"));

        Optional<EnrolmentEntity> existingActive = enrolmentRepository
                .findFirstByTenantIdAndSubjectTypeAndSubjectIdAndCourseIdAndStatusIn(
                        req.tenantId(), req.subjectType(), req.subjectId(), req.courseId(), ACTIVE_STATUSES);
        if (existingActive.isPresent()) {
            Map<String, Object> v = toView(existingActive.get());
            v.put("status", existingActive.get().getStatus());
            v.put("idempotent", true);
            return v;
        }

        EnrolmentEntity row = new EnrolmentEntity();
        row.setTenantId(req.tenantId());
        row.setSubjectType(req.subjectType());
        row.setSubjectId(req.subjectId());
        row.setCourseId(req.courseId());
        row.setPathwayId(req.pathwayId());
        row.setEnrolmentType(req.enrolmentType() != null ? req.enrolmentType() : "SELF");
        row.setStatus("ENROLLED");
        row.setAssignedBy(req.assignedBy());
        OffsetDateTime now = OffsetDateTime.now();
        if (req.enrolmentType() != null && !"SELF".equals(req.enrolmentType())) {
            row.setAssignedAt(now);
        }
        row.setDueAt(resolveDueAt(req.dueAt(), course, now));
        enrolmentRepository.save(row);

        outbox.append("FundoEnrolment", row.getId().toString(),
                FundoNativeEventTypes.ENROLMENT_CREATED,
                Map.of(
                        "tenantId", req.tenantId().toString(),
                        "subjectType", req.subjectType(),
                        "subjectId", req.subjectId(),
                        "courseId", req.courseId().toString(),
                        "enrolmentType", row.getEnrolmentType(),
                        "courseCode", course.getCode()));

        Map<String, Object> v = toView(row);
        v.put("idempotent", false);
        return v;
    }

    @Transactional(readOnly = true)
    public Optional<Map<String, Object>> get(UUID tenantId, UUID enrolmentId) {
        return enrolmentRepository.findByTenantIdAndId(tenantId, enrolmentId).map(FundoEnrolmentService::toView);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listForSubject(
            UUID tenantId, String subjectType, String subjectId, int limit) {
        int cap = Math.min(Math.max(limit, 1), 100);
        return enrolmentRepository
                .findByTenantIdAndSubjectTypeAndSubjectIdOrderByCreatedAtDesc(
                        tenantId, subjectType, subjectId, PageRequest.of(0, cap))
                .stream().map(FundoEnrolmentService::toView).toList();
    }

    @Transactional
    public Optional<Map<String, Object>> cancel(UUID tenantId, UUID enrolmentId, String reason) {
        Optional<EnrolmentEntity> row = enrolmentRepository.findByTenantIdAndId(tenantId, enrolmentId);
        if (row.isEmpty()) return Optional.empty();
        EnrolmentEntity e = row.get();
        if ("COMPLETED".equals(e.getStatus()) || "CANCELLED".equals(e.getStatus())) {
            return Optional.of(toView(e));
        }
        e.setStatus("CANCELLED");
        enrolmentRepository.save(e);
        outbox.append("FundoEnrolment", e.getId().toString(),
                FundoNativeEventTypes.ENROLMENT_CANCELLED,
                Map.of(
                        "tenantId", e.getTenantId().toString(),
                        "subjectType", e.getSubjectType(),
                        "subjectId", e.getSubjectId(),
                        "courseId", e.getCourseId().toString(),
                        "reason", reason == null ? "" : reason));
        return Optional.of(toView(e));
    }

    @Transactional
    public Optional<Map<String, Object>> start(UUID tenantId, UUID enrolmentId) {
        Optional<EnrolmentEntity> row = enrolmentRepository.findByTenantIdAndId(tenantId, enrolmentId);
        if (row.isEmpty()) return Optional.empty();
        EnrolmentEntity e = row.get();
        if ("CANCELLED".equals(e.getStatus()) || "EXPIRED".equals(e.getStatus())) {
            return Optional.of(toView(e));
        }
        if ("ENROLLED".equals(e.getStatus())) {
            e.setStatus("IN_PROGRESS");
            enrolmentRepository.save(e);
            outbox.append(
                    "FundoEnrolment",
                    e.getId().toString(),
                    FundoNativeEventTypes.PROGRESS_STARTED,
                    Map.of(
                            "tenantId", e.getTenantId().toString(),
                            "subjectType", e.getSubjectType(),
                            "subjectId", e.getSubjectId(),
                            "courseId", e.getCourseId().toString(),
                            "enrolmentId", e.getId().toString(),
                            "startedVia", "enrolment_start"));
        }
        return Optional.of(toView(e));
    }

    public static Map<String, Object> toView(EnrolmentEntity e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", e.getId().toString());
        m.put("subjectType", e.getSubjectType());
        m.put("subjectId", e.getSubjectId());
        m.put("courseId", e.getCourseId().toString());
        m.put("pathwayId", e.getPathwayId() == null ? null : e.getPathwayId().toString());
        m.put("enrolmentType", e.getEnrolmentType());
        m.put("status", e.getStatus());
        m.put("assignedBy", e.getAssignedBy());
        m.put("assignedAt", e.getAssignedAt() == null ? null : e.getAssignedAt().toString());
        m.put("dueAt", e.getDueAt() == null ? null : e.getDueAt().toString());
        m.put("completedAt", e.getCompletedAt() == null ? null : e.getCompletedAt().toString());
        m.put("createdAt", e.getCreatedAt() == null ? null : e.getCreatedAt().toString());
        return m;
    }

    private static OffsetDateTime resolveDueAt(OffsetDateTime requestedDueAt, CourseEntity course, OffsetDateTime enrolledAt) {
        if (requestedDueAt != null) return requestedDueAt;
        if ("FIXED".equals(course.getDueDateType())) {
            return course.getDueDate();
        }
        if ("RELATIVE".equals(course.getDueDateType()) && course.getDueDateDaysFromEnrollment() != null) {
            return enrolledAt.plusDays(course.getDueDateDaysFromEnrollment());
        }
        return null;
    }

    public record EnrolmentRequest(
            UUID tenantId,
            String subjectType,
            String subjectId,
            UUID courseId,
            UUID pathwayId,
            String enrolmentType,
            String assignedBy,
            OffsetDateTime dueAt) {}
}
