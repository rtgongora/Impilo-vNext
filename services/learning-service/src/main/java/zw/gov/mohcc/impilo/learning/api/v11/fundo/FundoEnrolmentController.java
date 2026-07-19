package zw.gov.mohcc.impilo.learning.api.v11.fundo;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.companion.context.RequestContext;
import zw.gov.mohcc.impilo.companion.context.RequestContextHolder;
import zw.gov.mohcc.impilo.learning.fundo.FundoCertificateService;
import zw.gov.mohcc.impilo.learning.fundo.FundoEnrolmentService;
import zw.gov.mohcc.impilo.learning.fundo.FundoProgressService;

/**
 * Native Fundo v1.1 enrolment surface (Phase 5B). Idempotent enrolment
 * creation: the underlying service collapses duplicate {@code ENROLLED} /
 * {@code IN_PROGRESS} (tenant, subject, course) into the existing row.
 */
@RestController
@RequestMapping("/internal/v1/learning/v11")
public class FundoEnrolmentController {

    private final FundoEnrolmentService enrolmentService;
    private final FundoProgressService progressService;
    private final FundoCertificateService certificateService;

    public FundoEnrolmentController(
            FundoEnrolmentService enrolmentService,
            FundoProgressService progressService,
            FundoCertificateService certificateService) {
        this.enrolmentService = enrolmentService;
        this.progressService = progressService;
        this.certificateService = certificateService;
    }

