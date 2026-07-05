package zw.gov.mohcc.impilo.learning.fundo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.learning.persistence.entity.AssessmentAttemptEntity;
import zw.gov.mohcc.impilo.learning.persistence.entity.AssessmentEntity;
import zw.gov.mohcc.impilo.learning.persistence.entity.CompletionRuleEntity;
import zw.gov.mohcc.impilo.learning.persistence.entity.CourseLessonEntity;
import zw.gov.mohcc.impilo.learning.persistence.entity.CourseModuleEntity;
import zw.gov.mohcc.impilo.learning.persistence.entity.CourseProgressEntity;
import zw.gov.mohcc.impilo.learning.persistence.entity.EnrolmentEntity;
import zw.gov.mohcc.impilo.learning.persistence.entity.ScheduledLearningSessionEntity;
import zw.gov.mohcc.impilo.learning.persistence.entity.SessionAttendanceEntity;
import zw.gov.mohcc.impilo.learning.persistence.repository.AssessmentAttemptRepository;
import zw.gov.mohcc.impilo.learning.persistence.repository.AssessmentRepository;
import zw.gov.mohcc.impilo.learning.persistence.repository.CompletionRuleRepository;
import zw.gov.mohcc.impilo.learning.persistence.repository.CourseLessonRepository;
import zw.gov.mohcc.impilo.learning.persistence.repository.CourseModuleRepository;
import zw.gov.mohcc.impilo.learning.persistence.repository.CourseProgressRepository;
import zw.gov.mohcc.impilo.learning.persistence.repository.EnrolmentRepository;
import zw.gov.mohcc.impilo.learning.persistence.repository.ScheduledLearningSessionRepository;
import zw.gov.mohcc.impilo.learning.persistence.repository.SessionAttendanceRepository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Completion-policy matrix (W3): rule-type semantics + AND aggregation.
 */
@ExtendWith(MockitoExtension.class)
class FundoCompletionPolicyServiceTest {

    private static final UUID TENANT = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID COURSE = UUID.fromString("00000000-0000-0000-0000-0000000000c1");

    @Mock private CompletionRuleRepository ruleRepository;
    @Mock private ScheduledLearningSessionRepository sessionRepository;
    @Mock private SessionAttendanceRepository attendanceRepository;
    @Mock private AssessmentRepository assessmentRepository;
    @Mock private AssessmentAttemptRepository attemptRepository;
    @Mock private CourseModuleRepository moduleRepository;
    @Mock private CourseLessonRepository lessonRepository;
    @Mock private CourseProgressRepository progressRepository;
    @Mock private EnrolmentRepository enrolmentRepository;

    private FundoCompletionPolicyService policy;
    private EnrolmentEntity enrolment;

    @BeforeEach
    void setUp() {
        policy = new FundoCompletionPolicyService(ruleRepository, sessionRepository, attendanceRepository,
                assessmentRepository, attemptRepository, moduleRepository, lessonRepository,
                progressRepository, enrolmentRepository);
        enrolment = new EnrolmentEntity();
        enrolment.setId(UUID.randomUUID());
        enrolment.setTenantId(TENANT);
        enrolment.setCourseId(COURSE);
        enrolment.setSubjectType("PROVIDER");
        enrolment.setSubjectId("provider-1");
        enrolment.setStatus("IN_PROGRESS");
    }

    // ── no rules → legacy behaviour flag ─────────────────────────────────────

    @Test
    void noRules_reportsLegacyBehaviour() {
        stubRules();

        FundoCompletionPolicyService.PolicyOutcome outcome = policy.evaluate(TENANT, enrolment);

        assertThat(outcome.hasRules()).isFalse();
        assertThat(outcome.complete()).isFalse();
        assertThat(outcome.satisfied()).isEmpty();
        assertThat(outcome.missing()).isEmpty();
    }

    // ── ATTENDANCE_THRESHOLD (threshold = MINUTES) ───────────────────────────

    @Test
    void attendanceThreshold_passesWhenWatchMinutesMeetThreshold() {
        stubRules(rule("ATTENDANCE_THRESHOLD", new BigDecimal("30"), true));
        stubLiveSessionWithAttendance(45);

        FundoCompletionPolicyService.PolicyOutcome outcome = policy.evaluate(TENANT, enrolment);

        assertThat(outcome.complete()).isTrue();
        assertThat(outcome.satisfied()).containsExactly("ATTENDANCE_THRESHOLD");
    }

    @Test
    void attendanceThreshold_failsWhenBelowThreshold() {
        stubRules(rule("ATTENDANCE_THRESHOLD", new BigDecimal("30"), true));
        stubLiveSessionWithAttendance(10);

        FundoCompletionPolicyService.PolicyOutcome outcome = policy.evaluate(TENANT, enrolment);

        assertThat(outcome.complete()).isFalse();
        assertThat(outcome.missing()).containsExactly("ATTENDANCE_THRESHOLD");
    }

