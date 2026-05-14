package zw.gov.mohcc.impilo.learning.fundo;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.learning.persistence.entity.CourseLessonEntity;
import zw.gov.mohcc.impilo.learning.persistence.entity.CourseModuleEntity;
import zw.gov.mohcc.impilo.learning.persistence.entity.CourseProgressEntity;
import zw.gov.mohcc.impilo.learning.persistence.entity.EnrolmentEntity;
import zw.gov.mohcc.impilo.learning.persistence.repository.CourseLessonRepository;
import zw.gov.mohcc.impilo.learning.persistence.repository.CourseModuleRepository;
import zw.gov.mohcc.impilo.learning.persistence.repository.CourseProgressRepository;
import zw.gov.mohcc.impilo.learning.persistence.repository.EnrolmentRepository;

/**
 * Native progress recording (Phase 5C). Manages lesson, module and course
 * progress rows against an enrolment, transitions enrolment status when
 * progress hits 100 %, and emits compliant v1.1 outbox events for every
 * progress write.
 */
@Service
public class FundoProgressService {

    private final EnrolmentRepository enrolmentRepository;
    private final CourseProgressRepository progressRepository;
    private final CourseModuleRepository moduleRepository;
    private final CourseLessonRepository lessonRepository;
    private final FundoOutboxAppender outbox;

    public FundoProgressService(
            EnrolmentRepository enrolmentRepository,
            CourseProgressRepository progressRepository,
            CourseModuleRepository moduleRepository,
            CourseLessonRepository lessonRepository,
            FundoOutboxAppender outbox) {
        this.enrolmentRepository = enrolmentRepository;
        this.progressRepository = progressRepository;
        this.moduleRepository = moduleRepository;
        this.lessonRepository = lessonRepository;
        this.outbox = outbox;
    }

