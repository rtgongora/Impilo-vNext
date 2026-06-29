package zw.gov.mohcc.impilo.patientsafety.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.patientsafety.api.dto.AddAttachmentRequest;
import zw.gov.mohcc.impilo.patientsafety.api.dto.AddEventRequest;
import zw.gov.mohcc.impilo.patientsafety.api.dto.AddProductRequest;
import zw.gov.mohcc.impilo.patientsafety.api.dto.ApiResponse;
import zw.gov.mohcc.impilo.patientsafety.api.dto.CreateReportRequest;
import zw.gov.mohcc.impilo.patientsafety.api.dto.ReportResponse;
import zw.gov.mohcc.impilo.patientsafety.api.dto.ReportSummary;
import zw.gov.mohcc.impilo.patientsafety.api.dto.UpdateReportRequest;
import zw.gov.mohcc.impilo.patientsafety.core.SafetyReportService;
import zw.gov.mohcc.impilo.shared.visibility.JsonRepresentationShaper;
import zw.gov.mohcc.impilo.shared.visibility.VisibilityContextHolder;
import zw.gov.mohcc.impilo.tshepo.contracts.dto.VisibilityProfile;

import java.util.List;
import java.util.UUID;

/**
 * Safety report intake API. Reports may originate from a clinical encounter (ADR), a vaccination
 * (AEFI) or a citizen/caregiver self-service flow; all paths share this internal surface (the BFF
 * exposes citizen-facing variants). Mutations honour {@code Idempotency-Key}.
 */
@RestController
@RequestMapping("/internal/v1/patient-safety/reports")
public class SafetyReportController {

    private final SafetyReportService reports;
    private final IdempotencySupport idempotency;
    private final ObjectMapper objectMapper;

    public SafetyReportController(SafetyReportService reports, IdempotencySupport idempotency,
                                  ObjectMapper objectMapper) {
        this.reports = reports;
        this.idempotency = idempotency;
        this.objectMapper = objectMapper;
    }

    /**
     * Applies the PDP's visibility obligation (from {@link VisibilityContextHolder}) to a response
     * body — suppressing/pseudonymising fields and masking PII per {@code suppressFields}/{@code piiAccess}.
     * When no obligation is present the body is returned unchanged. Fails closed: if shaping throws under
     * an active profile we 500 rather than leak unshaped PHI.
     */
    private Object shaped(Object dto) {
        VisibilityProfile profile = VisibilityContextHolder.current().orElse(null);
        if (profile == null) {
            return dto;
        }
        try {
            return JsonRepresentationShaper.shape(objectMapper, dto, profile);
        } catch (Exception e) {
            throw new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR, "visibility shaping failed");
        }
    }

    /** Row-wise visibility shaping for a list response (each summary is shaped as its own object). */
    private Object shapedList(List<?> rows) {
        VisibilityProfile profile = VisibilityContextHolder.current().orElse(null);
        if (profile == null) {
            return rows;
        }
        ArrayNode out = objectMapper.createArrayNode();
        try {
            for (Object row : rows) {
                out.add(JsonRepresentationShaper.shape(objectMapper, row, profile));
            }
        } catch (Exception e) {
            throw new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR, "visibility shaping failed");
        }
        return out;
    }

    @PostMapping
    public ResponseEntity<?> create(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestHeader(value = "X-Pod-ID", required = false) String podId,
            @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId,
            @RequestHeader(value = "X-Actor-ID", required = false) String actorId,
            @RequestHeader(value = "X-Actor-Type", required = false) String actorType,
            @RequestHeader(value = "X-Purpose-Of-Use", required = false) String purposeOfUse,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody CreateReportRequest request) {
        RequestContext ctx = RequestContext.of(tenantId, podId, correlationId, actorId, actorType, purposeOfUse);
        return idempotency.run(ctx, idempotencyKey, HttpStatus.CREATED.value(),
                () -> ApiResponse.of(reports.createDraft(ctx.tenantId(), ctx.podId(),
                        ctx.actorId(), ctx.actorType(), request)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Object>> list(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestHeader(value = "X-Actor-ID", required = false) String actorId,
            @RequestHeader(value = "X-Actor-Type", required = false) String actorType,
            @RequestHeader(value = "x-max-scope", required = false) String maxScope,
            @RequestHeader(value = "X-Facility-ID", required = false) String actorFacilityId,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "reporter_actor_id", required = false) String reporterActorId,
            @RequestParam(value = "facility_id", required = false) String facilityId) {
        UUID tid = UUID.fromString(tenantId);
        List<ReportSummary> rows = reports.listReports(tid, status, reporterActorId, facilityId,
                actorId, actorType, maxScope, actorFacilityId);
        return ResponseEntity.ok(ApiResponse.of(shapedList(rows)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<Object>> get(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestHeader(value = "X-Actor-ID", required = false) String actorId,
            @RequestHeader(value = "X-Actor-Type", required = false) String actorType,
            @PathVariable UUID id) {
        ReportResponse report = reports.getReport(UUID.fromString(tenantId), id, actorId, actorType);
        return ResponseEntity.ok(ApiResponse.of(shaped(report)));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<ApiResponse<ReportResponse>> update(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateReportRequest request) {
        return ResponseEntity.ok(ApiResponse.of(reports.updateDraft(UUID.fromString(tenantId), id, request)));
    }

    @PostMapping("/{id}/products")
    public ResponseEntity<ApiResponse<ReportResponse>> addProduct(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @PathVariable UUID id,
            @Valid @RequestBody AddProductRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.of(reports.addProduct(UUID.fromString(tenantId), id, request)));
    }

    @PostMapping("/{id}/events")
    public ResponseEntity<ApiResponse<ReportResponse>> addEvent(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @PathVariable UUID id,
            @Valid @RequestBody AddEventRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.of(reports.addEvent(UUID.fromString(tenantId), id, request)));
    }

    @PostMapping("/{id}/attachments")
    public ResponseEntity<ApiResponse<ReportResponse>> addAttachment(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @PathVariable UUID id,
            @Valid @RequestBody AddAttachmentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.of(reports.addAttachment(UUID.fromString(tenantId), id, request)));
    }

    @PostMapping("/{id}/submit")
    public ResponseEntity<?> submit(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestHeader(value = "X-Pod-ID", required = false) String podId,
            @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId,
            @RequestHeader(value = "X-Actor-ID", required = false) String actorId,
            @RequestHeader(value = "X-Actor-Type", required = false) String actorType,
            @RequestHeader(value = "X-Purpose-Of-Use", required = false) String purposeOfUse,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @PathVariable UUID id) {
        RequestContext ctx = RequestContext.of(tenantId, podId, correlationId, actorId, actorType, purposeOfUse);
        return idempotency.run(ctx, idempotencyKey, HttpStatus.OK.value(),
                () -> ApiResponse.of(reports.submit(ctx.tenantId(), ctx.podId(),
                        ctx.actorId(), ctx.correlationId(), id)));
    }
}
