package zw.gov.mohcc.impilo.learning.fundo;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.learning.persistence.entity.EnrolmentEntity;
import zw.gov.mohcc.impilo.learning.persistence.entity.ScheduledLearningSessionEntity;
import zw.gov.mohcc.impilo.learning.persistence.entity.SessionAttendanceEntity;
import zw.gov.mohcc.impilo.learning.persistence.repository.EnrolmentRepository;
import zw.gov.mohcc.impilo.learning.persistence.repository.SessionAttendanceRepository;

/**
 * Records Impilo Live event attendance as Fundo course completion for CPD-linked
 * webinars.
 *
 * <p>W3 refactor: completion is no longer blind. {@link #recordLiveCompletion}
 * still records 100 % progress, but the COMPLETED transition (and the
 * certificate) is gated by {@link FundoCompletionPolicyService} when the course
 * has configured completion rules — courses without rules keep the legacy
 * webhook behaviour unchanged. {@link #reconcileSessionCompletion} is the
 * event-driven counterpart invoked when {@code impilo.live.event.ended.v1}
 * arrives: it evaluates every attendee's enrolment and completes only those
 * whose rules pass.</p>
 */
@Service
public class FundoLiveCompletionService {

    private static final Logger log = LoggerFactory.getLogger(FundoLiveCompletionService.class);
    private static final List<String> ACTIVE_STATUSES = List.of("ENROLLED", "IN_PROGRESS");

    private final FundoEnrolmentService enrolmentService;
    private final FundoProgressService progressService;
    private final FundoCertificateService certificateService;
    private final FundoCompletionPolicyService completionPolicy;
    private final EnrolmentRepository enrolmentRepository;
    private final SessionAttendanceRepository attendanceRepository;

    public FundoLiveCompletionService(
            FundoEnrolmentService enrolmentService,
            FundoProgressService progressService,
            FundoCertificateService certificateService,
            FundoCompletionPolicyService completionPolicy,
            EnrolmentRepository enrolmentRepository,
            SessionAttendanceRepository attendanceRepository) {
        this.enrolmentService = enrolmentService;
        this.progressService = progressService;
        this.certificateService = certificateService;
        this.completionPolicy = completionPolicy;
        this.enrolmentRepository = enrolmentRepository;
        this.attendanceRepository = attendanceRepository;
    }

    @Transactional
    public Map<String, Object> recordLiveCompletion(UUID tenantId, Map<String, Object> body) {
        UUID courseId = parseUuid(body.get("courseId"));
        String subjectType = stringVal(body, "subjectType", "PROVIDER");
        String subjectId = stringVal(body, "subjectId", null);
        if (courseId == null || subjectId == null || subjectId.isBlank()) {
            throw new IllegalArgumentException("courseId and subjectId are required");
        }

        Map<String, Object> enrolment = enrolmentService.create(
                new FundoEnrolmentService.EnrolmentRequest(
                        tenantId, subjectType, subjectId, courseId, null,
                        "LIVE_EVENT", "impilo-live", null));
        UUID enrolmentId = UUID.fromString(enrolment.get("id").toString());

        enrolmentService.start(tenantId, enrolmentId);

        // Records the course-aggregate 100 % progress row; the enrolment COMPLETED
        // transition inside recordProgress is policy-gated (W3).
        progressService.recordProgress(tenantId,
                new FundoProgressService.ProgressUpdate(enrolmentId, null, null, 100, null));

        EnrolmentEntity entity = enrolmentRepository.findByTenantIdAndId(tenantId, enrolmentId)
                .orElseThrow(() -> new IllegalStateException("enrolment_not_found"));
        FundoCompletionPolicyService.PolicyOutcome outcome = completionPolicy.evaluate(tenantId, entity);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("enrolmentId", enrolmentId.toString());
        result.put("courseId", courseId.toString());
        result.put("subjectId", subjectId);
        result.put("liveEventId", stringVal(body, "liveEventId", null));
        result.put("source", stringVal(body, "source", "IMPILO_LIVE"));
        if (body.get("cpdPoints") != null) {
            result.put("cpdPoints", body.get("cpdPoints"));
        }
        if (outcome.hasRules()) {
            result.put("completionPolicy", outcome.toPayload());
        }

        if (!"COMPLETED".equals(entity.getStatus())) {
            // Course has rules that the live completion alone does not satisfy —
            // honest pending state instead of a blind certificate.
            result.put("completed", false);
            result.put("certificateIssued", false);
            result.put("certificateNote", "completion_rules_not_met");
            return result;
        }
        result.put("completed", true);

        try {
            FundoCertificateService.CertificateIssueResult cert =
                    certificateService.issueForEnrolment(tenantId, enrolmentId);
            result.put("certificate", cert.view());
            result.put("certificateIssued", true);
            result.put("certificateIdempotent", cert.idempotent());
        } catch (IllegalStateException ex) {
            result.put("certificateIssued", false);
            result.put("certificateNote", ex.getMessage());
        }

        return result;
    }

