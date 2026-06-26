package zw.gov.mohcc.impilo.patientsafety.api;

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
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.patientsafety.api.dto.ApiResponse;
import zw.gov.mohcc.impilo.patientsafety.api.dto.CaseResponse.InvestigationView;
import zw.gov.mohcc.impilo.patientsafety.api.dto.OpenInvestigationRequest;
import zw.gov.mohcc.impilo.patientsafety.api.dto.UpdateInvestigationRequest;
import zw.gov.mohcc.impilo.patientsafety.core.AefiInvestigationService;

import java.util.UUID;

/** Serious-AEFI investigation API. */
@RestController
@RequestMapping("/internal/v1/patient-safety")
public class AefiInvestigationController {

    private final AefiInvestigationService investigations;

    public AefiInvestigationController(AefiInvestigationService investigations) {
        this.investigations = investigations;
    }

    @PostMapping("/cases/{caseId}/investigation")
    public ResponseEntity<ApiResponse<InvestigationView>> open(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestHeader(value = "X-Pod-ID", required = false) String podId,
            @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId,
            @RequestHeader(value = "X-Actor-ID", required = false) String actorId,
            @RequestHeader(value = "X-Purpose-Of-Use", required = false) String purposeOfUse,
            @PathVariable UUID caseId,
            @Valid @RequestBody OpenInvestigationRequest request) {
        RequestContext ctx = RequestContext.of(tenantId, podId, correlationId, actorId, "STAFF", purposeOfUse);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(
                investigations.open(ctx.tenantId(), caseId, ctx.actorId(), ctx.actorRole(),
                        ctx.correlationId(), request)));
    }

    @GetMapping("/cases/{caseId}/investigation")
    public ResponseEntity<ApiResponse<InvestigationView>> getByCase(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @PathVariable UUID caseId) {
        return ResponseEntity.ok(ApiResponse.of(
                investigations.getByCase(UUID.fromString(tenantId), caseId)));
    }

    @PatchMapping("/investigations/{investigationId}")
    public ResponseEntity<ApiResponse<InvestigationView>> update(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestHeader(value = "X-Pod-ID", required = false) String podId,
            @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId,
            @RequestHeader(value = "X-Actor-ID", required = false) String actorId,
            @RequestHeader(value = "X-Purpose-Of-Use", required = false) String purposeOfUse,
            @PathVariable UUID investigationId,
            @Valid @RequestBody UpdateInvestigationRequest request) {
        RequestContext ctx = RequestContext.of(tenantId, podId, correlationId, actorId, "STAFF", purposeOfUse);
        return ResponseEntity.ok(ApiResponse.of(
                investigations.update(ctx.tenantId(), investigationId, ctx.actorId(), ctx.actorRole(),
                        ctx.correlationId(), request)));
    }
}
