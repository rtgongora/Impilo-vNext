package zw.gov.mohcc.impilo.costa.api.access;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.costa.api.dto.GrantWaiverRequest;
import zw.gov.mohcc.impilo.costa.api.dto.WaiverDecisionRequest;
import zw.gov.mohcc.impilo.costa.api.dto.WaiverResponse;
import zw.gov.mohcc.impilo.costa.service.WaiverService;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;

import java.util.List;
import java.util.UUID;

/**
 * Discretionary fee-waiver CRUD: grant → approve / reject → revoke.
 *
 * <p>Distinct from rules-driven exemptions. Authz enforced upstream by Envoy ext_authz →
 * TSHEPO; errors mapped by {@link ServiceAccessApiExceptionHandler}; approval/revocation
 * emit a WAIVER_APPLIED value-event (C8) for the audit trail and downstream rebilling.
 */
@RestController
@RequestMapping("/costa/v1/waivers")
public class WaiverController {

    private final WaiverService service;

    public WaiverController(WaiverService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<WaiverResponse>>> list(
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "patient_cpid", required = false) String patientCpid) {
        var ctx = TrustContextHolder.require();
        return ResponseEntity.ok(ApiResponse.ok(service.list(status, patientCpid), ctx.correlationId().toString()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<WaiverResponse>> get(@PathVariable("id") UUID id) {
        var ctx = TrustContextHolder.require();
        return ResponseEntity.ok(ApiResponse.ok(service.require(id), ctx.correlationId().toString()));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<WaiverResponse>> grant(@Valid @RequestBody GrantWaiverRequest body) {
        var ctx = TrustContextHolder.require();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(service.grant(body), ctx.correlationId().toString()));
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<ApiResponse<WaiverResponse>> approve(
            @PathVariable("id") UUID id,
            @RequestBody(required = false) WaiverDecisionRequest body) {
        var ctx = TrustContextHolder.require();
        return ResponseEntity.ok(ApiResponse.ok(service.approve(id, body), ctx.correlationId().toString()));
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<ApiResponse<WaiverResponse>> reject(
            @PathVariable("id") UUID id,
            @RequestBody(required = false) WaiverDecisionRequest body) {
        var ctx = TrustContextHolder.require();
        return ResponseEntity.ok(ApiResponse.ok(service.reject(id, body), ctx.correlationId().toString()));
    }

    @PostMapping("/{id}/revoke")
    public ResponseEntity<ApiResponse<WaiverResponse>> revoke(
            @PathVariable("id") UUID id,
            @RequestBody(required = false) WaiverDecisionRequest body) {
        var ctx = TrustContextHolder.require();
        return ResponseEntity.ok(ApiResponse.ok(service.revoke(id, body), ctx.correlationId().toString()));
    }
}
