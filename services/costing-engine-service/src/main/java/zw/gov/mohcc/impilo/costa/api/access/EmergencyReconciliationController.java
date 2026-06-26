package zw.gov.mohcc.impilo.costa.api.access;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.costa.api.dto.EmergencyDeferredChargeResponse;
import zw.gov.mohcc.impilo.costa.api.dto.FlagEmergencyDeferredChargeRequest;
import zw.gov.mohcc.impilo.costa.api.dto.LinkConfirmedPersonRequest;
import zw.gov.mohcc.impilo.costa.api.dto.ReconcileDeferredChargeRequest;
import zw.gov.mohcc.impilo.costa.service.EmergencyReconciliationService;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;

import java.util.List;
import java.util.UUID;

/**
 * Emergency-override deferred-charge reconciliation (Journey Law 1, §10).
 *
 * <p>Care is never blocked here — this is the post-care settle path. Authz is enforced
 * upstream by Envoy ext_authz → TSHEPO; policy rule (spec, track P):
 * {@code EMERGENCY-CARE-NEVER-BLOCKED}. Errors are mapped by
 * {@link ServiceAccessApiExceptionHandler}; the value-event audit trail rides the outbox.
 */
@RestController
@RequestMapping("/costa/v1/emergency-reconciliation")
public class EmergencyReconciliationController {

    private final EmergencyReconciliationService service;

    public EmergencyReconciliationController(EmergencyReconciliationService service) {
        this.service = service;
    }

    /** List emergency-overridden / deferred charges (filter by state or provisional person). */
    @GetMapping("/deferred-charges")
    public ResponseEntity<ApiResponse<List<EmergencyDeferredChargeResponse>>> list(
            @RequestParam(name = "state", required = false) String state,
            @RequestParam(name = "provisional_person_ref", required = false) String provisionalPersonRef) {
        var ctx = TrustContextHolder.require();
        List<EmergencyDeferredChargeResponse> items = service.list(state, provisionalPersonRef);
        return ResponseEntity.ok(ApiResponse.ok(items, ctx.correlationId().toString()));
    }

    @GetMapping("/deferred-charges/{id}")
    public ResponseEntity<ApiResponse<EmergencyDeferredChargeResponse>> get(@PathVariable("id") UUID id) {
        var ctx = TrustContextHolder.require();
        return ResponseEntity.ok(ApiResponse.ok(service.require(id), ctx.correlationId().toString()));
    }

    /** Flag a charge accrued under EMERGENCY_OVERRIDE as deferred, pending reconciliation. */
    @PostMapping("/deferred-charges")
    public ResponseEntity<ApiResponse<EmergencyDeferredChargeResponse>> flag(
            @Valid @RequestBody FlagEmergencyDeferredChargeRequest body) {
        var ctx = TrustContextHolder.require();
        EmergencyDeferredChargeResponse created = service.flag(body);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(created, ctx.correlationId().toString()));
    }

    /** Link the provisional emergency person to the confirmed person (post-stabilisation). */
    @PostMapping("/deferred-charges/{id}/link-person")
    public ResponseEntity<ApiResponse<EmergencyDeferredChargeResponse>> linkPerson(
            @PathVariable("id") UUID id,
            @Valid @RequestBody LinkConfirmedPersonRequest body) {
        var ctx = TrustContextHolder.require();
        return ResponseEntity.ok(ApiResponse.ok(service.linkConfirmedPerson(id, body), ctx.correlationId().toString()));
    }

    /** Route a pending deferred charge to settle/claim/waiver and close reconciliation. */
    @PostMapping("/deferred-charges/{id}/reconcile")
    public ResponseEntity<ApiResponse<EmergencyDeferredChargeResponse>> reconcile(
            @PathVariable("id") UUID id,
            @Valid @RequestBody ReconcileDeferredChargeRequest body) {
        var ctx = TrustContextHolder.require();
        return ResponseEntity.ok(ApiResponse.ok(service.reconcile(id, body), ctx.correlationId().toString()));
    }
}
