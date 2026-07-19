package zw.gov.mohcc.impilo.coverage.api;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.coverage.api.dto.AuthorisationDtos.AuthorisationView;
import zw.gov.mohcc.impilo.coverage.api.dto.AuthorisationDtos.CreateAuthorisationRequest;
import zw.gov.mohcc.impilo.coverage.api.dto.AuthorisationDtos.DecideAuthorisationRequest;
import zw.gov.mohcc.impilo.coverage.api.dto.AuthorisationDtos.TransitionAuthorisationRequest;
import zw.gov.mohcc.impilo.coverage.core.AuthorisationService;
import zw.gov.mohcc.impilo.coverage.core.AuthorisationService.LineDecision;
import zw.gov.mohcc.impilo.coverage.core.AuthorisationService.LineInput;
import zw.gov.mohcc.impilo.coverage.domain.AuthorisationEntity;

import java.util.List;
import java.util.UUID;

/** Authorisation endpoints (spec §17). */
@RestController
@RequestMapping("/internal/v1/coverage/authorisations")
public class AuthorisationController {

    private final AuthorisationService service;

    public AuthorisationController(AuthorisationService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<List<AuthorisationView>> list(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestParam(name = "member_cpid", required = false) String memberCpid,
            @RequestParam(name = "status", required = false) String status) {
        UUID tid = UUID.fromString(tenantId);
        List<AuthorisationEntity> rows = memberCpid != null && !memberCpid.isBlank()
                ? service.forMember(tid, memberCpid)
                : status != null && !status.isBlank()
                    ? service.queue(tid, status.trim().toUpperCase())
                    : List.of();
        return ResponseEntity.ok(rows.stream().map(a -> AuthorisationView.of(a, service.lines(a.getId()))).toList());
    }

    @GetMapping("/{id}")
    public ResponseEntity<AuthorisationView> get(
            @RequestHeader("X-Tenant-ID") String tenantId, @PathVariable UUID id) {
        UUID tid = UUID.fromString(tenantId);
        AuthorisationEntity a = service.get(tid, id);
        return ResponseEntity.ok(AuthorisationView.of(a, service.lines(id)));
    }

    @PostMapping
    public ResponseEntity<AuthorisationView> create(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestHeader("X-Pod-ID") String podId,
            @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId,
            @Valid @RequestBody CreateAuthorisationRequest req) {
        UUID tid = UUID.fromString(tenantId);
        List<LineInput> lines = req.lines() == null ? List.of() : req.lines().stream()
                .map(l -> new LineInput(l.benefitCode(), l.serviceCode(), l.description(),
                        l.quantityRequested(), l.estimatedAmount()))
                .toList();
        AuthorisationEntity a = service.create(tid, podId, req.coverageId(), req.authType(),
                req.requestingProviderId(), req.facilityId(), req.referralId(), req.diagnosisSystem(),
                req.diagnosisCode(), req.clinicalJustification(), req.urgency(), req.proposedServiceDate(),
                lines, CorrelationIds.fromHeader(correlationId));
        return ResponseEntity.status(HttpStatus.CREATED).body(AuthorisationView.of(a, service.lines(a.getId())));
    }

    @PostMapping("/{id}/submit")
    public ResponseEntity<AuthorisationView> submit(
            @RequestHeader("X-Tenant-ID") String tenantId, @RequestHeader("X-Pod-ID") String podId,
            @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId,
            @PathVariable UUID id) {
        return transition(tenantId, podId, correlationId, id, "SUBMITTED", null);
    }

    @PostMapping("/{id}/acknowledge")
    public ResponseEntity<AuthorisationView> acknowledge(
            @RequestHeader("X-Tenant-ID") String tenantId, @RequestHeader("X-Pod-ID") String podId,
            @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId,
            @PathVariable UUID id, @RequestBody(required = false) TransitionAuthorisationRequest req) {
        return transition(tenantId, podId, correlationId, id, "ACKNOWLEDGED", req);
    }

    @PostMapping("/{id}/request-information")
    public ResponseEntity<AuthorisationView> requestInfo(
            @RequestHeader("X-Tenant-ID") String tenantId, @RequestHeader("X-Pod-ID") String podId,
            @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId,
            @PathVariable UUID id, @RequestBody(required = false) TransitionAuthorisationRequest req) {
        return transition(tenantId, podId, correlationId, id, "INFORMATION_REQUESTED", req);
    }

    @PostMapping("/{id}/withdraw")
    public ResponseEntity<AuthorisationView> withdraw(
            @RequestHeader("X-Tenant-ID") String tenantId, @RequestHeader("X-Pod-ID") String podId,
            @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId,
            @PathVariable UUID id, @RequestBody(required = false) TransitionAuthorisationRequest req) {
        return transition(tenantId, podId, correlationId, id, "WITHDRAWN", req);
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<AuthorisationView> cancel(
            @RequestHeader("X-Tenant-ID") String tenantId, @RequestHeader("X-Pod-ID") String podId,
            @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId,
            @PathVariable UUID id, @RequestBody(required = false) TransitionAuthorisationRequest req) {
        return transition(tenantId, podId, correlationId, id, "CANCELLED", req);
    }

    @PostMapping("/{id}/decision")
    public ResponseEntity<AuthorisationView> decide(
            @RequestHeader("X-Tenant-ID") String tenantId, @RequestHeader("X-Pod-ID") String podId,
            @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId,
            @PathVariable UUID id, @RequestBody DecideAuthorisationRequest req) {
        UUID tid = UUID.fromString(tenantId);
        List<LineDecision> decisions = req.lines() == null ? List.of() : req.lines().stream()
                .map(l -> new LineDecision(l.lineId(), l.outcome(), l.quantityApproved(), l.approvedAmount(), l.reason()))
                .toList();
        AuthorisationEntity a = service.decide(tid, podId, id, decisions, req.reviewerId(),
                req.validityFrom(), req.validityTo(), req.conditions(), CorrelationIds.fromHeader(correlationId));
        return ResponseEntity.ok(AuthorisationView.of(a, service.lines(id)));
    }

    private ResponseEntity<AuthorisationView> transition(String tenantId, String podId, String correlationId,
                                                         UUID id, String target, TransitionAuthorisationRequest req) {
        UUID tid = UUID.fromString(tenantId);
        AuthorisationEntity a = service.transition(tid, podId, id, target,
                req != null ? req.reviewerId() : null, req != null ? req.note() : null,
                CorrelationIds.fromHeader(correlationId));
        return ResponseEntity.ok(AuthorisationView.of(a, service.lines(id)));
    }
}
