package zw.gov.mohcc.impilo.learning.fundo;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.learning.persistence.entity.AssessmentAttemptEntity;
import zw.gov.mohcc.impilo.learning.persistence.entity.AssessmentEntity;
import zw.gov.mohcc.impilo.learning.persistence.entity.AssessmentQuestionEntity;
import zw.gov.mohcc.impilo.learning.persistence.entity.EnrolmentEntity;
import zw.gov.mohcc.impilo.learning.persistence.repository.AssessmentAttemptRepository;
import zw.gov.mohcc.impilo.learning.persistence.repository.AssessmentQuestionRepository;
import zw.gov.mohcc.impilo.learning.persistence.repository.AssessmentRepository;
import zw.gov.mohcc.impilo.learning.persistence.repository.EnrolmentRepository;

/**
 * Native assessment foundation (Phase 5C). Lists assessments by course,
 * returns assessment+questions for detail, and records attempts with
 * simple objective scoring for {@code MULTIPLE_CHOICE} and
 * {@code TRUE_FALSE} questions. Non-objective question types
 * ({@code SHORT_ANSWER}, {@code MATCHING}, {@code CASE_PROMPT}) are
 * accepted with {@code passed} left null (manual marking pending).
 */
@Service
public class FundoAssessmentService {

    private final AssessmentRepository assessmentRepository;
    private final AssessmentQuestionRepository questionRepository;
    private final AssessmentAttemptRepository attemptRepository;
    private final EnrolmentRepository enrolmentRepository;
    private final FundoOutboxAppender outbox;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public FundoAssessmentService(
            AssessmentRepository assessmentRepository,
            AssessmentQuestionRepository questionRepository,
            AssessmentAttemptRepository attemptRepository,
            EnrolmentRepository enrolmentRepository,
            FundoOutboxAppender outbox) {
        this.assessmentRepository = assessmentRepository;
        this.questionRepository = questionRepository;
        this.attemptRepository = attemptRepository;
        this.enrolmentRepository = enrolmentRepository;
        this.outbox = outbox;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listByCourse(UUID tenantId, UUID courseId) {
        return assessmentRepository
                .findByTenantIdAndCourseIdAndStatus(tenantId, courseId, "PUBLISHED")
                .stream().map(FundoAssessmentService::toAssessmentView).toList();
    }

    @Transactional(readOnly = true)
    public Optional<Map<String, Object>> getAssessment(UUID tenantId, UUID assessmentId) {
        Optional<AssessmentEntity> a = assessmentRepository.findByTenantIdAndId(tenantId, assessmentId);
        if (a.isEmpty()) return Optional.empty();
        List<AssessmentQuestionEntity> questions =
                questionRepository.findByAssessmentIdOrderBySequenceNoAsc(assessmentId);
        Map<String, Object> view = new LinkedHashMap<>(toAssessmentView(a.get()));
        view.put("questions", questions.stream().map(FundoAssessmentService::toQuestionViewNoAnswer).toList());
        return Optional.of(view);
    }

    @Transactional
    public Map<String, Object> submitAttempt(UUID tenantId, UUID assessmentId, AttemptSubmission submission) {
        AssessmentEntity assessment = assessmentRepository.findByTenantIdAndId(tenantId, assessmentId)
                .orElseThrow(() -> new IllegalArgumentException("assessment_not_found"));

        long existingAttempts = attemptRepository.countByAssessmentIdAndSubjectTypeAndSubjectId(
                assessmentId, submission.subjectType(), submission.subjectId());
        if (existingAttempts >= assessment.getMaxAttempts()) {
            throw new IllegalStateException("max_attempts_reached");
        }

        List<AssessmentQuestionEntity> questions =
                questionRepository.findByAssessmentIdOrderBySequenceNoAsc(assessmentId);

        ScoreResult result = scoreAttempt(questions, submission.answers());

        AssessmentAttemptEntity attempt = new AssessmentAttemptEntity();
        attempt.setTenantId(tenantId);
        attempt.setAssessmentId(assessmentId);
        if (submission.enrolmentId() != null) {
            Optional<EnrolmentEntity> e = enrolmentRepository.findByTenantIdAndId(tenantId, submission.enrolmentId());
            e.ifPresent(en -> attempt.setEnrolmentId(en.getId()));
        }
        attempt.setSubjectType(submission.subjectType());
        attempt.setSubjectId(submission.subjectId());
        attempt.setAttemptNo((int) existingAttempts + 1);
        attempt.setScore(result.score());
        attempt.setPassed(result.passed(assessment.getPassMark()));
        attempt.setManualReviewStatus(result.requiresManualReview() ? "PENDING_MANUAL_REVIEW" : "AUTO_GRADED");
        attempt.setManualReviewScore(result.score());
        attempt.setManualReviewPassed(result.passed(assessment.getPassMark()));
        attempt.setSubmittedAt(OffsetDateTime.now());
        attemptRepository.save(attempt);

        outbox.append("FundoAssessmentAttempt", attempt.getId().toString(),
                FundoNativeEventTypes.ASSESSMENT_ATTEMPT_SUBMITTED,
                Map.of(
                        "tenantId", tenantId.toString(),
                        "assessmentId", assessmentId.toString(),
                        "courseId", assessment.getCourseId().toString(),
                        "subjectType", submission.subjectType(),
                        "subjectId", submission.subjectId(),
                        "attemptNo", attempt.getAttemptNo(),
                        "score", attempt.getScore() == null ? -1 : attempt.getScore(),
                        "passed", attempt.getPassed() == null ? "PENDING" : attempt.getPassed().toString(),
                        "assessmentType", assessment.getAssessmentType()));

        return toAttemptView(attempt);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listPendingManualReviews(UUID tenantId, UUID assessmentId, Integer limit) {
        int max = Math.max(1, Math.min(limit == null ? 100 : limit, 500));
        return attemptRepository
                .findByTenantIdAndAssessmentIdAndManualReviewStatusOrderBySubmittedAtDesc(
                        tenantId, assessmentId, "PENDING_MANUAL_REVIEW")
                .stream()
                .limit(max)
                .map(FundoAssessmentService::toAttemptView)
                .toList();
    }

    @Transactional
    public Optional<Map<String, Object>> recordManualReview(
            UUID tenantId, UUID attemptId, ManualReviewUpdate review) {
        Optional<AssessmentAttemptEntity> row = attemptRepository.findByTenantIdAndId(tenantId, attemptId);
        if (row.isEmpty()) return Optional.empty();
        AssessmentAttemptEntity attempt = row.get();
        attempt.setManualReviewStatus("REVIEWED");
        if (review.score() != null) {
            attempt.setManualReviewScore(review.score());
            attempt.setScore(review.score());
        }
        if (review.passed() != null) {
            attempt.setManualReviewPassed(review.passed());
            attempt.setPassed(review.passed());
        }
        attempt.setReviewerId(review.reviewerId());
        attempt.setReviewedAt(OffsetDateTime.now());
        attempt.setRubricAppliedJson(review.rubricAppliedJson());
        attempt.setFeedbackText(review.feedbackText());
        attemptRepository.save(attempt);
        outbox.append("FundoAssessmentAttempt", attempt.getId().toString(),
                FundoNativeEventTypes.ASSESSMENT_ATTEMPT_REVIEWED,
                Map.of(
                        "tenantId", tenantId.toString(),
                        "assessmentId", attempt.getAssessmentId().toString(),
                        "attemptId", attempt.getId().toString(),
                        "reviewerId", review.reviewerId() == null ? "unknown" : review.reviewerId(),
                        "score", attempt.getScore() == null ? -1 : attempt.getScore(),
                        "passed", attempt.getPassed() == null ? "PENDING" : attempt.getPassed().toString()));
        return Optional.of(toAttemptView(attempt));
    }

    private ScoreResult scoreAttempt(List<AssessmentQuestionEntity> questions, Map<String, Object> answers) {
        if (questions.isEmpty()) return ScoreResult.pending();
        int total = 0;
        int earned = 0;
        boolean hasObjective = false;
        boolean hasNonObjective = false;
        for (AssessmentQuestionEntity q : questions) {
            total += q.getPoints();
            String type = q.getQuestionType();
            if ("MULTIPLE_CHOICE".equals(type) || "TRUE_FALSE".equals(type)) {
                hasObjective = true;
                Object given = answers == null ? null : answers.get(q.getId().toString());
                if (given != null && q.getCorrectAnswerJson() != null) {
                    try {
                        Object correct = objectMapper.readValue(q.getCorrectAnswerJson(), Object.class);
                        if (Objects.equals(stringify(given), stringify(correct))) {
                            earned += q.getPoints();
                        }
                    } catch (JsonProcessingException ignored) {
                        // treat as incorrect on parse failure
                    }
                }
            } else {
                hasNonObjective = true;
            }
        }
        if (!hasObjective || hasNonObjective) {
            return ScoreResult.pending();
        }
        int percent = total == 0 ? 0 : (int) Math.round(earned * 100.0 / total);
        return ScoreResult.scored(percent);
    }

    private String stringify(Object o) {
        return o == null ? "" : o.toString().trim().toLowerCase();
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> attemptsForSubject(
            UUID tenantId, String subjectType, String subjectId) {
        return attemptRepository.findByTenantIdAndSubjectTypeAndSubjectId(tenantId, subjectType, subjectId)
                .stream().map(FundoAssessmentService::toAttemptView).toList();
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> attemptsForAssessment(
            UUID tenantId, UUID assessmentId, String subjectType, String subjectId) {
        return attemptRepository
                .findByAssessmentIdAndSubjectTypeAndSubjectIdOrderByAttemptNoDesc(assessmentId, subjectType, subjectId)
                .stream()
                .filter(a -> tenantId.equals(a.getTenantId()))
                .map(FundoAssessmentService::toAttemptView)
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<Map<String, Object>> getAttempt(UUID tenantId, UUID attemptId) {
        return attemptRepository.findByTenantIdAndId(tenantId, attemptId).map(FundoAssessmentService::toAttemptView);
    }

    public static Map<String, Object> toAssessmentView(AssessmentEntity a) {
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("id", a.getId().toString());
        v.put("courseId", a.getCourseId().toString());
        v.put("moduleId", a.getModuleId() == null ? null : a.getModuleId().toString());
        v.put("title", a.getTitle());
        v.put("description", a.getDescription());
        v.put("assessmentType", a.getAssessmentType());
        v.put("passMark", a.getPassMark());
        v.put("maxAttempts", a.getMaxAttempts());
        v.put("status", a.getStatus());
        return v;
    }

    public static Map<String, Object> toQuestionViewNoAnswer(AssessmentQuestionEntity q) {
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("id", q.getId().toString());
        v.put("type", q.getQuestionType());
        v.put("prompt", q.getPrompt());
        v.put("optionsJson", q.getOptionsJson());
        v.put("rubricJson", q.getRubricJson());
        v.put("sequence", q.getSequenceNo());
        v.put("points", q.getPoints());
        return v;
    }

    public static Map<String, Object> toAttemptView(AssessmentAttemptEntity a) {
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("id", a.getId().toString());
        v.put("assessmentId", a.getAssessmentId().toString());
        v.put("enrolmentId", a.getEnrolmentId() == null ? null : a.getEnrolmentId().toString());
        v.put("subjectType", a.getSubjectType());
        v.put("subjectId", a.getSubjectId());
        v.put("attemptNo", a.getAttemptNo());
        v.put("score", a.getScore());
        v.put("passed", a.getPassed());
        v.put("manualReviewStatus", a.getManualReviewStatus());
        v.put("manualReviewScore", a.getManualReviewScore());
        v.put("manualReviewPassed", a.getManualReviewPassed());
        v.put("reviewerId", a.getReviewerId());
        v.put("reviewedAt", a.getReviewedAt() == null ? null : a.getReviewedAt().toString());
        v.put("rubricAppliedJson", a.getRubricAppliedJson());
        v.put("feedbackText", a.getFeedbackText());
        v.put("submittedAt", a.getSubmittedAt() == null ? null : a.getSubmittedAt().toString());
        return v;
    }

    public record AttemptSubmission(
            UUID enrolmentId,
            String subjectType,
            String subjectId,
            Map<String, Object> answers) {}

    public record ManualReviewUpdate(
            Integer score,
            Boolean passed,
            String reviewerId,
            String rubricAppliedJson,
            String feedbackText) {}

    private record ScoreResult(Integer score, boolean objectiveOnly, boolean requiresManualReview) {
        static ScoreResult pending() { return new ScoreResult(null, false, true); }
        static ScoreResult scored(int percent) { return new ScoreResult(percent, true, false); }
        Boolean passed(int passMark) {
            if (score == null || !objectiveOnly) return null;
            return score >= passMark;
        }
    }

    /** Convenience accessor for the learning record service. */
    public List<Map<String, Object>> summaryForSubject(UUID tenantId, String subjectType, String subjectId) {
        List<Map<String, Object>> all = attemptsForSubject(tenantId, subjectType, subjectId);
        return new ArrayList<>(all);
    }
}
