package zw.gov.mohcc.impilo.patientsafety.api;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.patientsafety.api.dto.ApiResponse;
import zw.gov.mohcc.impilo.patientsafety.api.dto.CaseResponse;
import zw.gov.mohcc.impilo.patientsafety.api.dto.CaseSummary;
import zw.gov.mohcc.impilo.patientsafety.api.dto.FollowUpRequestDto;
import zw.gov.mohcc.impilo.patientsafety.api.dto.FollowUpResponseDto;
import zw.gov.mohcc.impilo.patientsafety.api.dto.ReviewActionRequest;
import zw.gov.mohcc.impilo.patientsafety.api.dto.TriageRequest;
import zw.gov.mohcc.impilo.patientsafety.api.dto.VigiFlowManualEntryRequest;
import zw.gov.mohcc.impilo.patientsafety.core.ConfigService;
import zw.gov.mohcc.impilo.patientsafety.core.SafetyCaseService;

import java.util.List;
import java.util.UUID;

/**
 * Regulatory safety case API (MCAZ workbench actions). Honesty: VigiFlow entry is MANUAL and export
 * readiness reflects the real adapter posture — never a faked "submitted" state.
 */
@RestController
@RequestMapping("/internal/v1/patient-safety/cases")
public class SafetyCaseController {

    private final SafetyCaseService cases;
    private final ConfigService config;
    private final IdempotencySupport idempotency;

    public SafetyCaseController(SafetyCaseService cases, ConfigService config, IdempotencySupport idempotency) {
        this.cases = cases;
        this.config = config;
        this.idempotency = idempotency;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<CaseSummary>>> list(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "priority", required = false) String priority) {
        return ResponseEntity.ok(ApiResponse.of(
                cases.listCases(UUID.fromString(tenantId), status, priority)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<CaseResponse>> get(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.of(cases.getCase(UUID.fromString(tenantId), id)));
    }

    @PostMapping("/{id}/triage")
    public ResponseEntity<ApiResponse<CaseResponse>> triage(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestHeader(value = "X-Pod-ID", required = false) String podId,
            @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId,
            @RequestHeader(value = "X-Actor-ID", required = false) String actorId,
            @RequestHeader(value = "X-Purpose-Of-Use", required = false) String purposeOfUse,
            @PathVariable UUID id,
            @Valid @RequestBody TriageRequest request) {
        RequestContext ctx = RequestContext.of(tenantId, podId, correlationId, actorId, "STAFF", purposeOfUse);
        return ResponseEntity.ok(ApiResponse.of(
                cases.triage(ctx.tenantId(), id, ctx.actorId(), ctx.actorRole(), ctx.correlationId(), request)));
    }

    @PostMapping("/{id}/review-actions")
    public ResponseEntity<ApiResponse<CaseResponse>> review(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestHeader(value = "X-Actor-ID", required = false) String actorId,
            @RequestHeader(value = "X-Purpose-Of-Use", required = false) String purposeOfUse,
            @PathVariable UUID id,
            @Valid @RequestBody ReviewActionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(
                cases.addReviewAction(UUID.fromString(tenantId), id, actorId, purposeOfUse, request)));
    }

    @PostMapping("/{id}/follow-up")
    public ResponseEntity<ApiResponse<CaseResponse>> requestFollowUp(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestHeader(value = "X-Pod-ID", required = false) String podId,
            @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId,
            @RequestHeader(value = "X-Actor-ID", required = false) String actorId,
            @RequestHeader(value = "X-Purpose-Of-Use", required = false) String purposeOfUse,
            @PathVariable UUID id,
            @Valid @RequestBody FollowUpRequestDto request) {
        RequestContext ctx = RequestContext.of(tenantId, podId, correlationId, actorId, "STAFF", purposeOfUse);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(
                cases.requestFollowUp(ctx.tenantId(), id, ctx.actorId(), ctx.actorRole(),
                        ctx.correlationId(), request)));
    }

    @PostMapping("/{id}/follow-up/{followUpId}/response")
    public ResponseEntity<ApiResponse<CaseResponse>> followUpResponse(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestHeader(value = "X-Pod-ID", required = false) String podId,
            @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId,
            @RequestHeader(value = "X-Actor-ID", required = false) String actorId,
            @PathVariable UUID id,
            @PathVariable UUID followUpId,
            @Valid @RequestBody FollowUpResponseDto request) {
        RequestContext ctx = RequestContext.of(tenantId, podId, correlationId, actorId, null, null);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(
                cases.recordFollowUpResponse(ctx.tenantId(), id, followUpId, ctx.correlationId(), request)));
    }

    @PostMapping("/{id}/vigiflow-manual-entry")
    public ResponseEntity<?> vigiflowManualEntry(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestHeader(value = "X-Pod-ID", required = false) String podId,
            @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId,
            @RequestHeader(value = "X-Actor-ID", required = false) String actorId,
            @RequestHeader(value = "X-Purpose-Of-Use", required = false) String purposeOfUse,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @PathVariable UUID id,
            @Valid @RequestBody VigiFlowManualEntryRequest request) {
        RequestContext ctx = RequestContext.of(tenantId, podId, correlationId, actorId, "STAFF", purposeOfUse);
        return idempotency.run(ctx, idempotencyKey, HttpStatus.CREATED.value(),
                () -> ApiResponse.of(cases.recordVigiFlowManualEntry(
                        ctx.tenantId(), id, ctx.actorId(), ctx.actorRole(), ctx.correlationId(), request)));
    }

    @PostMapping("/{id}/mark-export-ready")
    public ResponseEntity<?> markExportReady(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestHeader(value = "X-Pod-ID", required = false) String podId,
            @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId,
            @RequestHeader(value = "X-Actor-ID", required = false) String actorId,
            @RequestHeader(value = "X-Purpose-Of-Use", required = false) String purposeOfUse,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @PathVariable UUID id) {
        RequestContext ctx = RequestContext.of(tenantId, podId, correlationId, actorId, "STAFF", purposeOfUse);
        boolean adapterEnabled = config.isVigiflowAdapterEnabled();
        return idempotency.run(ctx, idempotencyKey, HttpStatus.CREATED.value(),
                () -> ApiResponse.of(cases.markExportReady(
                        ctx.tenantId(), id, ctx.actorId(), ctx.actorRole(), ctx.correlationId(), adapterEnabled)));
    }

    @PostMapping("/{id}/close")
    public ResponseEntity<ApiResponse<CaseResponse>> close(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestHeader(value = "X-Actor-ID", required = false) String actorId,
            @RequestHeader(value = "X-Purpose-Of-Use", required = false) String purposeOfUse,
            @PathVariable UUID id,
            @RequestBody(required = false) ReviewActionRequest request) {
        String note = request != null ? request.note() : null;
        return ResponseEntity.ok(ApiResponse.of(
                cases.close(UUID.fromString(tenantId), id, actorId, purposeOfUse, note)));
    }
}