    @Test
    void attendanceThreshold_nullThresholdAcceptsAnyPresence() {
        stubRules(rule("ATTENDANCE_THRESHOLD", null, true));
        stubLiveSessionWithAttendance(null); // PRESENT row with no minutes

        assertThat(policy.evaluate(TENANT, enrolment).complete()).isTrue();
    }

    @Test
    void attendanceThreshold_failsWithoutAnyLiveSessionAttendance() {
        stubRules(rule("ATTENDANCE_THRESHOLD", new BigDecimal("30"), true));
        when(sessionRepository.findByTenantIdAndCourseId(TENANT, COURSE)).thenReturn(List.of());

        assertThat(policy.evaluate(TENANT, enrolment).complete()).isFalse();
    }

    // ── QUIZ_REQUIRED / ASSESSMENT_PASS ──────────────────────────────────────

    @Test
    void quizRequired_passesOnPassedQuizAttempt() {
        stubRules(rule("QUIZ_REQUIRED", null, true));
        stubAssessment("QUIZ", attempt(true, null));

        assertThat(policy.evaluate(TENANT, enrolment).complete()).isTrue();
    }

    @Test
    void quizRequired_acceptsManualReviewPass() {
        stubRules(rule("QUIZ_REQUIRED", null, true));
        stubAssessment("QUIZ", attempt(null, true));

        assertThat(policy.evaluate(TENANT, enrolment).complete()).isTrue();
    }

    @Test
    void quizRequired_failsWithoutPassedAttempt() {
        stubRules(rule("QUIZ_REQUIRED", null, true));
        stubAssessment("QUIZ", attempt(false, null));

        FundoCompletionPolicyService.PolicyOutcome outcome = policy.evaluate(TENANT, enrolment);
        assertThat(outcome.complete()).isFalse();
        assertThat(outcome.missing()).containsExactly("QUIZ_REQUIRED");
    }

    @Test
    void assessmentPass_matchesFinalAssessmentNotQuiz() {
        stubRules(rule("ASSESSMENT_PASS", null, true));
        // Passed QUIZ must NOT satisfy ASSESSMENT_PASS.
        stubAssessment("QUIZ", attempt(true, null));

        assertThat(policy.evaluate(TENANT, enrolment).complete()).isFalse();

        stubAssessment("FINAL_ASSESSMENT", attempt(true, null));
        assertThat(policy.evaluate(TENANT, enrolment).complete()).isTrue();
    }

    // ── FACILITATOR_CONFIRM ──────────────────────────────────────────────────

    @Test
    void facilitatorConfirm_requiresConfirmationMarker() {
        stubRules(rule("FACILITATOR_CONFIRM", null, true));

        assertThat(policy.evaluate(TENANT, enrolment).complete()).isFalse();

        enrolment.setFacilitatorConfirmedAt(OffsetDateTime.now());
        assertThat(policy.evaluate(TENANT, enrolment).complete()).isTrue();
    }

    // ── LESSON_PROGRESS ──────────────────────────────────────────────────────

    @Test
    void lessonProgress_requiresAllRequiredLessonsCompleted() {
        stubRules(rule("LESSON_PROGRESS", null, true));
        CourseModuleEntity module = new CourseModuleEntity();
        module.setId(UUID.randomUUID());
        CourseLessonEntity lesson = new CourseLessonEntity();
        lesson.setId(UUID.randomUUID());
        lesson.setModuleId(module.getId());
        lesson.setRequired(true);
        when(moduleRepository.findByCourseIdOrderBySequenceNoAsc(COURSE)).thenReturn(List.of(module));
        when(lessonRepository.findByModuleIdInOrderBySequenceNoAsc(List.of(module.getId())))
                .thenReturn(List.of(lesson));
        when(progressRepository.findByEnrolmentIdAndLessonId(enrolment.getId(), lesson.getId()))
                .thenReturn(Optional.empty());

        assertThat(policy.evaluate(TENANT, enrolment).complete()).isFalse();

        CourseProgressEntity done = new CourseProgressEntity();
        done.setStatus("COMPLETED");
        when(progressRepository.findByEnrolmentIdAndLessonId(enrolment.getId(), lesson.getId()))
                .thenReturn(Optional.of(done));
        assertThat(policy.evaluate(TENANT, enrolment).complete()).isTrue();
    }

    @Test
    void lessonProgress_vacuouslySatisfiedWithoutLessons() {
        stubRules(rule("LESSON_PROGRESS", null, true));
        when(moduleRepository.findByCourseIdOrderBySequenceNoAsc(COURSE)).thenReturn(List.of());

        assertThat(policy.evaluate(TENANT, enrolment).complete()).isTrue();
    }

