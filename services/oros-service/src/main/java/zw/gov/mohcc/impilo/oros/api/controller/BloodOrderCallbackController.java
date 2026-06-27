package zw.gov.mohcc.impilo.oros.api.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.oros.core.BloodOrderCallbackService;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;

import java.util.Map;

/**
 * Internal sink for blood-bank fulfilment callbacks from the MADI service (spec §3.D). MADI is the
 * sovereign fulfiller; these endpoints close the loop on the OROS BLOOD_BANK order — recording
 * crossmatch/issue/transfusion outcomes as results/events. Authorization is enforced at the
 * Envoy/TSHEPO edge (internal service-to-service with propagated trust headers).
 */
@RestController
@RequestMapping("/internal/v1/orders/blood")
public class BloodOrderCallbackController {

    private final BloodOrderCallbackService service;

    public BloodOrderCallbackController(BloodOrderCallbackService service) {
        this.service = service;
    }

    public record SubmittedRequest(String orosOrderRef, String madiOrderId, String source) {}
    public record IssuedRequest(String orosOrderRef, String unitId, String source) {}
    public record CrossmatchRequest(String orosOrderRef, String resultStatus, String notes) {}
    public record TransfusionOutcomeRequest(String orosOrderRef, String outcome, String notes) {}

    @PostMapping("/submitted")
    public ResponseEntity<ApiResponse<Map<String, Object>>> submitted(@RequestBody SubmittedRequest req) {
        service.submitted(req.orosOrderRef(), req.madiOrderId());
        return ok();
    }

    @PostMapping("/issued")
    public ResponseEntity<ApiResponse<Map<String, Object>>> issued(@RequestBody IssuedRequest req) {
        service.issued(req.orosOrderRef(), req.unitId());
        return ok();
    }

    @PostMapping("/crossmatch-result")
    public ResponseEntity<ApiResponse<Map<String, Object>>> crossmatch(@RequestBody CrossmatchRequest req) {
        service.crossmatchResult(req.orosOrderRef(), req.resultStatus(), req.notes());
        return ok();
    }

    @PostMapping("/transfusion-outcome")
    public ResponseEntity<ApiResponse<Map<String, Object>>> transfusionOutcome(
            @RequestBody TransfusionOutcomeRequest req) {
        service.transfusionOutcome(req.orosOrderRef(), req.outcome(), req.notes());
        return ok();
    }

    private ResponseEntity<ApiResponse<Map<String, Object>>> ok() {
        String cid = TrustContextHolder.require().correlationId().toString();
        return ResponseEntity.ok(ApiResponse.ok(Map.of("status", "RECORDED"), cid));
    }
}
