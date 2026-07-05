package zw.gov.mohcc.impilo.learning.fundo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.learning.persistence.entity.EnrolmentEntity;
import zw.gov.mohcc.impilo.learning.persistence.entity.ScheduledLearningSessionEntity;
import zw.gov.mohcc.impilo.learning.persistence.entity.SessionAttendanceEntity;
import zw.gov.mohcc.impilo.learning.persistence.repository.EnrolmentRepository;
import zw.gov.mohcc.impilo.learning.persistence.repository.SessionAttendanceRepository;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FundoLiveCompletionServiceTest {

    @Mock
    private FundoEnrolmentService enrolmentService;
    @Mock
    private FundoProgressService progressService;
    @Mock
    private FundoCertificateService certificateService;
    @Mock
    private FundoCompletionPolicyService completionPolicy;
    @Mock
    private EnrolmentRepository enrolmentRepository;
    @Mock
    private SessionAttendanceRepository attendanceRepository;

    private FundoLiveCompletionService service;
    private final UUID tenantId = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @BeforeEach
    void setUp() {
        service = new FundoLiveCompletionService(enrolmentService, progressService, certificateService,
                completionPolicy, enrolmentRepository, attendanceRepository);
    }

    @Test
    void recordLiveCompletionEnrolsProgressAndIssuesCertificate() {
        UUID courseId = UUID.randomUUID();
        UUID enrolmentId = UUID.randomUUID();
        Map<String, Object> enrolment = Map.of("id", enrolmentId.toString());

        when(enrolmentService.create(any(FundoEnrolmentService.EnrolmentRequest.class))).thenReturn(enrolment);
        // No rules: recordProgress's legacy path completed the enrolment.
        when(enrolmentRepository.findByTenantIdAndId(tenantId, enrolmentId))
                .thenReturn(Optional.of(enrolment(enrolmentId, courseId, "COMPLETED")));
        when(completionPolicy.evaluate(eq(tenantId), any()))
                .thenReturn(new FundoCompletionPolicyService.PolicyOutcome(false, false, List.of(), List.of()));
        when(certificateService.issueForEnrolment(eq(tenantId), eq(enrolmentId)))
                .thenReturn(new FundoCertificateService.CertificateIssueResult(
                        Map.of("id", "cert-1"), false));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("courseId", courseId.toString());
        body.put("subjectId", "provider-42");
        body.put("liveEventId", UUID.randomUUID().toString());
        body.put("cpdPoints", 2);

        Map<String, Object> result = service.recordLiveCompletion(tenantId, body);

        assertThat(result.get("enrolmentId")).isEqualTo(enrolmentId.toString());
        assertThat(result.get("completed")).isEqualTo(true);
        assertThat(result.get("certificateIssued")).isEqualTo(true);
        assertThat(result.get("courseId")).isEqualTo(courseId.toString());
    }

    @Test
    void recordLiveCompletionWithUnmetRulesStaysPendingWithoutCertificate() {
        UUID courseId = UUID.randomUUID();
        UUID enrolmentId = UUID.randomUUID();
        when(enrolmentService.create(any(FundoEnrolmentService.EnrolmentRequest.class)))
                .thenReturn(Map.of("id", enrolmentId.toString()));
        // Rules exist and are not satisfied — recordProgress leaves it IN_PROGRESS.
        when(enrolmentRepository.findByTenantIdAndId(tenantId, enrolmentId))
                .thenReturn(Optional.of(enrolment(enrolmentId, courseId, "IN_PROGRESS")));
        when(completionPolicy.evaluate(eq(tenantId), any()))
                .thenReturn(new FundoCompletionPolicyService.PolicyOutcome(
                        true, false, List.of(), List.of("QUIZ_REQUIRED")));

        Map<String, Object> result = service.recordLiveCompletion(tenantId, Map.of(
                "courseId", courseId.toString(),
                "subjectId", "provider-42"));

        assertThat(result.get("completed")).isEqualTo(false);
        assertThat(result.get("certificateIssued")).isEqualTo(false);
        assertThat(result.get("certificateNote")).isEqualTo("completion_rules_not_met");
        assertThat(result.get("completionPolicy")).isNotNull();
        verify(certificateService, never()).issueForEnrolment(any(), any());
    }

    @Test
    void recordLiveCompletionRequiresCourseAndSubject() {
        assertThatThrownBy(() -> service.recordLiveCompletion(tenantId, Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("courseId and subjectId are required");
    }

    // ── reconcileSessionCompletion (event.ended pass) ────────────────────────

    @Test
    void reconcileCompletesOnlyEnrolmentsWhoseRulesPass() {
        UUID courseId = UUID.randomUUID();
        ScheduledLearningSessionEntity session = session(courseId);

        EnrolmentEntity passing = enrolment(UUID.randomUUID(), courseId, "IN_PROGRESS");
        EnrolmentEntity failing = enrolment(UUID.randomUUID(), courseId, "IN_PROGRESS");
        SessionAttendanceEntity aPass = attendanceRow(session, "subj-pass", passing.getId());
        SessionAttendanceEntity aFail = attendanceRow(session, "subj-fail", failing.getId());

        when(attendanceRepository.findBySessionId(session.getId())).thenReturn(List.of(aPass, aFail));
        when(enrolmentRepository.findByTenantIdAndId(tenantId, passing.getId())).thenReturn(Optional.of(passing));
        when(enrolmentRepository.findByTenantIdAndId(tenantId, failing.getId())).thenReturn(Optional.of(failing));
        when(completionPolicy.evaluate(tenantId, passing)).thenReturn(
                new FundoCompletionPolicyService.PolicyOutcome(true, true, List.of("ATTENDANCE_THRESHOLD"), List.of()));
        when(completionPolicy.evaluate(tenantId, failing)).thenReturn(
                new FundoCompletionPolicyService.PolicyOutcome(true, false, List.of(), List.of("ATTENDANCE_THRESHOLD")));
        when(progressService.completeIfEligible(eq(tenantId), eq(passing), any(), anyString())).thenReturn(true);
        when(certificateService.issueForEnrolment(tenantId, passing.getId()))
                .thenReturn(new FundoCertificateService.CertificateIssueResult(Map.of("id", "cert-1"), false));

        Map<String, Object> summary = service.reconcileSessionCompletion(tenantId, session);

        assertThat(summary.get("completed")).isEqualTo(1);
        assertThat(summary.get("certificatesIssued")).isEqualTo(1);
        verify(progressService, never()).completeIfEligible(eq(tenantId), eq(failing), any(), anyString());
    }

    @Test
    void reconcileNeverCompletesRulelessCoursesOrCompletedEnrolments() {
        UUID courseId = UUID.randomUUID();
        ScheduledLearningSessionEntity session = session(courseId);

        EnrolmentEntity ruleless = enrolment(UUID.randomUUID(), courseId, "IN_PROGRESS");
        EnrolmentEntity done = enrolment(UUID.randomUUID(), courseId, "COMPLETED");
        when(attendanceRepository.findBySessionId(session.getId())).thenReturn(List.of(
                attendanceRow(session, "subj-1", ruleless.getId()),
                attendanceRow(session, "subj-2", done.getId())));
        when(enrolmentRepository.findByTenantIdAndId(tenantId, ruleless.getId())).thenReturn(Optional.of(ruleless));
        when(enrolmentRepository.findByTenantIdAndId(tenantId, done.getId())).thenReturn(Optional.of(done));
        when(completionPolicy.evaluate(tenantId, ruleless)).thenReturn(
                new FundoCompletionPolicyService.PolicyOutcome(false, false, List.of(), List.of()));

        Map<String, Object> summary = service.reconcileSessionCompletion(tenantId, session);

        assertThat(summary.get("completed")).isEqualTo(0);
        verify(progressService, never()).completeIfEligible(any(), any(), any(), anyString());
        verify(certificateService, never()).issueForEnrolment(any(), any());
    }

    private ScheduledLearningSessionEntity session(UUID courseId) {
        ScheduledLearningSessionEntity s = new ScheduledLearningSessionEntity();
        s.setId(UUID.randomUUID());
        s.setTenantId(tenantId);
        s.setCourseId(courseId);
        s.setSessionMode("LIVE");
        s.setLiveEventId(UUID.randomUUID());
        return s;
    }

    private EnrolmentEntity enrolment(UUID id, UUID courseId, String status) {
        EnrolmentEntity e = new EnrolmentEntity();
        e.setId(id);
        e.setTenantId(tenantId);
        e.setCourseId(courseId);
        e.setSubjectType("PROVIDER");
        e.setSubjectId("subj-" + id);
        e.setStatus(status);
        return e;
    }

    private SessionAttendanceEntity attendanceRow(ScheduledLearningSessionEntity session,
                                                  String subjectId, UUID enrolmentId) {
        SessionAttendanceEntity a = new SessionAttendanceEntity();
        a.setId(UUID.randomUUID());
        a.setTenantId(tenantId);
        a.setSessionId(session.getId());
        a.setSubjectType("PROVIDER");
        a.setSubjectId(subjectId);
        a.setEnrolmentId(enrolmentId);
        a.setStatus("PRESENT");
        return a;
    }
}