    // ── WATCH_THRESHOLD (honest W4 placeholder) ──────────────────────────────

    @Test
    void watchThreshold_honestlyEvaluatesFalseUntilW4() {
        stubRules(rule("WATCH_THRESHOLD", new BigDecimal("30"), true));

        FundoCompletionPolicyService.PolicyOutcome outcome = policy.evaluate(TENANT, enrolment);

        assertThat(outcome.complete()).isFalse();
        assertThat(outcome.missing()).containsExactly("WATCH_THRESHOLD");
    }

    // ── AND semantics / optional rules ───────────────────────────────────────

    @Test
    void multipleRequiredRules_useAndSemantics() {
        stubRules(
                rule("ATTENDANCE_THRESHOLD", new BigDecimal("30"), true),
                rule("FACILITATOR_CONFIRM", null, true));
        stubLiveSessionWithAttendance(60); // attendance met, confirm missing

        FundoCompletionPolicyService.PolicyOutcome outcome = policy.evaluate(TENANT, enrolment);

        assertThat(outcome.complete()).isFalse();
        assertThat(outcome.satisfied()).containsExactly("ATTENDANCE_THRESHOLD");
        assertThat(outcome.missing()).containsExactly("FACILITATOR_CONFIRM");

        enrolment.setFacilitatorConfirmedAt(OffsetDateTime.now());
        assertThat(policy.evaluate(TENANT, enrolment).complete()).isTrue();
    }

    @Test
    void optionalRuleNeverBlocksCompletion() {
        stubRules(
                rule("FACILITATOR_CONFIRM", null, true),
                rule("QUIZ_REQUIRED", null, false)); // optional, unmet
        enrolment.setFacilitatorConfirmedAt(OffsetDateTime.now());
        when(assessmentRepository.findByTenantIdAndCourseId(TENANT, COURSE)).thenReturn(List.of());

        FundoCompletionPolicyService.PolicyOutcome outcome = policy.evaluate(TENANT, enrolment);

        assertThat(outcome.complete()).isTrue();
        assertThat(outcome.missing()).containsExactly("QUIZ_REQUIRED");
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private void stubRules(CompletionRuleEntity... rules) {
        when(ruleRepository.findByTenantIdAndCourseIdOrderByCreatedAtAsc(TENANT, COURSE))
                .thenReturn(List.of(rules));
    }

    private static CompletionRuleEntity rule(String type, BigDecimal threshold, boolean required) {
        CompletionRuleEntity r = new CompletionRuleEntity();
        r.setId(UUID.randomUUID());
        r.setTenantId(TENANT);
        r.setCourseId(COURSE);
        r.setRuleType(type);
        r.setThresholdValue(threshold);
        r.setRequired(required);
        return r;
    }

    private void stubLiveSessionWithAttendance(Integer watchMinutes) {
        ScheduledLearningSessionEntity session = new ScheduledLearningSessionEntity();
        session.setId(UUID.randomUUID());
        session.setTenantId(TENANT);
        session.setCourseId(COURSE);
        session.setSessionMode("LIVE");
        session.setLiveEventId(UUID.randomUUID());
        when(sessionRepository.findByTenantIdAndCourseId(TENANT, COURSE)).thenReturn(List.of(session));

        SessionAttendanceEntity attendance = new SessionAttendanceEntity();
        attendance.setSessionId(session.getId());
        attendance.setSubjectType("PROVIDER");
        attendance.setSubjectId(enrolment.getSubjectId());
        attendance.setStatus("PRESENT");
        attendance.setWatchMinutes(watchMinutes);
        lenient().when(attendanceRepository.findBySessionIdInAndSubjectId(anyList(), eq(enrolment.getSubjectId())))
                .thenReturn(List.of(attendance));
    }

    private void stubAssessment(String type, AssessmentAttemptEntity attempt) {
        AssessmentEntity assessment = new AssessmentEntity();
        assessment.setId(UUID.randomUUID());
        assessment.setTenantId(TENANT);
        assessment.setCourseId(COURSE);
        assessment.setAssessmentType(type);
        lenient().when(assessmentRepository.findByTenantIdAndCourseId(TENANT, COURSE))
                .thenReturn(List.of(assessment));
        lenient().when(attemptRepository.findByAssessmentIdAndSubjectTypeAndSubjectIdOrderByAttemptNoDesc(
                        eq(assessment.getId()), anyString(), anyString()))
                .thenReturn(List.of(attempt));
    }

    private static AssessmentAttemptEntity attempt(Boolean passed, Boolean manualReviewPassed) {
        AssessmentAttemptEntity a = new AssessmentAttemptEntity();
        a.setId(UUID.randomUUID());
        a.setPassed(passed);
        a.setManualReviewPassed(manualReviewPassed);
        return a;
    }
}
