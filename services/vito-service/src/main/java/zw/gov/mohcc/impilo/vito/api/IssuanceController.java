package zw.gov.mohcc.impilo.vito.api;

import org.springframework.data.domain.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.shared.auth.*;
import zw.gov.mohcc.impilo.vito.core.*;
import zw.gov.mohcc.impilo.vito.core.issuance.IssuanceStateMachineService;
import zw.gov.mohcc.impilo.vito.persistence.entity.IssuanceRequestEntity;

import java.util.*;

/**
 * Internal issuance workflow endpoints.
 * Used by operators to manage the issuance pipeline.
 */
@RestController
@RequestMapping("/v1/internal/issuance")
public class IssuanceController {

    private final IssuanceStateMachineService issuanceService;

    public IssuanceController(IssuanceStateMachineService issuanceService) {
        this.issuanceService = issuanceService;
    }

    @PostMapping("/submit")
    public ResponseEntity<?> submit(@RequestBody Map<String, String> body) {
        TrustContext ctx = TrustContextHolder.require();
        if (ctx.mode() != AccessMode.INTERNAL) {
            return ResponseEntity.status(403).body(Map.of("error", "INTERNAL_ONLY"));
        }
        UUID tenantId = ctx.tenantId();
        UUID healthId = UUID.fromString(body.get("healthId"));
        IssuanceType type = IssuanceType.valueOf(body.getOrDefault("type", "NEW"));
        IssuanceChannel channel = IssuanceChannel.valueOf(body.getOrDefault("channel", "ASSISTED"));

        IssuanceRequestEntity req = issuanceService.submit(tenantId, healthId, type, channel,
                ctx.actorId());
        return ResponseEntity.ok(req);
    }

    @PostMapping("/{requestId}/proofing")
    public ResponseEntity<?> startProofing(@PathVariable Long requestId) {
        TrustContext ctx = TrustContextHolder.require();
        if (ctx.mode() != AccessMode.INTERNAL) {
            return ResponseEntity.status(403).body(Map.of("error", "INTERNAL_ONLY"));
        }
        UUID tenantId = ctx.tenantId();
        return ResponseEntity.ok(issuanceService.startProofing(tenantId, requestId,
                ctx.actorId()));
    }

    @PostMapping("/{requestId}/approve")
    public ResponseEntity<?> approve(@PathVariable Long requestId) {
        TrustContext ctx = TrustContextHolder.require();
        if (ctx.mode() != AccessMode.INTERNAL) {
            return ResponseEntity.status(403).body(Map.of("error", "INTERNAL_ONLY"));
        }
        UUID tenantId = ctx.tenantId();
        return ResponseEntity.ok(issuanceService.approve(tenantId, requestId,
                ctx.actorId()));
    }

    @PostMapping("/{requestId}/issue")
    public ResponseEntity<?> issue(@PathVariable Long requestId) {
        TrustContext ctx = TrustContextHolder.require();
        if (ctx.mode() != AccessMode.INTERNAL) {
            return ResponseEntity.status(403).body(Map.of("error", "INTERNAL_ONLY"));
        }
        UUID tenantId = ctx.tenantId();
        return ResponseEntity.ok(issuanceService.issue(tenantId, requestId,
                ctx.actorId()));
    }

    @PostMapping("/{requestId}/deliver")
    public ResponseEntity<?> deliver(@PathVariable Long requestId) {
        TrustContext ctx = TrustContextHolder.require();
        if (ctx.mode() != AccessMode.INTERNAL) {
            return ResponseEntity.status(403).body(Map.of("error", "INTERNAL_ONLY"));
        }
        UUID tenantId = ctx.tenantId();
        return ResponseEntity.ok(issuanceService.deliver(tenantId, requestId));
    }

    @PostMapping("/{requestId}/reject")
    public ResponseEntity<?> reject(@PathVariable Long requestId, @RequestBody Map<String, String> body) {
        TrustContext ctx = TrustContextHolder.require();
        if (ctx.mode() != AccessMode.INTERNAL) {
            return ResponseEntity.status(403).body(Map.of("error", "INTERNAL_ONLY"));
        }
        UUID tenantId = ctx.tenantId();
        return ResponseEntity.ok(issuanceService.reject(tenantId, requestId, body.get("reason")));
    }

    @GetMapping("/queue")
    public ResponseEntity<?> queue(@RequestParam(defaultValue = "SUBMITTED") String state,
                                    @RequestParam(defaultValue = "0") int page,
                                    @RequestParam(defaultValue = "50") int size) {
        TrustContext ctx = TrustContextHolder.require();
        UUID tenantId = ctx.tenantId();
        IssuanceState issuanceState = IssuanceState.valueOf(state);
        return ResponseEntity.ok(issuanceService.listByState(tenantId, issuanceState,
                PageRequest.of(page, size)));
    }

    @GetMapping("/{requestId}")
    public ResponseEntity<?> get(@PathVariable Long requestId) {
        TrustContext ctx = TrustContextHolder.require();
        UUID tenantId = ctx.tenantId();
        return ResponseEntity.ok(issuanceService.getRequest(tenantId, requestId));
    }
}
