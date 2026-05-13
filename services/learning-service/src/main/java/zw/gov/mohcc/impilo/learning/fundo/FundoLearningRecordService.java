package zw.gov.mohcc.impilo.learning.fundo;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.learning.persistence.entity.CertificateEntity;
import zw.gov.mohcc.impilo.learning.persistence.entity.EnrolmentEntity;
import zw.gov.mohcc.impilo.learning.persistence.repository.CertificateRepository;
import zw.gov.mohcc.impilo.learning.persistence.repository.CourseProgressRepository;
import zw.gov.mohcc.impilo.learning.persistence.repository.EnrolmentRepository;

/**
 * Native learner transcript (Phase 5C/5B). Read-only aggregation across
 * native enrolment / progress / certificate / assessment data. Built as
 * query logic over the existing tables — no separate transcript table.
 *
 * <p>The {@code cpdEligibleCompletions} list exposes CPD-eligible course
 * completions as evidence only. Awarding CPD points remains the
 * responsibility of {@code varapi-service} (the canonical CPD ledger);
 * Fundo intentionally does not duplicate that ledger.</p>
 */
@Service
public class FundoLearningRecordService {

    private final EnrolmentRepository enrolmentRepository;
    private final CourseProgressRepository progressRepository;
    private final CertificateRepository certificateRepository;
    private final FundoAssessmentService assessmentService;

    public FundoLearningRecordService(
            EnrolmentRepository enrolmentRepository,
            CourseProgressRepository progressRepository,
            CertificateRepository certificateRepository,
            FundoAssessmentService assessmentService) {
        this.enrolmentRepository = enrolmentRepository;
        this.progressRepository = progressRepository;
        this.certificateRepository = certificateRepository;
        this.assessmentService = assessmentService;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getRecord(UUID tenantId, String subjectType, String subjectId) {
        List<EnrolmentEntity> enrolments = enrolmentRepository
                .findByTenantIdAndSubjectTypeAndSubjectIdOrderByCreatedAtDesc(
                        tenantId, subjectType, subjectId, PageRequest.of(0, 100));

        List<Map<String, Object>> enrolmentViews = enrolments.stream()
                .map(FundoEnrolmentService::toView).toList();

        List<Map<String, Object>> completedCourses = new ArrayList<>();
        List<Map<String, Object>> cpdEligibleCompletions = new ArrayList<>();
        for (EnrolmentEntity e : enrolments) {
            if ("COMPLETED".equals(e.getStatus())) {
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("enrolmentId", e.getId().toString());
                entry.put("courseId", e.getCourseId().toString());
                entry.put("completedAt", e.getCompletedAt() == null ? null : e.getCompletedAt().toString());
                completedCourses.add(entry);
            }
        }

        List<CertificateEntity> certs = certificateRepository
                .findByTenantIdAndSubjectTypeAndSubjectId(tenantId, subjectType, subjectId);
        List<Map<String, Object>> certViews = certs.stream().map(FundoCertificateService::toView).toList();
        for (CertificateEntity c : certs) {
            if (c.isCpdEligible() && "ISSUED".equals(c.getStatus())) {
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("certificateId", c.getId().toString());
                entry.put("courseId", c.getCourseId().toString());
                entry.put("enrolmentId", c.getEnrolmentId().toString());
                entry.put("cpdPoints", c.getCpdPoints());
                entry.put("issuedAt", c.getIssuedAt() == null ? null : c.getIssuedAt().toString());
                cpdEligibleCompletions.add(entry);
            }
        }

        List<Map<String, Object>> progressRows = progressRepository
                .findByTenantIdAndSubjectTypeAndSubjectId(tenantId, subjectType, subjectId)
                .stream().map(FundoProgressService::toView).toList();

        List<Map<String, Object>> attempts = assessmentService.summaryForSubject(tenantId, subjectType, subjectId);

        Map<String, Object> progressSummary = new LinkedHashMap<>();
        long total = enrolments.size();
        long completed = enrolments.stream().filter(e -> "COMPLETED".equals(e.getStatus())).count();
        long inProgress = enrolments.stream().filter(e -> "IN_PROGRESS".equals(e.getStatus())).count();
        long enrolled = enrolments.stream().filter(e -> "ENROLLED".equals(e.getStatus())).count();
        progressSummary.put("totalEnrolments", total);
        progressSummary.put("completed", completed);
        progressSummary.put("inProgress", inProgress);
        progressSummary.put("enrolled", enrolled);

        Map<String, Object> record = new LinkedHashMap<>();
        record.put("subjectType", subjectType);
        record.put("subjectId", subjectId);
        record.put("enrolments", enrolmentViews);
        record.put("progressSummary", progressSummary);
        record.put("progressRows", progressRows);
        record.put("completedCourses", completedCourses);
        record.put("certificates", certViews);
        record.put("assessmentAttempts", attempts);
        record.put("cpdEligibleCompletions", cpdEligibleCompletions);
        return record;
    }
}
