package zw.gov.mohcc.impilo.learning.api.fundo;

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
 * Native Fundo enrolment surface. Idempotent creation collapses duplicate
 * active enrolments for the same tenant, subject and course into the existing row.
 */
@RestController
@RequestMapping("/internal/v1/learning/fundo")
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
        UUID tenantId = FundoApiSupport.requireTenantOrNull(ctx);
        if (tenantId == null || body == null) {
            return FundoApiSupport.badRequest("INVALID_INPUT",
                    tenantId == null ? "Tenant header is not a valid UUID" : "Request body is required");
        }
        UUID courseId = FundoApiSupport.tryParseUuid(FundoApiSupport.asString(body.get("courseId")));
        String subjectType = FundoApiSupport.asString(body.get("subjectType"));
        String subjectId = FundoApiSupport.asString(body.get("subjectId"));
        if (courseId == null || subjectType == null || subjectId == null) {
            return FundoApiSupport.badRequest("INVALID_INPUT",
                    "courseId, subjectType and subjectId are required");
        }
        UUID pathwayId = FundoApiSupport.tryParseUuid(FundoApiSupport.asString(body.get("pathwayId")));
        String enrolmentType = FundoApiSupport.asString(body.get("enrolmentType"));
        String assignedBy = FundoApiSupport.asString(body.get("assignedBy"));
        OffsetDateTime dueAt = null;
        String dueAtStr = FundoApiSupport.asString(body.get("dueAt"));
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
            return FundoApiSupport.dataEnvelope("enrolment", view);
        } catch (IllegalArgumentException ex) {
            return FundoApiSupport.notFound("COURSE_NOT_FOUND", ex.getMessage());
        }
    }

    @GetMapping("/enrolments")
    public ResponseEntity<Map<String, Object>> listEnrolments(
            @RequestParam(required = false) String subjectType,
            @RequestParam(required = false) String subjectId,
            @RequestParam(defaultValue = "25") int limit) {
        RequestContext ctx = RequestContextHolder.require();
        UUID tenantId = FundoApiSupport.requireTenantOrNull(ctx);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("subjectType", subjectType);
        data.put("subjectId", subjectId);
        data.put("limit", limit);
        if (tenantId == null || subjectType == null || subjectId == null) {
            data.put("items", List.of());
            return FundoApiSupport.dataEnvelope(data);
        }
        data.put("items", enrolmentService.listForSubject(tenantId, subjectType, subjectId, limit));
        return FundoApiSupport.dataEnvelope(data);
    }

    @GetMapping("/enrolments/{enrolmentId}")
    public ResponseEntity<Map<String, Object>> getEnrolment(@PathVariable String enrolmentId) {
        RequestContext ctx = RequestContextHolder.require();
        UUID tenantId = FundoApiSupport.requireTenantOrNull(ctx);
        UUID eid = FundoApiSupport.tryParseUuid(enrolmentId);
        if (tenantId == null || eid == null) {
            return FundoApiSupport.notFound("ENROLMENT_NOT_FOUND", "Enrolment not found");
        }
        return enrolmentService.get(tenantId, eid)
                .map(v -> FundoApiSupport.dataEnvelope("enrolment", v))
                .orElseGet(() -> FundoApiSupport.notFound("ENROLMENT_NOT_FOUND", "Enrolment not found"));
    }

    @PostMapping("/enrolments/{enrolmentId}/cancel")
    public ResponseEntity<Map<String, Object>> cancel(
            @PathVariable String enrolmentId,
            @RequestBody(required = false) Map<String, Object> body) {
        RequestContext ctx = RequestContextHolder.require();
        UUID tenantId = FundoApiSupport.requireTenantOrNull(ctx);
        UUID eid = FundoApiSupport.tryParseUuid(enrolmentId);
        if (tenantId == null || eid == null) {
            return FundoApiSupport.notFound("ENROLMENT_NOT_FOUND", "Enrolment not found");
        }
        String reason = body == null ? null : FundoApiSupport.asString(body.get("reason"));
        return enrolmentService.cancel(tenantId, eid, reason)
                .map(v -> FundoApiSupport.dataEnvelope("enrolment", v))
                .orElseGet(() -> FundoApiSupport.notFound("ENROLMENT_NOT_FOUND", "Enrolment not found"));
    }

    @PostMapping("/enrolments/{enrolmentId}/start")
    public ResponseEntity<Map<String, Object>> start(@PathVariable String enrolmentId) {
        RequestContext ctx = RequestContextHolder.require();
        UUID tenantId = FundoApiSupport.requireTenantOrNull(ctx);
        UUID eid = FundoApiSupport.tryParseUuid(enrolmentId);
        if (tenantId == null || eid == null) {
            return FundoApiSupport.notFound("ENROLMENT_NOT_FOUND", "Enrolment not found");
        }
        return enrolmentService.start(tenantId, eid)
                .map(v -> FundoApiSupport.dataEnvelope("enrolment", v))
                .orElseGet(() -> FundoApiSupport.notFound("ENROLMENT_NOT_FOUND", "Enrolment not found"));
    }

    @GetMapping("/enrolments/{enrolmentId}/progress")
    public ResponseEntity<Map<String, Object>> progressByEnrolment(@PathVariable String enrolmentId) {
        RequestContext ctx = RequestContextHolder.require();
        UUID tenantId = FundoApiSupport.requireTenantOrNull(ctx);
        UUID eid = FundoApiSupport.tryParseUuid(enrolmentId);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("enrolmentId", enrolmentId);
        if (tenantId == null || eid == null) {
            data.put("items", List.of());
            return FundoApiSupport.dataEnvelope(data);
        }
        data.put("items", progressService.getByEnrolment(tenantId, eid));
        return FundoApiSupport.dataEnvelope(data);
    }

    @PostMapping("/enrolments/{enrolmentId}/certificate")
    public ResponseEntity<Map<String, Object>> issueCertificate(@PathVariable String enrolmentId) {
        RequestContext ctx = RequestContextHolder.require();
        UUID tenantId = FundoApiSupport.requireTenantOrNull(ctx);
        UUID eid = FundoApiSupport.tryParseUuid(enrolmentId);
        if (tenantId == null || eid == null) {
            return FundoApiSupport.notFound("ENROLMENT_NOT_FOUND", "Enrolment not found");
        }
        try {
            FundoCertificateService.CertificateIssueResult result =
                    certificateService.issueForEnrolment(tenantId, eid);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("certificate", result.view());
            data.put("idempotent", result.idempotent());
            return FundoApiSupport.dataEnvelope(data);
        } catch (IllegalArgumentException ex) {
            return FundoApiSupport.notFound("ENROLMENT_NOT_FOUND", ex.getMessage());
        } catch (IllegalStateException ex) {
            return FundoApiSupport.conflict("ENROLMENT_NOT_COMPLETED",
                    "Certificate may only be issued for COMPLETED enrolments");
        }
    }
}
