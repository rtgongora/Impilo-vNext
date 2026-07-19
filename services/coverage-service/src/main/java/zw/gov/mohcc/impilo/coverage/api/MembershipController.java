package zw.gov.mohcc.impilo.coverage.api;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.coverage.api.dto.MembershipDtos.AddDependantRequest;
import zw.gov.mohcc.impilo.coverage.api.dto.MembershipDtos.MembershipView;
import zw.gov.mohcc.impilo.coverage.api.dto.MembershipDtos.TransitionRequest;
import zw.gov.mohcc.impilo.coverage.api.dto.MembershipDtos.VerifyRequest;
import zw.gov.mohcc.impilo.coverage.core.MembershipService;
import zw.gov.mohcc.impilo.coverage.domain.MemberCoverageEntity;

import java.util.List;
import java.util.UUID;

/**
 * Membership lifecycle endpoints (spec §10): verification, state transitions,
 * dependants, and the verification work-queue.
 */
@RestController
@RequestMapping("/internal/v1/coverage/members")
public class MembershipController {

    private final MembershipService membershipService;

    public MembershipController(MembershipService membershipService) {
        this.membershipService = membershipService;
    }

    @GetMapping("/pending-verification")
    public ResponseEntity<List<MembershipView>> pendingVerification(
            @RequestHeader("X-Tenant-ID") String tenantId) {
        UUID tid = UUID.fromString(tenantId);
        return ResponseEntity.ok(membershipService.pendingVerification(tid)
                .stream().map(MembershipView::of).toList());
    }

    @GetMapping("/{id}/dependants")
    public ResponseEntity<List<MembershipView>> dependants(
            @RequestHeader("X-Tenant-ID") String tenantId, @PathVariable UUID id) {
        UUID tid = UUID.fromString(tenantId);
        return ResponseEntity.ok(membershipService.listDependants(tid, id)
                .stream().map(MembershipView::of).toList());
    }

    @PostMapping("/{id}/verify")
    public ResponseEntity<MembershipView> verify(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestHeader("X-Pod-ID") String podId,
            @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId,
            @PathVariable UUID id,
            @RequestBody(required = false) VerifyRequest req) {
        boolean verified = req == null || req.verified() == null || req.verified();
        String source = req != null ? req.source() : null;
        MemberCoverageEntity m = membershipService.verify(UUID.fromString(tenantId), podId, id,
                source, verified, CorrelationIds.fromHeader(correlationId));
        return ResponseEntity.ok(MembershipView.of(m));
    }

    @PostMapping("/{id}/suspend")
    public ResponseEntity<MembershipView> suspend(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestHeader("X-Pod-ID") String podId,
            @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId,
            @PathVariable UUID id,
            @RequestBody(required = false) TransitionRequest req) {
        return doTransition(tenantId, podId, correlationId, id, "SUSPENDED", req);
    }

    @PostMapping("/{id}/reinstate")
    public ResponseEntity<MembershipView> reinstate(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestHeader("X-Pod-ID") String podId,
            @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId,
            @PathVariable UUID id,
            @RequestBody(required = false) TransitionRequest req) {
        return doTransition(tenantId, podId, correlationId, id, "ACTIVE", req);
    }

    @PostMapping("/{id}/terminate")
    public ResponseEntity<MembershipView> terminate(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestHeader("X-Pod-ID") String podId,
            @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId,
            @PathVariable UUID id,
            @RequestBody(required = false) TransitionRequest req) {
        return doTransition(tenantId, podId, correlationId, id, "TERMINATED", req);
    }

    @PostMapping("/{id}/dependants")
    public ResponseEntity<MembershipView> addDependant(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestHeader("X-Pod-ID") String podId,
            @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId,
            @PathVariable UUID id,
            @Valid @RequestBody AddDependantRequest req) {
        MemberCoverageEntity dep = membershipService.addDependant(UUID.fromString(tenantId), podId, id,
                req.dependantClientId(), req.relationship(), req.memberNumber(),
                CorrelationIds.fromHeader(correlationId));
        return ResponseEntity.status(HttpStatus.CREATED).body(MembershipView.of(dep));
    }

    private ResponseEntity<MembershipView> doTransition(String tenantId, String podId, String correlationId,
                                                        UUID id, String target, TransitionRequest req) {
        MemberCoverageEntity m = membershipService.transition(UUID.fromString(tenantId), podId, id, target,
                req != null ? req.reason() : null, CorrelationIds.fromHeader(correlationId));
        return ResponseEntity.ok(MembershipView.of(m));
    }
}
