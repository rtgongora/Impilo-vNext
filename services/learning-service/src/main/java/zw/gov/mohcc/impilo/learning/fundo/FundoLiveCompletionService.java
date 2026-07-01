package zw.gov.mohcc.impilo.learning.fundo;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records Impilo Live event attendance as Fundo course completion for CPD-linked webinars.
 */
@Service
public class FundoLiveCompletionService {

    private final FundoEnrolmentService enrolmentService;
    private final FundoProgressService progressService;
    private final FundoCertificateService certificateService;

    public FundoLiveCompletionService(
            FundoEnrolmentService enrolmentService,
            FundoProgressService progressService,
            FundoCertificateService certificateService) {
        this.enrolmentService = enrolmentService;
        this.progressService = progressService;
        this.certificateService = certificateService;
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

        progressService.recordProgress(tenantId,
                new FundoProgressService.ProgressUpdate(enrolmentId, null, null, 100, null));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("enrolmentId", enrolmentId.toString());
        result.put("courseId", courseId.toString());
        result.put("subjectId", subjectId);
        result.put("liveEventId", stringVal(body, "liveEventId", null));
        result.put("source", stringVal(body, "source", "IMPILO_LIVE"));
        if (body.get("cpdPoints") != null) {
            result.put("cpdPoints", body.get("cpdPoints"));
        }

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