    @PostMapping("/enrolments")
    public ResponseEntity<Map<String, Object>> createEnrolment(
            @RequestBody(required = false) Map<String, Object> body) {
        RequestContext ctx = RequestContextHolder.require();
        UUID tenantId = FundoV11Support.requireTenantOrNull(ctx);
        if (tenantId == null || body == null) {
            return FundoV11Support.badRequest("INVALID_INPUT",
                    tenantId == null ? "Tenant header is not a valid UUID" : "Request body is required");
        }
        UUID courseId = FundoV11Support.tryParseUuid(asString(body.get("courseId")));
        String subjectType = asString(body.get("subjectType"));
        String subjectId = asString(body.get("subjectId"));
        if (courseId == null || subjectType == null || subjectId == null) {
            return FundoV11Support.badRequest("INVALID_INPUT",
                    "courseId, subjectType and subjectId are required");
        }
        if (!FundoEnrolmentService.ALLOWED_SUBJECT_TYPES.contains(subjectType)) {
            return FundoV11Support.badRequest("INVALID_SUBJECT_TYPE",
                    "subjectType must be one of " + FundoEnrolmentService.ALLOWED_SUBJECT_TYPES);
        }
        UUID pathwayId = FundoV11Support.tryParseUuid(asString(body.get("pathwayId")));
        String enrolmentType = asString(body.get("enrolmentType"));
        String assignedBy = asString(body.get("assignedBy"));
        OffsetDateTime dueAt = null;
        String dueAtStr = asString(body.get("dueAt"));
        if (dueAtStr != null && !dueAtStr.isBlank()) {
            try {
                dueAt = OffsetDateTime.parse(dueAtStr);
            } catch (RuntimeException ignored) {
                // ignore unparseable due date
            }
        }
        try {
            Map<String, Object> view = enrolmentService.create(
                    new FundoEnrolmentService.EnrolmentRequest(
                            tenantId, subjectType, subjectId, courseId, pathwayId,
                            enrolmentType, assignedBy, dueAt));
            return FundoV11Support.dataEnvelope("enrolment", view);
        } catch (IllegalArgumentException ex) {
            return FundoV11Support.notFound("COURSE_NOT_FOUND", ex.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    @PostMapping("/enrolments/bulk")
    public ResponseEntity<Map<String, Object>> bulkEnrol(@RequestBody Map<String, Object> body) {
        RequestContext ctx = RequestContextHolder.require();
        UUID tenantId = FundoV11Support.requireTenantOrNull(ctx);
        if (tenantId == null || body == null) {
            return FundoV11Support.badRequest("INVALID_INPUT", "Tenant header + body required");
        }
        UUID courseId = FundoV11Support.tryParseUuid(asString(body.get("courseId")));
        if (courseId == null) {
            return FundoV11Support.badRequest("INVALID_INPUT", "courseId is required");
        }
        List<Map<String, Object>> subjects = (List<Map<String, Object>>) body.getOrDefault("subjects", List.of());
        try {
            return FundoV11Support.dataEnvelope(enrolmentService.bulkEnrol(
                    tenantId, courseId, subjects, asString(body.get("enrolmentType")), asString(body.get("assignedBy"))));
        } catch (IllegalArgumentException ex) {
            return FundoV11Support.notFound("COURSE_NOT_FOUND", ex.getMessage());
        }
    }

    @GetMapping("/enrolments")
    public ResponseEntity<Map<String, Object>> listEnrolments(
            @RequestParam(required = false) String subjectType,
            @RequestParam(required = false) String subjectId,
            @RequestParam(defaultValue = "25") int limit) {
        RequestContext ctx = RequestContextHolder.require();
        UUID tenantId = FundoV11Support.requireTenantOrNull(ctx);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("subjectType", subjectType);
        data.put("subjectId", subjectId);
        data.put("limit", limit);
        if (tenantId == null || subjectType == null || subjectId == null) {
            data.put("items", List.of());
            return FundoV11Support.dataEnvelope(data);
        }
        data.put("items", enrolmentService.listForSubject(tenantId, subjectType, subjectId, limit));
        return FundoV11Support.dataEnvelope(data);
    }

    @GetMapping("/enrolments/{enrolmentId}")
    public ResponseEntity<Map<String, Object>> getEnrolment(@PathVariable String enrolmentId) {
        RequestContext ctx = RequestContextHolder.require();
        UUID tenantId = FundoV11Support.requireTenantOrNull(ctx);
        UUID eid = FundoV11Support.tryParseUuid(enrolmentId);
        if (tenantId == null || eid == null) {
            return FundoV11Support.notFound("ENROLMENT_NOT_FOUND", "Enrolment not found");
        }
        return enrolmentService.get(tenantId, eid)
                .map(v -> FundoV11Support.dataEnvelope("enrolment", v))
                .orElseGet(() -> FundoV11Support.notFound("ENROLMENT_NOT_FOUND", "Enrolment not found"));
    }

    @PostMapping("/enrolments/{enrolmentId}/cancel")
    public ResponseEntity<Map<String, Object>> cancel(
            @PathVariable String enrolmentId,
            @RequestBody(required = false) Map<String, Object> body) {
        RequestContext ctx = RequestContextHolder.require();
        UUID tenantId = FundoV11Support.requireTenantOrNull(ctx);
        UUID eid = FundoV11Support.tryParseUuid(enrolmentId);
        if (tenantId == null || eid == null) {
            return FundoV11Support.notFound("ENROLMENT_NOT_FOUND", "Enrolment not found");
        }
        String reason = body == null ? null : asString(body.get("reason"));
        return enrolmentService.cancel(tenantId, eid, reason)
                .map(v -> FundoV11Support.dataEnvelope("enrolment", v))
                .orElseGet(() -> FundoV11Support.notFound("ENROLMENT_NOT_FOUND", "Enrolment not found"));
    }

    @PostMapping("/enrolments/{enrolmentId}/start")
    public ResponseEntity<Map<String, Object>> start(@PathVariable String enrolmentId) {
        RequestContext ctx = RequestContextHolder.require();
        UUID tenantId = FundoV11Support.requireTenantOrNull(ctx);
        UUID eid = FundoV11Support.tryParseUuid(enrolmentId);
        if (tenantId == null || eid == null) {
            return FundoV11Support.notFound("ENROLMENT_NOT_FOUND", "Enrolment not found");
        }
        return enrolmentService.start(tenantId, eid)
                .map(v -> FundoV11Support.dataEnvelope("enrolment", v))
                .orElseGet(() -> FundoV11Support.notFound("ENROLMENT_NOT_FOUND", "Enrolment not found"));
    }

    @GetMapping("/enrolments/{enrolmentId}/progress")
    public ResponseEntity<Map<String, Object>> progressByEnrolment(@PathVariable String enrolmentId) {
        RequestContext ctx = RequestContextHolder.require();
        UUID tenantId = FundoV11Support.requireTenantOrNull(ctx);
        UUID eid = FundoV11Support.tryParseUuid(enrolmentId);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("enrolmentId", enrolmentId);
        if (tenantId == null || eid == null) {
            data.put("items", List.of());
            return FundoV11Support.dataEnvelope(data);
        }
        data.put("items", progressService.getByEnrolment(tenantId, eid));
        return FundoV11Support.dataEnvelope(data);
    }

    @PostMapping("/enrolments/{enrolmentId}/certificate")
    public ResponseEntity<Map<String, Object>> issueCertificate(@PathVariable String enrolmentId) {
        RequestContext ctx = RequestContextHolder.require();
        UUID tenantId = FundoV11Support.requireTenantOrNull(ctx);
        UUID eid = FundoV11Support.tryParseUuid(enrolmentId);
        if (tenantId == null || eid == null) {
            return FundoV11Support.notFound("ENROLMENT_NOT_FOUND", "Enrolment not found");
        }
        try {
            FundoCertificateService.CertificateIssueResult result =
                    certificateService.issueForEnrolment(tenantId, eid);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("certificate", result.view());
            data.put("idempotent", result.idempotent());
            return FundoV11Support.dataEnvelope(data);
        } catch (IllegalArgumentException ex) {
            return FundoV11Support.notFound("ENROLMENT_NOT_FOUND", ex.getMessage());
        } catch (IllegalStateException ex) {
            return FundoV11Support.conflict("ENROLMENT_NOT_COMPLETED",
                    "Certificate may only be issued for COMPLETED enrolments");
        }
    }

    private static String asString(Object v) { return v == null ? null : v.toString(); }
}
