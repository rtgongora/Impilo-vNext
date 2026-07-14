package zw.gov.mohcc.impilo.tuso.api.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;
import zw.gov.mohcc.impilo.tuso.core.CssdCycleService;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * CSSD sterilisation lifecycle API (TUSO SoR, Wave 3, spec §9). Consumed by inpatient theatre so the
 * INSTRUMENT_SET readiness gate issues a real STERILE set and the SIGN_OUT/return flow marks it SOILED.
 */
@RestController
@RequestMapping("/v1/internal/tuso/instrument-sets")
public class CssdCycleController {

    private final CssdCycleService service;

    public CssdCycleController(CssdCycleService service) {
        this.service = service;
    }

    private String cid() {
        TrustContext ctx = TrustContextHolder.require();
        return ctx.correlationId() != null ? ctx.correlationId().toString() : UUID.randomUUID().toString();
    }

    @GetMapping("/{id}/cssd")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getSet(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(service.getSet(id), cid()));
    }

    @GetMapping("/{id}/cssd/cycles")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> cycles(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(service.listCycles(id), cid()));
    }

    @PostMapping("/{id}/issue")
    public ResponseEntity<ApiResponse<Map<String, Object>>> issue(
            @PathVariable UUID id, @RequestBody(required = false) Map<String, Object> body) {
        return ResponseEntity.ok(ApiResponse.ok(service.issueToCase(id, body != null ? body : Map.of()), cid()));
    }

    @PostMapping("/{id}/return")
    public ResponseEntity<ApiResponse<Map<String, Object>>> returnSet(
            @PathVariable UUID id, @RequestBody(required = false) Map<String, Object> body) {
        return ResponseEntity.ok(ApiResponse.ok(service.returnFromCase(id, body != null ? body : Map.of()), cid()));
    }

    @PostMapping("/{id}/reprocess")
    public ResponseEntity<ApiResponse<Map<String, Object>>> reprocess(
            @PathVariable UUID id, @RequestBody(required = false) Map<String, Object> body) {
        return ResponseEntity.ok(ApiResponse.ok(service.reprocess(id, body != null ? body : Map.of()), cid()));
    }

    @PostMapping("/{id}/flag")
    public ResponseEntity<ApiResponse<Map<String, Object>>> flag(
            @PathVariable UUID id, @RequestBody(required = false) Map<String, Object> body) {
        return ResponseEntity.ok(ApiResponse.ok(service.flagContaminated(id, body != null ? body : Map.of()), cid()));
    }
}
