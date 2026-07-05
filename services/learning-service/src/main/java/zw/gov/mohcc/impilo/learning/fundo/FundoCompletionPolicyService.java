package zw.gov.mohcc.impilo.learning.fundo;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.learning.persistence.entity.AssessmentAttemptEntity;
import zw.gov.mohcc.impilo.learning.persistence.entity.AssessmentEntity;
import zw.gov.mohcc.impilo.learning.persistence.entity.CompletionRuleEntity;
import zw.gov.mohcc.impilo.learning.persistence.entity.CourseLessonEntity;
import zw.gov.mohcc.impilo.learning.persistence.entity.CourseModuleEntity;
import zw.gov.mohcc.impilo.learning.persistence.entity.CourseProgressEntity;
import zw.gov.mohcc.impilo.learning.persistence.entity.EnrolmentEntity;
import zw.gov.mohcc.impilo.learning.persistence.entity.MediaAssetEntity;
import zw.gov.mohcc.impilo.learning.persistence.entity.MediaWatchProgressEntity;
import zw.gov.mohcc.impilo.learning.persistence.entity.ScheduledLearningSessionEntity;
import zw.gov.mohcc.impilo.learning.persistence.entity.SessionAttendanceEntity;
import zw.gov.mohcc.impilo.learning.persistence.repository.AssessmentAttemptRepository;
import zw.gov.mohcc.impilo.learning.persistence.repository.AssessmentRepository;
import zw.gov.mohcc.impilo.learning.persistence.repository.CompletionRuleRepository;
import zw.gov.mohcc.impilo.learning.persistence.repository.CourseLessonRepository;
import zw.gov.mohcc.impilo.learning.persistence.repository.CourseModuleRepository;
import zw.gov.mohcc.impilo.learning.persistence.repository.CourseProgressRepository;
import zw.gov.mohcc.impilo.learning.persistence.repository.EnrolmentRepository;
import zw.gov.mohcc.impilo.learning.persistence.repository.MediaAssetRepository;
import zw.gov.mohcc.impilo.learning.persistence.repository.MediaWatchProgressRepository;
import zw.gov.mohcc.impilo.learning.persistence.repository.ScheduledLearningSessionRepository;
import zw.gov.mohcc.impilo.learning.persistence.repository.SessionAttendanceRepository;

/**
 * Configurable completion policy (LEARNING_LIVE W3) — evaluates the
 * {@code lrn_completion_rule} rows for a course against an enrolment.
 *
 * <p><strong>Contract:</strong> a course with NO rules keeps the legacy implicit
 * behaviour (callers complete on the all-required-lessons-at-100 / direct
 * aggregate-100 paths, unchanged). A course WITH rules only transitions its
 * enrolments to COMPLETED when every {@code required=true} rule is satisfied
 * (AND semantics). Optional ({@code required=false}) rules are evaluated and
 * reported but never block completion.</p>
 *
 * <p><strong>Rule-type semantics:</strong></p>
 * <ul>
 *   <li>{@code ATTENDANCE_THRESHOLD} — {@code threshold_value} is <em>MINUTES</em>
 *       (not percent): the minimum total webhook-accurate watch minutes
 *       ({@code lrn_session_attendance.watch_minutes}) summed across the course's
 *       LIVE-mode sessions for the enrolment subject. A {@code NULL} threshold
 *       degrades to "any PRESENT/LATE attendance row on a live session". Subject
 *       matching is by {@code subject_id} only — the Impilo Live webhook records
 *       attendance with the participant identity, whose subject-type idiom
 *       (PARTICIPANT) may differ from the enrolment's (PROVIDER/STUDENT).</li>
 *   <li>{@code QUIZ_REQUIRED} — at least one passed attempt (auto {@code passed}
 *       or {@code manual_review_passed}) on a QUIZ assessment of the course.</li>
 *   <li>{@code ASSESSMENT_PASS} — a passed attempt on a FINAL_ASSESSMENT or
 *       POST_TEST assessment of the course.</li>
 *   <li>{@code FACILITATOR_CONFIRM} — {@code lrn_enrolment.facilitator_confirmed_at}
 *       is set (written by {@code POST /v11/enrolments/{id}/facilitator-confirm}).</li>
 *   <li>{@code LESSON_PROGRESS} — the legacy all-required-lessons-COMPLETED rule
 *       in explicit form; a course with no required lessons satisfies it vacuously.</li>
 *   <li>{@code WATCH_THRESHOLD} — {@code threshold_value} is a PERCENT (default
 *       90): every VIDEO media asset bound to the course
 *       ({@code lrn_media_asset.course_id}, W4 replay adoption) must have a
 *       {@code lrn_media_watch_progress} row for the enrolment at or above the
 *       threshold. A course with NO course-bound video assets evaluates FALSE —
 *       the rule demands watching something that does not exist yet (mirrors
 *       ATTENDANCE_THRESHOLD's no-live-sessions honesty).</li>
 * </ul>
 */