    /**
     * Event-driven completion pass for a live-linked scheduled session, invoked when
     * {@code impilo.live.event.ended.v1} arrives (LiveAttendanceConsumer). For each
     * attendance row of the session, resolves the enrolment and completes it — with
     * a certificate — only when the course HAS completion rules and they all pass.
     * Courses without rules are left to the explicit live-completion webhook (legacy
     * path); mere attendance never blindly completes them. Idempotent: enrolments
     * already COMPLETED are skipped and certificate issuance is idempotent.
     */
    @Transactional
    public Map<String, Object> reconcileSessionCompletion(UUID tenantId, ScheduledLearningSessionEntity session) {
        List<SessionAttendanceEntity> attendance = attendanceRepository.findBySessionId(session.getId());
        OffsetDateTime now = OffsetDateTime.now();
        int evaluated = 0;
        int completed = 0;
        int certificates = 0;
        for (SessionAttendanceEntity row : attendance) {
            EnrolmentEntity enrolment = resolveEnrolment(tenantId, session.getCourseId(), row);
            if (enrolment == null || "COMPLETED".equals(enrolment.getStatus())) {
                continue;
            }
            evaluated++;
            FundoCompletionPolicyService.PolicyOutcome outcome = completionPolicy.evaluate(tenantId, enrolment);
            if (!outcome.hasRules() || !outcome.complete()) {
                continue;
            }
            if (progressService.completeIfEligible(tenantId, enrolment, now, "LIVE_EVENT_ENDED")) {
                completed++;
                try {
                    certificateService.issueForEnrolment(tenantId, enrolment.getId());
                    certificates++;
                } catch (IllegalStateException ex) {
                    log.warn("Certificate not issued for enrolment {} after live event end: {}",
                            enrolment.getId(), ex.getMessage());
                }
            }
        }
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("sessionId", session.getId().toString());
        summary.put("courseId", session.getCourseId().toString());
        summary.put("attendanceRows", attendance.size());
        summary.put("evaluated", evaluated);
        summary.put("completed", completed);
        summary.put("certificatesIssued", certificates);
        return summary;
    }

    private EnrolmentEntity resolveEnrolment(UUID tenantId, UUID courseId, SessionAttendanceEntity row) {
        if (row.getEnrolmentId() != null) {
            EnrolmentEntity byId = enrolmentRepository.findByTenantIdAndId(tenantId, row.getEnrolmentId())
                    .orElse(null);
            if (byId != null) {
                return byId;
            }
        }
        return enrolmentRepository
                .findFirstByTenantIdAndSubjectTypeAndSubjectIdAndCourseIdAndStatusIn(
                        tenantId, row.getSubjectType(), row.getSubjectId(), courseId, ACTIVE_STATUSES)
                .orElse(null);
    }

    private static UUID parseUuid(Object raw) {
        if (raw == null) return null;
        try {
            return UUID.fromString(raw.toString());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static String stringVal(Map<String, Object> body, String key, String fallback) {
        Object v = body.get(key);
        return v == null ? fallback : v.toString();
    }
}
