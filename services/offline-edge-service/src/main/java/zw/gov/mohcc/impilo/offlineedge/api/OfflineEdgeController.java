package zw.gov.mohcc.impilo.offlineedge.api;

import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.companion.context.RequestContext;
import zw.gov.mohcc.impilo.companion.context.RequestContextHolder;
import zw.gov.mohcc.impilo.companion.error.ErrorEnvelope;
import zw.gov.mohcc.impilo.offlineedge.api.dto.*;
import zw.gov.mohcc.impilo.offlineedge.core.OfflineEdgeService;
import zw.gov.mohcc.impilo.offlineedge.domain.*;

import java.util.*;

@RestController
public class OfflineEdgeController {

    private static final Logger log = LoggerFactory.getLogger(OfflineEdgeController.class);
    private final OfflineEdgeService edgeService;

    public OfflineEdgeController(OfflineEdgeService edgeService) { this.edgeService = edgeService; }

    @PostMapping("/internal/v1/offline/entitlements")
    public ResponseEntity<?> issueEntitlement(@Valid @RequestBody IssueEntitlementRequest request,
                                               jakarta.servlet.http.HttpServletRequest httpRequest) {
        RequestContext ctx = RequestContextHolder.require();
        String idempotencyKey = httpRequest.getHeader(CompanionHeaders.IDEMPOTENCY_KEY);
        EntitlementEntity ent = edgeService.issueEntitlement(UUID.fromString(ctx.tenantId()),
                ctx.podId(), ctx.correlationId(), idempotencyKey, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(toEntitlementResponse(ent));
    }

    @GetMapping("/internal/v1/offline/entitlements/{entitlement_id}")
    public ResponseEntity<?> getEntitlement(@PathVariable("entitlement_id") UUID entitlementId) {
        RequestContext ctx = RequestContextHolder.require();
        return edgeService.getEntitlement(entitlementId)
                .map(e -> ResponseEntity.ok((Object) toEntitlementResponse(e)))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ErrorEnvelope.of("NOT_FOUND", "Entitlement not found", ctx.requestId(), ctx.correlationId())));
    }

    @GetMapping("/internal/v1/offline/entitlements/{entitlement_id}/verify")
    public ResponseEntity<?> verifyEntitlement(@PathVariable("entitlement_id") UUID entitlementId,
                                                @RequestParam("token_hash") String tokenHash) {
        RequestContextHolder.require();
        boolean valid = edgeService.verifyEntitlementToken(entitlementId, tokenHash);
        return ResponseEntity.ok(Map.of("valid", valid, "entitlement_id", entitlementId.toString()));
    }

    @PostMapping("/internal/v1/offline/actions")
    public ResponseEntity<?> captureAction(@Valid @RequestBody CaptureActionRequest request,
                                            jakarta.servlet.http.HttpServletRequest httpRequest) {
        RequestContext ctx = RequestContextHolder.require();
        String idempotencyKey = httpRequest.getHeader(CompanionHeaders.IDEMPOTENCY_KEY);
        try {
            CapturedActionEntity action = edgeService.captureAction(UUID.fromString(ctx.tenantId()),
                    ctx.podId(), ctx.correlationId(), idempotencyKey, request);
            return ResponseEntity.status(HttpStatus.CREATED).body(toActionResponse(action));
        } catch (OfflineEdgeService.InvalidEntitlementException e) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                    .body(ErrorEnvelope.of("INVALID_ENTITLEMENT", e.getMessage(), ctx.requestId(), ctx.correlationId()));
        }
    }

    @PostMapping("/internal/v1/offline/replay/{entitlement_id}")
    public ResponseEntity<?> replayActions(@PathVariable("entitlement_id") UUID entitlementId,
                                            jakarta.servlet.http.HttpServletRequest httpRequest) {
        RequestContext ctx = RequestContextHolder.require();
        try {
            ReconciliationBatchEntity batch = edgeService.replayActions(entitlementId,
                    UUID.fromString(ctx.tenantId()), ctx.podId(), ctx.correlationId());
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("batch_id", batch.getBatchId().toString());
            body.put("status", batch.getStatus());
            body.put("total_actions", batch.getTotalActions());
            body.put("replayed_count", batch.getReplayedCount());
            body.put("failed_count", batch.getFailedCount());
            return ResponseEntity.ok(body);
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ErrorEnvelope.of("NO_QUEUED_ACTIONS", e.getMessage(), ctx.requestId(), ctx.correlationId()));
        }
    }

    @GetMapping("/internal/v1/offline/actions")
    public ResponseEntity<?> listActions(@RequestParam(required = false) String status,
                                          @RequestParam(defaultValue = "0") int cursor,
                                          @RequestParam(defaultValue = "50") int limit) {
        RequestContext ctx = RequestContextHolder.require();
        Page<CapturedActionEntity> page = edgeService.listActions(UUID.fromString(ctx.tenantId()),
                status, cursor, limit);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("items", page.getContent().stream().map(this::toActionResponse).toList());
        body.put("cursor", page.hasNext() ? String.valueOf(cursor + 1) : null);
        body.put("limit", limit);
        body.put("total_elements", page.getTotalElements());
        body.put("has_more", page.hasNext());
        return ResponseEntity.ok(body);
    }

    private EntitlementResponse toEntitlementResponse(EntitlementEntity e) {
        return new EntitlementResponse(e.getEntitlementId(), e.getTenantId(), e.getActorId(),
                e.getFacilityRef(), e.getWorkflowType(), e.getTokenHash(),
                e.getIssuedAt(), e.getExpiresAt(), e.isRevoked());
    }

    private CapturedActionResponse toActionResponse(CapturedActionEntity a) {
        return new CapturedActionResponse(a.getActionId(), a.getEntitlementId(), a.getTenantId(),
                a.getActorId(), a.getPatientRef(), a.getWorkflowType(), a.getActionType(),
                a.getStatus(), a.getCapturedAt(), a.getSequenceNum(),
                a.getReplayedAt(), a.getReplayError());
    }
}
