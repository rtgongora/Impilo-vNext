package zw.gov.mohcc.impilo.tuso.api.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;
import zw.gov.mohcc.impilo.tuso.core.VirtualServiceRegistryService;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Virtual service-delivery registry API (TUSO SoR). Governed configuration for
 * virtual hospitals: entity + routing seams + queue definitions, and the
 * fail-closed activation lifecycle. Authz via TSHEPO ext_authz; the BFF
 * composes these payloads for the experience shell.
 */
@RestController
@RequestMapping("/v1/internal/virtual-services")
public class VirtualServiceController {

    private static final Logger log = LoggerFactory.getLogger(VirtualServiceController.class);

    private final VirtualServiceRegistryService registryService;

    public VirtualServiceController(VirtualServiceRegistryService registryService) {
        this.registryService = registryService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> list(
            @RequestParam(value = "lifecycleStatus", required = false) String lifecycleStatus) {
        TrustContext ctx = TrustContextHolder.require();
        List<String> statuses = lifecycleStatus == null || lifecycleStatus.isBlank()
                ? List.of()
                : Arrays.stream(lifecycleStatus.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
        return ResponseEntity.ok(ApiResponse.ok(
                registryService.list(ctx.tenantId(), statuses), ctx.correlationId().toString()));
    }

    @GetMapping("/{vsUid}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> get(@PathVariable String vsUid) {
        TrustContext ctx = TrustContextHolder.require();
        return ResponseEntity.ok(ApiResponse.ok(
                registryService.getByUid(ctx.tenantId(), vsUid), ctx.correlationId().toString()));
    }

    @GetMapping("/{vsUid}/history")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> history(@PathVariable String vsUid) {
        TrustContext ctx = TrustContextHolder.require();
        return ResponseEntity.ok(ApiResponse.ok(
                registryService.history(ctx.tenantId(), vsUid), ctx.correlationId().toString()));
    }

    @PatchMapping("/{vsUid}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> patch(
            @PathVariable String vsUid, @RequestBody Map<String, Object> request) {
        return doPatch(vsUid, request);
    }

    /**
     * POST alias for partial update — composition layers whose HTTP client
     * cannot issue PATCH (the BFF's SimpleClientHttpRequestFactory) use this.
     * Same semantics as the PATCH route (mirrors ServicePointController).
     */
    @PostMapping("/{vsUid}/update")
    public ResponseEntity<ApiResponse<Map<String, Object>>> patchViaPost(
            @PathVariable String vsUid, @RequestBody Map<String, Object> request) {
        return doPatch(vsUid, request);
    }

    private ResponseEntity<ApiResponse<Map<String, Object>>> doPatch(String vsUid, Map<String, Object> request) {
        TrustContext ctx = TrustContextHolder.require();
        log.info("Virtual service PATCH [vsUid={}] by {} correlationId={}", vsUid, ctx.actorId(), ctx.correlationId());
        return ResponseEntity.ok(ApiResponse.ok(
                registryService.patch(ctx, vsUid, request), ctx.correlationId().toString()));
    }

    @PostMapping("/{vsUid}/routing-seams")
    public ResponseEntity<ApiResponse<Map<String, Object>>> addSeam(
            @PathVariable String vsUid, @RequestBody Map<String, Object> request) {
        TrustContext ctx = TrustContextHolder.require();
        log.info("Virtual service seam add [vsUid={}] by {} correlationId={}",
                vsUid, ctx.actorId(), ctx.correlationId());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(
                registryService.addSeam(ctx, vsUid, request), ctx.correlationId().toString()));
    }

    @PostMapping("/{vsUid}/activate")
    public ResponseEntity<ApiResponse<Map<String, Object>>> activate(@PathVariable String vsUid) {
        TrustContext ctx = TrustContextHolder.require();
        log.info("Virtual service activation requested [vsUid={}] by {} correlationId={}",
                vsUid, ctx.actorId(), ctx.correlationId());
        return ResponseEntity.ok(ApiResponse.ok(
                registryService.activate(ctx, vsUid), ctx.correlationId().toString()));
    }

    @PostMapping("/{vsUid}/suspend")
    public ResponseEntity<ApiResponse<Map<String, Object>>> suspend(
            @PathVariable String vsUid, @RequestBody(required = false) Map<String, Object> request) {
        TrustContext ctx = TrustContextHolder.require();
        String reason = request == null ? null : (request.get("reason") == null ? null : request.get("reason").toString());
        log.info("Virtual service suspend [vsUid={}] by {} correlationId={}", vsUid, ctx.actorId(), ctx.correlationId());
        return ResponseEntity.ok(ApiResponse.ok(
                registryService.suspend(ctx, vsUid, reason), ctx.correlationId().toString()));
    }
}