@Service
public class FundoCompletionPolicyService {

    private static final Logger log = LoggerFactory.getLogger(FundoCompletionPolicyService.class);

    static final Set<String> RULE_TYPES = Set.of(
            "ATTENDANCE_THRESHOLD", "QUIZ_REQUIRED", "ASSESSMENT_PASS",
            "FACILITATOR_CONFIRM", "LESSON_PROGRESS", "WATCH_THRESHOLD");
    private static final Set<String> ATTENDED_STATUSES = Set.of("PRESENT", "LATE");

    private final CompletionRuleRepository ruleRepository;
    private final ScheduledLearningSessionRepository sessionRepository;
    private final SessionAttendanceRepository attendanceRepository;
    private final AssessmentRepository assessmentRepository;
    private final AssessmentAttemptRepository attemptRepository;
    private final CourseModuleRepository moduleRepository;
    private final CourseLessonRepository lessonRepository;
    private final CourseProgressRepository progressRepository;
    private final EnrolmentRepository enrolmentRepository;
    private final MediaAssetRepository mediaAssetRepository;
    private final MediaWatchProgressRepository watchProgressRepository;

    public FundoCompletionPolicyService(
            CompletionRuleRepository ruleRepository,
            ScheduledLearningSessionRepository sessionRepository,
            SessionAttendanceRepository attendanceRepository,
            AssessmentRepository assessmentRepository,
            AssessmentAttemptRepository attemptRepository,
            CourseModuleRepository moduleRepository,
            CourseLessonRepository lessonRepository,
            CourseProgressRepository progressRepository,
            EnrolmentRepository enrolmentRepository,
            MediaAssetRepository mediaAssetRepository,
            MediaWatchProgressRepository watchProgressRepository) {
        this.ruleRepository = ruleRepository;
        this.sessionRepository = sessionRepository;
        this.attendanceRepository = attendanceRepository;
        this.assessmentRepository = assessmentRepository;
        this.attemptRepository = attemptRepository;
        this.moduleRepository = moduleRepository;
        this.lessonRepository = lessonRepository;
        this.progressRepository = progressRepository;
        this.enrolmentRepository = enrolmentRepository;
        this.mediaAssetRepository = mediaAssetRepository;
        this.watchProgressRepository = watchProgressRepository;
    }