    /**
     * Record a lesson-scoped progress update. If lessonId is null the update
     * is treated as a course-aggregate progress update against the enrolment.
     */
    @Transactional
    public Map<String, Object> recordProgress(UUID tenantId, ProgressUpdate update) {
        EnrolmentEntity enrolment = enrolmentRepository
                .findByTenantIdAndId(tenantId, update.enrolmentId())
                .orElseThrow(() -> new IllegalArgumentException("enrolment_not_found"));
        if ("CANCELLED".equals(enrolment.getStatus()) || "EXPIRED".equals(enrolment.getStatus())) {
            throw new IllegalStateException("enrolment_not_active");
        }
        OffsetDateTime now = OffsetDateTime.now();

        CourseProgressEntity row;
        if (update.lessonId() != null) {
            row = progressRepository
                    .findByEnrolmentIdAndLessonId(enrolment.getId(), update.lessonId())
                    .orElseGet(() -> {
                        CourseProgressEntity p = newRow(enrolment, tenantId);
                        CourseLessonEntity lesson = lessonRepository.findById(update.lessonId())
                                .orElseThrow(() -> new IllegalArgumentException("lesson_not_found"));
                        p.setLessonId(update.lessonId());
                        p.setModuleId(lesson.getModuleId());
                        return p;
                    });
        } else if (update.moduleId() != null) {
            row = progressRepository
                    .findByEnrolmentIdAndModuleIdAndLessonIdIsNull(enrolment.getId(), update.moduleId())
                    .orElseGet(() -> {
                        CourseProgressEntity p = newRow(enrolment, tenantId);
                        moduleRepository.findById(update.moduleId())
                                .orElseThrow(() -> new IllegalArgumentException("module_not_found"));
                        p.setModuleId(update.moduleId());
                        return p;
                    });
        } else {
            row = progressRepository
                    .findByEnrolmentIdAndModuleIdIsNullAndLessonIdIsNull(enrolment.getId())
                    .orElseGet(() -> newRow(enrolment, tenantId));
        }

        boolean wasStarted = row.getStartedAt() != null;
        if (!wasStarted) row.setStartedAt(now);
        row.setLastAccessedAt(now);
        if (update.score() != null) row.setScore(update.score());
        int newPercent = Math.max(0, Math.min(100, update.progressPercent()));
        row.setProgressPercent(newPercent);
        String previousStatus = row.getStatus();
        if (newPercent >= 100) {
            row.setStatus("COMPLETED");
            if (row.getCompletedAt() == null) row.setCompletedAt(now);
        } else if (newPercent > 0) {
            row.setStatus("STARTED");
        }
        progressRepository.save(row);

        if (!"STARTED".equals(previousStatus) && "STARTED".equals(row.getStatus())) {
            emit(FundoNativeEventTypes.PROGRESS_STARTED, row, enrolment, "STARTED");
        }
        if (!"COMPLETED".equals(previousStatus) && "COMPLETED".equals(row.getStatus())) {
            emit(FundoNativeEventTypes.PROGRESS_COMPLETED, row, enrolment, "COMPLETED");
        }

        if (update.lessonId() != null) {
            reconcileAggregateCompletion(tenantId, enrolment, now);
        }

        boolean enrolmentChanged = false;
        if ("STARTED".equals(row.getStatus()) && "ENROLLED".equals(enrolment.getStatus())) {
            enrolment.setStatus("IN_PROGRESS");
            enrolmentChanged = true;
        }
        if (update.lessonId() == null && update.moduleId() == null
                && "COMPLETED".equals(row.getStatus())
                && !"COMPLETED".equals(enrolment.getStatus())) {
            enrolment.setStatus("COMPLETED");
            enrolment.setCompletedAt(now);
            enrolmentChanged = true;
            outbox.append("FundoEnrolment", enrolment.getId().toString(),
                    FundoNativeEventTypes.COURSE_COMPLETED,
                    Map.of(
                            "tenantId", enrolment.getTenantId().toString(),
                            "subjectType", enrolment.getSubjectType(),
                            "subjectId", enrolment.getSubjectId(),
                            "courseId", enrolment.getCourseId().toString(),
                            "enrolmentId", enrolment.getId().toString()));
        }
        if (enrolmentChanged) enrolmentRepository.save(enrolment);

        return toView(row);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getByEnrolment(UUID tenantId, UUID enrolmentId) {
        Optional<EnrolmentEntity> enrolment = enrolmentRepository.findByTenantIdAndId(tenantId, enrolmentId);
        if (enrolment.isEmpty()) return List.of();
        return progressRepository.findByEnrolmentIdOrderByCreatedAtAsc(enrolmentId)
                .stream().map(FundoProgressService::toView).toList();
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listForSubject(UUID tenantId, String subjectType, String subjectId) {
        return progressRepository.findByTenantIdAndSubjectTypeAndSubjectId(tenantId, subjectType, subjectId)
                .stream().map(FundoProgressService::toView).toList();
    }

    @Transactional
    public Map<String, Object> openLesson(UUID tenantId, UUID lessonId, UUID enrolmentId) {
        if (enrolmentId == null) {
            throw new IllegalArgumentException("enrolment_required");
        }
        EnrolmentEntity enrolment = enrolmentRepository
                .findByTenantIdAndId(tenantId, enrolmentId)
                .orElseThrow(() -> new IllegalArgumentException("enrolment_not_found"));
        if ("CANCELLED".equals(enrolment.getStatus()) || "EXPIRED".equals(enrolment.getStatus())) {
            throw new IllegalStateException("enrolment_not_active");
        }
        CourseLessonEntity lesson = lessonRepository.findById(lessonId)
                .orElseThrow(() -> new IllegalArgumentException("lesson_not_found"));

        CourseProgressEntity row = progressRepository
                .findByEnrolmentIdAndLessonId(enrolment.getId(), lessonId)
                .orElseGet(() -> {
                    CourseProgressEntity p = newRow(enrolment, tenantId);
                    p.setLessonId(lessonId);
                    p.setModuleId(lesson.getModuleId());
                    p.setProgressPercent(0);
                    return p;
                });
        OffsetDateTime now = OffsetDateTime.now();
        if (row.getStartedAt() == null) {
            row.setStartedAt(now);
            row.setStatus("STARTED");
            emit(FundoNativeEventTypes.PROGRESS_STARTED, row, enrolment, "STARTED");
        }
        row.setLastAccessedAt(now);
        progressRepository.save(row);

        if ("ENROLLED".equals(enrolment.getStatus())) {
            enrolment.setStatus("IN_PROGRESS");
            enrolmentRepository.save(enrolment);
        }

        outbox.append(
                "FundoLesson",
                lessonId.toString(),
                FundoNativeEventTypes.LESSON_OPENED,
                Map.of(
                        "tenantId", tenantId.toString(),
                        "courseId", enrolment.getCourseId().toString(),
                        "enrolmentId", enrolment.getId().toString(),
                        "lessonId", lessonId.toString(),
                        "moduleId", lesson.getModuleId().toString(),
                        "subjectType", enrolment.getSubjectType(),
                        "subjectId", enrolment.getSubjectId()));
        reconcileAggregateCompletion(tenantId, enrolment, now);
        return toView(row);
    }

    private void reconcileAggregateCompletion(UUID tenantId, EnrolmentEntity enrolment, OffsetDateTime now) {
        List<CourseModuleEntity> modules = moduleRepository.findByCourseIdOrderBySequenceNoAsc(enrolment.getCourseId());
        if (modules.isEmpty()) return;
        List<UUID> moduleIds = modules.stream().map(CourseModuleEntity::getId).toList();
        List<CourseLessonEntity> requiredLessons = lessonRepository
                .findByModuleIdInOrderBySequenceNoAsc(moduleIds)
                .stream()
                .filter(CourseLessonEntity::isRequired)
                .collect(Collectors.toList());
        if (requiredLessons.isEmpty()) return;

        int completed = 0;
        for (CourseLessonEntity lesson : requiredLessons) {
            Optional<CourseProgressEntity> row = progressRepository.findByEnrolmentIdAndLessonId(enrolment.getId(), lesson.getId());
            if (row.isPresent() && "COMPLETED".equals(row.get().getStatus())) {
                completed++;
            }
        }
        int percent = (int) Math.round((completed * 100.0) / requiredLessons.size());
        CourseProgressEntity aggregate = progressRepository
                .findByEnrolmentIdAndModuleIdIsNullAndLessonIdIsNull(enrolment.getId())
                .orElseGet(() -> newRow(enrolment, tenantId));
        String previousStatus = aggregate.getStatus();
        if (aggregate.getStartedAt() == null && percent > 0) {
            aggregate.setStartedAt(now);
        }
        aggregate.setLastAccessedAt(now);
        aggregate.setProgressPercent(percent);
        if (percent >= 100) {
            aggregate.setStatus("COMPLETED");
            if (aggregate.getCompletedAt() == null) aggregate.setCompletedAt(now);
        } else if (percent > 0) {
            aggregate.setStatus("STARTED");
        }
        progressRepository.save(aggregate);
        if (!"STARTED".equals(previousStatus) && "STARTED".equals(aggregate.getStatus())) {
            emit(FundoNativeEventTypes.PROGRESS_STARTED, aggregate, enrolment, "STARTED");
        }
        if (!"COMPLETED".equals(previousStatus) && "COMPLETED".equals(aggregate.getStatus())) {
            emit(FundoNativeEventTypes.PROGRESS_COMPLETED, aggregate, enrolment, "COMPLETED");
        }
        if ("COMPLETED".equals(aggregate.getStatus()) && !"COMPLETED".equals(enrolment.getStatus())) {
            enrolment.setStatus("COMPLETED");
            enrolment.setCompletedAt(now);
            enrolmentRepository.save(enrolment);
            outbox.append("FundoEnrolment", enrolment.getId().toString(),
                    FundoNativeEventTypes.COURSE_COMPLETED,
                    Map.of(
                            "tenantId", enrolment.getTenantId().toString(),
                            "subjectType", enrolment.getSubjectType(),
                            "subjectId", enrolment.getSubjectId(),
                            "courseId", enrolment.getCourseId().toString(),
                            "enrolmentId", enrolment.getId().toString()));
        }
    }

    private CourseProgressEntity newRow(EnrolmentEntity enrolment, UUID tenantId) {
        CourseProgressEntity p = new CourseProgressEntity();
        p.setTenantId(tenantId);
        p.setEnrolmentId(enrolment.getId());
        p.setCourseId(enrolment.getCourseId());
        p.setSubjectType(enrolment.getSubjectType());
        p.setSubjectId(enrolment.getSubjectId());
        return p;
    }

    private void emit(String eventType, CourseProgressEntity row, EnrolmentEntity e, String status) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("tenantId", row.getTenantId().toString());
        payload.put("enrolmentId", row.getEnrolmentId().toString());
        payload.put("courseId", row.getCourseId().toString());
        payload.put("moduleId", row.getModuleId() == null ? null : row.getModuleId().toString());
        payload.put("lessonId", row.getLessonId() == null ? null : row.getLessonId().toString());
        payload.put("subjectType", e.getSubjectType());
        payload.put("subjectId", e.getSubjectId());
        payload.put("status", status);
        payload.put("progressPercent", row.getProgressPercent());
        outbox.append("FundoProgress", row.getId().toString(), eventType, payload);
    }

    public static Map<String, Object> toView(CourseProgressEntity p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", p.getId().toString());
        m.put("enrolmentId", p.getEnrolmentId().toString());
        m.put("courseId", p.getCourseId().toString());
        m.put("moduleId", p.getModuleId() == null ? null : p.getModuleId().toString());
        m.put("lessonId", p.getLessonId() == null ? null : p.getLessonId().toString());
        m.put("subjectType", p.getSubjectType());
        m.put("subjectId", p.getSubjectId());
        m.put("status", p.getStatus());
        m.put("progressPercent", p.getProgressPercent());
        m.put("score", p.getScore());
        m.put("startedAt", p.getStartedAt() == null ? null : p.getStartedAt().toString());
        m.put("completedAt", p.getCompletedAt() == null ? null : p.getCompletedAt().toString());
        m.put("lastAccessedAt", p.getLastAccessedAt() == null ? null : p.getLastAccessedAt().toString());
        return m;
    }

    public record ProgressUpdate(
            UUID enrolmentId,
            UUID moduleId,
            UUID lessonId,
            int progressPercent,
            Integer score) {}
}
