package zw.gov.mohcc.impilo.learning.fundo;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.learning.persistence.entity.AssessmentAttemptEntity;
import zw.gov.mohcc.impilo.learning.persistence.entity.AssessmentEntity;
import zw.gov.mohcc.impilo.learning.persistence.entity.CourseEntity;
import zw.gov.mohcc.impilo.learning.persistence.entity.EnrolmentEntity;
import zw.gov.mohcc.impilo.learning.persistence.repository.AssessmentAttemptRepository;
import zw.gov.mohcc.impilo.learning.persistence.repository.AssessmentRepository;
import zw.gov.mohcc.impilo.learning.persistence.repository.CourseRepository;
import zw.gov.mohcc.impilo.learning.persistence.repository.EnrolmentRepository;

/** Trainer/supervisor reporting surfaces for native Fundo LMS. */
@Service
public class FundoReportService {

    private final EnrolmentRepository enrolmentRepository;
    private final CourseRepository courseRepository;
    private final AssessmentRepository assessmentRepository;
    private final AssessmentAttemptRepository attemptRepository;
    private final FundoCohortReportService cohortReportService;

    public FundoReportService(
            EnrolmentRepository enrolmentRepository,
            CourseRepository courseRepository,
            AssessmentRepository assessmentRepository,
            AssessmentAttemptRepository attemptRepository,
            FundoCohortReportService cohortReportService) {
        this.enrolmentRepository = enrolmentRepository;
        this.courseRepository = courseRepository;
        this.assessmentRepository = assessmentRepository;
        this.attemptRepository = attemptRepository;
        this.cohortReportService = cohortReportService;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> overview(UUID tenantId) {
        List<CourseEntity> courses = courseRepository.findByTenantIdAndStatus(tenantId, "PUBLISHED", PageRequest.of(0, 200));
        int enrolments = 0;
        int inProgress = 0;
        int completed = 0;
        int overdue = 0;
        OffsetDateTime now = OffsetDateTime.now();
        for (CourseEntity c : courses) {
            List<EnrolmentEntity> courseEnrolments = enrolmentRepository.findByTenantIdAndCourseId(tenantId, c.getId());
            enrolments += courseEnrolments.size();
            for (EnrolmentEntity e : courseEnrolments) {
                if ("IN_PROGRESS".equals(e.getStatus())) inProgress++;
                if ("COMPLETED".equals(e.getStatus())) completed++;
                if (e.getDueAt() != null
                        && e.getDueAt().isBefore(now)
                        && !"COMPLETED".equals(e.getStatus())
                        && !"CANCELLED".equals(e.getStatus())) overdue++;
            }
        }
        return Map.of(
                "publishedCourses", courses.size(),
                "totalEnrolments", enrolments,
                "inProgress", inProgress,
                "completed", completed,
                "overdue", overdue);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> courseCompletions(
            UUID tenantId, UUID courseId, String subjectType, String statusFilter, Integer limit) {
        List<CourseEntity> target = courseId == null
                ? courseRepository.findByTenantIdAndStatus(tenantId, "PUBLISHED", PageRequest.of(0, 200))
                : courseRepository.findById(courseId).filter(c -> tenantId.equals(c.getTenantId())).stream().toList();
        List<Map<String, Object>> items = new ArrayList<>();
        for (CourseEntity c : target) {
            List<EnrolmentEntity> es = enrolmentRepository.findByTenantIdAndCourseId(tenantId, c.getId()).stream()
                    .filter(e -> subjectType == null || subjectType.isBlank()
                            || subjectType.equalsIgnoreCase(e.getSubjectType()))
                    .filter(e -> statusFilter == null || statusFilter.isBlank()
                            || statusFilter.equalsIgnoreCase(e.getStatus()))
                    .toList();
            long completed = es.stream().filter(e -> "COMPLETED".equals(e.getStatus())).count();
            long active = es.stream().filter(e -> "ENROLLED".equals(e.getStatus()) || "IN_PROGRESS".equals(e.getStatus())).count();
            double completionRate = es.isEmpty() ? 0.0 : Math.round((completed * 1000.0 / es.size())) / 10.0;
            items.add(Map.of(
                    "courseId", c.getId().toString(),
                    "courseCode", c.getCode(),
                    "courseTitle", c.getTitle(),
                    "totalEnrolments", es.size(),
                    "activeEnrolments", active,
                    "completed", completed,
                    "completionRatePercent", completionRate));
        }
        return Map.of("items", limit(items, limit));
    }

    @Transactional(readOnly = true)
    public Map<String, Object> overdueLearning(
            UUID tenantId, UUID courseId, String subjectType, String statusFilter, Integer limit) {
        OffsetDateTime now = OffsetDateTime.now();
        List<CourseEntity> courses = courseId == null
                ? courseRepository.findByTenantIdAndStatus(tenantId, "PUBLISHED", PageRequest.of(0, 200))
                : courseRepository.findById(courseId).filter(c -> tenantId.equals(c.getTenantId())).stream().toList();
        List<Map<String, Object>> items = new ArrayList<>();
        for (CourseEntity c : courses) {
            for (EnrolmentEntity e : enrolmentRepository.findByTenantIdAndCourseId(tenantId, c.getId())) {
                if (e.getDueAt() != null
                        && e.getDueAt().isBefore(now)
                        && !"COMPLETED".equals(e.getStatus())
                        && !"CANCELLED".equals(e.getStatus())
                        && (subjectType == null || subjectType.isBlank()
                            || subjectType.equalsIgnoreCase(e.getSubjectType()))
                        && (statusFilter == null || statusFilter.isBlank()
                            || statusFilter.equalsIgnoreCase(e.getStatus()))) {
                    Map<String, Object> row = new LinkedHashMap<>(FundoEnrolmentService.toView(e));
                    row.put("courseCode", c.getCode());
                    row.put("courseTitle", c.getTitle());
                    items.add(row);
                }
            }
        }
        return Map.of("items", limit(items, limit));
    }

    @Transactional(readOnly = true)
    public Map<String, Object> assessmentPerformance(
            UUID tenantId, UUID courseId, String subjectType, Integer limit) {
        List<CourseEntity> target = courseId == null
                ? courseRepository.findByTenantIdAndStatus(tenantId, "PUBLISHED", PageRequest.of(0, 200))
                : courseRepository.findById(courseId).filter(c -> tenantId.equals(c.getTenantId())).stream().toList();
        List<Map<String, Object>> items = new ArrayList<>();
        for (CourseEntity c : target) {
            List<AssessmentEntity> assessments = assessmentRepository.findByTenantIdAndCourseIdAndStatus(
                    tenantId, c.getId(), "PUBLISHED");
            for (AssessmentEntity a : assessments) {
                List<AssessmentAttemptEntity> attemptsForAssessment =
                        attemptRepository.findByTenantIdAndAssessmentId(tenantId, a.getId()).stream()
                                .filter(at -> subjectType == null || subjectType.isBlank()
                                        || subjectType.equalsIgnoreCase(at.getSubjectType()))
                                .toList();
                List<Integer> scored = attemptsForAssessment.stream()
                        .map(at -> at.getScore())
                        .filter(s -> s != null)
                        .toList();
                long pass = attemptsForAssessment.stream()
                        .filter(at -> Boolean.TRUE.equals(at.getPassed()))
                        .count();
                int attempts = attemptsForAssessment.size();
                double avg = scored.isEmpty() ? 0.0 : Math.round((scored.stream().mapToInt(Integer::intValue).average().orElse(0) * 10.0)) / 10.0;
                double passRate = attempts == 0 ? 0.0 : Math.round((pass * 1000.0 / attempts)) / 10.0;
                items.add(Map.of(
                        "courseId", c.getId().toString(),
                        "courseCode", c.getCode(),
                        "assessmentId", a.getId().toString(),
                        "assessmentTitle", a.getTitle(),
                        "attempts", attempts,
                        "averageScore", avg,
                        "passRatePercent", passRate));
            }
        }
        return Map.of("items", limit(items, limit));
    }

    @Transactional(readOnly = true)
    public Map<String, Object> cohortCompletions(UUID tenantId, UUID pathwayId, UUID courseId) {
        return cohortReportService.cohortCompletions(tenantId, new FundoCohortReportService.CohortReportFilter(pathwayId, courseId));
    }

    private static List<Map<String, Object>> limit(List<Map<String, Object>> items, Integer limit) {
        int max = Math.max(1, Math.min(limit == null ? 100 : limit, 500));
        return items.size() <= max ? items : items.subList(0, max);
    }
}
