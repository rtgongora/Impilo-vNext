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
import zw.gov.mohcc.impilo.learning.persistence.entity.FundoPathwayEntity;
import zw.gov.mohcc.impilo.learning.persistence.repository.CertificateRepository;
import zw.gov.mohcc.impilo.learning.persistence.repository.CourseRepository;
import zw.gov.mohcc.impilo.learning.persistence.repository.EnrolmentRepository;
import zw.gov.mohcc.impilo.learning.persistence.repository.FundoPathwayRepository;

/** Aggregates learner journey and CPD-evidence views for native Fundo. */
@Service
public class FundoLearnerJourneyService {

    private final EnrolmentRepository enrolmentRepository;
    private final CourseRepository courseRepository;
    private final CertificateRepository certificateRepository;
    private final FundoPathwayRepository pathwayRepository;
    private final FundoLearningRecordService learningRecordService;

    public FundoLearnerJourneyService(
            EnrolmentRepository enrolmentRepository,
            CourseRepository courseRepository,
            CertificateRepository certificateRepository,
            FundoPathwayRepository pathwayRepository,
            FundoLearningRecordService learningRecordService) {
        this.enrolmentRepository = enrolmentRepository;
        this.courseRepository = courseRepository;
        this.certificateRepository = certificateRepository;
        this.pathwayRepository = pathwayRepository;
        this.learningRecordService = learningRecordService;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> myLearning(UUID tenantId, String subjectType, String subjectId) {
        List<EnrolmentEntity> enrolments = enrolmentRepository
                .findByTenantIdAndSubjectTypeAndSubjectIdOrderByCreatedAtDesc(
                        tenantId, subjectType, subjectId, PageRequest.of(0, 200));
        Map<UUID, CourseEntity> courses = courseRepository.findAllById(
                        enrolments.stream().map(EnrolmentEntity::getCourseId).collect(Collectors.toSet()))
                .stream()
                .filter(c -> tenantId.equals(c.getTenantId()))
                .collect(Collectors.toMap(CourseEntity::getId, c -> c));

        List<Map<String, Object>> enrolled = new ArrayList<>();
        List<Map<String, Object>> inProgress = new ArrayList<>();
        List<Map<String, Object>> completed = new ArrayList<>();
        List<Map<String, Object>> overdue = new ArrayList<>();
        List<Map<String, Object>> cancelled = new ArrayList<>();
        List<Map<String, Object>> required = new ArrayList<>();
        Set<UUID> unavailableCourseIds = enrolments.stream()
                .filter(e -> !"CANCELLED".equals(e.getStatus()))
                .map(EnrolmentEntity::getCourseId)
                .collect(Collectors.toSet());
        Set<UUID> pathwayIds = enrolments.stream()
                .map(EnrolmentEntity::getPathwayId)
                .filter(p -> p != null)
                .collect(Collectors.toSet());

        for (EnrolmentEntity e : enrolments) {
            Map<String, Object> row = FundoEnrolmentService.toView(e);
            CourseEntity course = courses.get(e.getCourseId());
            boolean mandatoryCourse = false;
            if (course != null) {
                row.put("courseCode", course.getCode());
                row.put("courseTitle", course.getTitle());
                row.put("category", course.getCategory());
                row.put("cpdEligible", course.isCpdEligible());
                row.put("cpdPoints", course.getCpdPoints());
                row.put("mandatory", course.isMandatory());
                mandatoryCourse = course.isMandatory();
            }
            if ("ENROLLED".equals(e.getStatus())) enrolled.add(row);
            if ("IN_PROGRESS".equals(e.getStatus())) inProgress.add(row);
            if ("COMPLETED".equals(e.getStatus())) completed.add(row);
            if ("CANCELLED".equals(e.getStatus())) cancelled.add(row);
            boolean outstanding = !"COMPLETED".equals(e.getStatus()) && !"CANCELLED".equals(e.getStatus());
            if (e.getDueAt() != null
                    && e.getDueAt().isBefore(OffsetDateTime.now())
                    && outstanding) {
                overdue.add(row);
            }
            // Honest "required" bucket: outstanding learning the subject is obliged to
            // finish. Same semantics as FundoWorkforceReadinessService.isRequired —
            // a mandatory-flagged course, or an enrolment assigned by orchestration.
            // Enrolment-scoped truth only; role/workspace requirement gaps belong to
            // the training-gate and are never guessed here.
            String type = e.getEnrolmentType();
            boolean assignedType = "ASSIGNED".equals(type) || "SYSTEM".equals(type) || "COHORT".equals(type);
            if (outstanding && (mandatoryCourse || assignedType)) {
                required.add(row);
            }
        }

        List<Map<String, Object>> recommended = courseRepository
                .findByTenantIdAndStatus(tenantId, "PUBLISHED", PageRequest.of(0, 30))
                .stream()
                .filter(c -> !unavailableCourseIds.contains(c.getId()))
                .limit(12)
                .map(FundoCatalogService::toView)
                .toList();

        List<Map<String, Object>> assignedPathways = pathwayIds.stream()
                .map(id -> pathwayRepository.findByTenantIdAndId(tenantId, id))
                .filter(java.util.Optional::isPresent)
                .map(java.util.Optional::get)
                .map(this::toPathwayView)
                .toList();

        List<Map<String, Object>> certificates = certificateRepository
                .findByTenantIdAndSubjectTypeAndSubjectId(tenantId, subjectType, subjectId)
                .stream()
                .map(FundoCertificateService::toView)
                .toList();

        Map<String, Object> record = learningRecordService.getRecord(tenantId, subjectType, subjectId);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> cpdEligible = (List<Map<String, Object>>) record.getOrDefault("cpdEligibleCompletions", List.of());

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("subjectType", subjectType);
        out.put("subjectId", subjectId);
        out.put("enrolled", enrolled);
        out.put("inProgress", inProgress);
        out.put("completed", completed);
        out.put("overdue", overdue);
        out.put("cancelled", cancelled);
        out.put("required", required);
        out.put("recommended", recommended);
        out.put("assignedPathways", assignedPathways);
        out.put("certificates", certificates);
        out.put("cpdEligibleCompletions", cpdEligible);
        return out;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> cpdEvidence(UUID tenantId, String subjectType, String subjectId) {
        Map<String, Object> record = learningRecordService.getRecord(tenantId, subjectType, subjectId);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("subjectType", subjectType);
        data.put("subjectId", subjectId);
        data.put("evidence", record.getOrDefault("cpdEligibleCompletions", List.of()));
        data.put("certificates", record.getOrDefault("certificates", List.of()));
        data.put("completedCourses", record.getOrDefault("completedCourses", List.of()));
        return data;
    }

    private Map<String, Object> toPathwayView(FundoPathwayEntity p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", p.getId().toString());
        m.put("code", p.getCode());
        m.put("title", p.getTitle());
        m.put("status", p.getStatus());
        return m;
    }
}