    /**
     * Outcome of a policy evaluation. {@code hasRules=false} means the course has
     * no configured rules and callers must keep the legacy completion behaviour
     * ({@code complete} is {@code false} in that case and must be ignored).
     */
    public record PolicyOutcome(boolean hasRules, boolean complete,
                                List<String> satisfied, List<String> missing) {

        public Map<String, Object> toPayload() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("hasRules", hasRules);
            m.put("complete", complete);
            m.put("satisfied", satisfied);
            m.put("missing", missing);
            return m;
        }
    }

    @Transactional(readOnly = true)
    public PolicyOutcome evaluate(UUID tenantId, EnrolmentEntity enrolment) {
        List<CompletionRuleEntity> rules =
                ruleRepository.findByTenantIdAndCourseIdOrderByCreatedAtAsc(tenantId, enrolment.getCourseId());
        if (rules.isEmpty()) {
            return new PolicyOutcome(false, false, List.of(), List.of());
        }
        List<String> satisfied = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        boolean complete = true;
        for (CompletionRuleEntity rule : rules) {
            boolean met = evaluateRule(tenantId, enrolment, rule);
            if (met) {
                satisfied.add(rule.getRuleType());
            } else {
                missing.add(rule.getRuleType());
                if (rule.isRequired()) {
                    complete = false;
                }
            }
        }
        return new PolicyOutcome(true, complete, List.copyOf(satisfied), List.copyOf(missing));
    }

    private boolean evaluateRule(UUID tenantId, EnrolmentEntity enrolment, CompletionRuleEntity rule) {
        return switch (rule.getRuleType()) {
            case "ATTENDANCE_THRESHOLD" -> attendanceThresholdMet(tenantId, enrolment, rule.getThresholdValue());
            case "QUIZ_REQUIRED" -> passedAttemptExists(tenantId, enrolment,
                    type -> "QUIZ".equalsIgnoreCase(type));
            case "ASSESSMENT_PASS" -> passedAttemptExists(tenantId, enrolment,
                    type -> "FINAL_ASSESSMENT".equalsIgnoreCase(type) || "POST_TEST".equalsIgnoreCase(type));
            case "FACILITATOR_CONFIRM" -> enrolment.getFacilitatorConfirmedAt() != null;
            case "LESSON_PROGRESS" -> allRequiredLessonsCompleted(enrolment);
            case "WATCH_THRESHOLD" -> watchThresholdMet(tenantId, enrolment, rule.getThresholdValue());
            default -> {
                log.warn("Unknown completion rule type '{}' for course {} — treated as unmet",
                        rule.getRuleType(), enrolment.getCourseId());
                yield false;
            }
        };
    }

    private boolean attendanceThresholdMet(UUID tenantId, EnrolmentEntity enrolment, BigDecimal threshold) {
        List<UUID> liveSessionIds = sessionRepository
                .findByTenantIdAndCourseId(tenantId, enrolment.getCourseId()).stream()
                .filter(s -> "LIVE".equals(s.getSessionMode()) || "HYBRID".equals(s.getSessionMode())
                        || s.getLiveEventId() != null)
                .map(ScheduledLearningSessionEntity::getId)
                .toList();
        if (liveSessionIds.isEmpty()) {
            return false;
        }
        List<SessionAttendanceEntity> rows = attendanceRepository
                .findBySessionIdInAndSubjectId(liveSessionIds, enrolment.getSubjectId()).stream()
                .filter(a -> ATTENDED_STATUSES.contains(a.getStatus()))
                .toList();
        if (rows.isEmpty()) {
            return false;
        }
        if (threshold == null) {
            return true;
        }
        int totalMinutes = rows.stream()
                .mapToInt(a -> a.getWatchMinutes() == null ? 0 : a.getWatchMinutes())
                .sum();
        return totalMinutes >= threshold.intValue();
    }

    /**
     * WATCH_THRESHOLD (W4): every VIDEO asset bound to the course must be watched
     * to at least {@code threshold} percent (default
     * {@link FundoMediaWatchService#DEFAULT_WATCH_THRESHOLD_PERCENT}) by the
     * enrolment. No course-bound video assets → FALSE (nothing to watch yet).
     */
    private boolean watchThresholdMet(UUID tenantId, EnrolmentEntity enrolment, BigDecimal threshold) {
        int requiredPercent = threshold != null && threshold.compareTo(BigDecimal.ZERO) > 0
                ? threshold.intValue()
                : FundoMediaWatchService.DEFAULT_WATCH_THRESHOLD_PERCENT;
        List<MediaAssetEntity> videoAssets = mediaAssetRepository
                .findByTenantIdAndCourseId(tenantId, enrolment.getCourseId()).stream()
                .filter(a -> "VIDEO".equalsIgnoreCase(a.getMediaType()))
                .toList();
        if (videoAssets.isEmpty()) {
            log.info("WATCH_THRESHOLD for course {} evaluated FALSE — no course-bound video assets",
                    enrolment.getCourseId());
            return false;
        }
        for (MediaAssetEntity asset : videoAssets) {
            Optional<MediaWatchProgressEntity> row = watchProgressRepository
                    .findByEnrolmentIdAndAssetId(enrolment.getId(), asset.getId());
            if (row.isEmpty() || row.get().getPercent() < requiredPercent) {
                return false;
            }
        }
        return true;
    }

    private boolean passedAttemptExists(UUID tenantId, EnrolmentEntity enrolment,
                                        Predicate<String> assessmentTypeMatches) {
        List<AssessmentEntity> assessments =
                assessmentRepository.findByTenantIdAndCourseId(tenantId, enrolment.getCourseId());
        for (AssessmentEntity assessment : assessments) {
            if (assessment.getAssessmentType() == null
                    || !assessmentTypeMatches.test(assessment.getAssessmentType())) {
                continue;
            }
            List<AssessmentAttemptEntity> attempts = attemptRepository
                    .findByAssessmentIdAndSubjectTypeAndSubjectIdOrderByAttemptNoDesc(
                            assessment.getId(), enrolment.getSubjectType(), enrolment.getSubjectId());
            boolean passed = attempts.stream().anyMatch(a ->
                    Boolean.TRUE.equals(a.getPassed()) || Boolean.TRUE.equals(a.getManualReviewPassed()));
            if (passed) {
                return true;
            }
        }
        return false;
    }

    private boolean allRequiredLessonsCompleted(EnrolmentEntity enrolment) {
        List<CourseModuleEntity> modules =
                moduleRepository.findByCourseIdOrderBySequenceNoAsc(enrolment.getCourseId());
        if (modules.isEmpty()) {
            return true; // no lessons to complete — vacuously satisfied
        }
        List<UUID> moduleIds = modules.stream().map(CourseModuleEntity::getId).toList();
        List<CourseLessonEntity> requiredLessons = lessonRepository
                .findByModuleIdInOrderBySequenceNoAsc(moduleIds).stream()
                .filter(CourseLessonEntity::isRequired)
                .toList();
        if (requiredLessons.isEmpty()) {
            return true;
        }
        for (CourseLessonEntity lesson : requiredLessons) {
            Optional<CourseProgressEntity> row =
                    progressRepository.findByEnrolmentIdAndLessonId(enrolment.getId(), lesson.getId());
            if (row.isEmpty() || !"COMPLETED".equals(row.get().getStatus())) {
                return false;
            }
        }
        return true;
    }

    // ── rule configuration (studio surface) ─────────────────────────────────

    @Transactional(readOnly = true)
    public Map<String, Object> listRules(UUID tenantId, UUID courseId) {
        List<Map<String, Object>> items = ruleRepository
                .findByTenantIdAndCourseIdOrderByCreatedAtAsc(tenantId, courseId).stream()
                .map(FundoCompletionPolicyService::ruleView)
                .toList();
        return Map.of("courseId", courseId.toString(), "items", items);
    }

    /** Upsert on (tenant, course, ruleType) — mirrors the V028 unique constraint. */
    @Transactional
    public Map<String, Object> upsertRule(UUID tenantId, UUID courseId, String actorId, Map<String, Object> body) {
        Object rawType = body.get("ruleType");
        String ruleType = rawType == null ? null : rawType.toString().trim().toUpperCase();
        if (ruleType == null || !RULE_TYPES.contains(ruleType)) {
            throw new IllegalArgumentException("ruleType must be one of " + RULE_TYPES);
        }
        CompletionRuleEntity rule = ruleRepository
                .findByTenantIdAndCourseIdAndRuleType(tenantId, courseId, ruleType)
                .orElseGet(CompletionRuleEntity::new);
        rule.setTenantId(tenantId);
        rule.setCourseId(courseId);
        rule.setRuleType(ruleType);
        Object threshold = body.get("thresholdValue");
        rule.setThresholdValue(threshold == null || threshold.toString().isBlank()
                ? null : new BigDecimal(threshold.toString()));
        Object required = body.get("required");
        rule.setRequired(required == null || Boolean.parseBoolean(required.toString()));
        if (rule.getCreatedBy() == null) {
            rule.setCreatedBy(actorId);
        }
        ruleRepository.save(rule);
        return ruleView(rule);
    }

    @Transactional
    public Map<String, Object> deleteRule(UUID tenantId, UUID ruleId) {
        CompletionRuleEntity rule = ruleRepository.findByTenantIdAndId(tenantId, ruleId)
                .orElseThrow(() -> new IllegalArgumentException("completion rule not found"));
        ruleRepository.delete(rule);
        return Map.of("id", ruleId.toString(), "deleted", true);
    }

    /** Records the FACILITATOR_CONFIRM marker on the enrolment (idempotent). */
    @Transactional
    public Map<String, Object> facilitatorConfirm(UUID tenantId, UUID enrolmentId, String actorId) {
        EnrolmentEntity enrolment = enrolmentRepository.findByTenantIdAndId(tenantId, enrolmentId)
                .orElseThrow(() -> new IllegalArgumentException("enrolment_not_found"));
        boolean alreadyConfirmed = enrolment.getFacilitatorConfirmedAt() != null;
        if (!alreadyConfirmed) {
            enrolment.setFacilitatorConfirmedAt(OffsetDateTime.now());
            enrolment.setFacilitatorConfirmedBy(actorId);
            enrolmentRepository.save(enrolment);
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("enrolmentId", enrolmentId.toString());
        m.put("facilitatorConfirmedAt", enrolment.getFacilitatorConfirmedAt().toString());
        m.put("facilitatorConfirmedBy", enrolment.getFacilitatorConfirmedBy());
        m.put("idempotent", alreadyConfirmed);
        return m;
    }

    private static Map<String, Object> ruleView(CompletionRuleEntity r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", r.getId().toString());
        m.put("courseId", r.getCourseId().toString());
        m.put("ruleType", r.getRuleType());
        m.put("thresholdValue", r.getThresholdValue());
        m.put("required", r.isRequired());
        m.put("createdBy", r.getCreatedBy());
        m.put("createdAt", r.getCreatedAt() == null ? null : r.getCreatedAt().toString());
        return m;
    }
}
