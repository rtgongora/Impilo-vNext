package zw.gov.mohcc.impilo.coverage.api;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.coverage.api.dto.ClaimV2Dtos.*;
import zw.gov.mohcc.impilo.coverage.core.ClaimAdjudicationService;
import zw.gov.mohcc.impilo.coverage.core.ClaimAdjudicationService.LineInput;
import zw.gov.mohcc.impilo.coverage.core.ClaimAdjudicationService.ScrubResult;
import zw.gov.mohcc.impilo.coverage.domain.ClaimEntity;
import zw.gov.mohcc.impilo.coverage.domain.ClaimLineEntity;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Claims v2 — line-level adjudication, EOB, lineage (spec §19-§20).
 * Distinct from the legacy {@code /internal/v1/coverage/claims} filing path (kept for COSTA).
 */
@RestController
@RequestMapping("/internal/v1/coverage/v2/claims")
public class ClaimV2Controller {

    private final ClaimAdjudicationService service;

    public ClaimV2Controller(ClaimAdjudicationService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<ClaimView> create(
            @RequestHeader("X-Tenant-ID") String tenantId, @RequestHeader("X-Pod-ID") String podId,
            @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId,
            @Valid @RequestBody CreateClaimRequest req) {
        UUID tid = UUID.fromString(tenantId);
        List<LineInput> lines = req.lines() == null ? List.of() : req.lines().stream()
                .map(l -> new LineInput(l.benefitCode(), l.serviceCode(), l.description(), l.quantity(), l.submittedAmount()))
                .toList();
        ClaimEntity c = service.createDraft(tid, podId, req.coverageId(), req.claimType(), req.facilityId(),
                req.providerId(), req.encounterId(), req.authorisationId(), req.referralId(), lines,
                CorrelationIds.fromHeader(correlationId));
        return ResponseEntity.status(HttpStatus.CREATED).body(ClaimView.of(c, service.lines(c.getId())));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ClaimView> get(@RequestHeader("X-Tenant-ID") String tenantId, @PathVariable UUID id) {
        UUID tid = UUID.fromString(tenantId);
        ClaimEntity c = service.get(tid, id);
        return ResponseEntity.ok(ClaimView.of(c, service.lines(id)));
    }

    @PostMapping("/{id}/scrub")
    public ResponseEntity<ScrubView> scrub(@RequestHeader("X-Tenant-ID") String tenantId, @PathVariable UUID id) {
        ScrubResult r = service.scrub(UUID.fromString(tenantId), id);
        return ResponseEntity.ok(new ScrubView(r.pass(), r.blocking(), r.warnings()));
    }

    @PostMapping("/{id}/submit")
    public ResponseEntity<ClaimView> submit(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId,
            @PathVariable UUID id) {
        ClaimEntity c = service.submit(UUID.fromString(tenantId), id, CorrelationIds.fromHeader(correlationId));
        return ResponseEntity.ok(ClaimView.of(c, service.lines(id)));
    }

    @PostMapping("/{id}/adjudicate")
    public ResponseEntity<ClaimView> adjudicate(
            @RequestHeader("X-Tenant-ID") String tenantId, @RequestHeader("X-Pod-ID") String podId,
            @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId,
            @PathVariable UUID id) {
        ClaimEntity c = service.adjudicate(UUID.fromString(tenantId), podId, id, CorrelationIds.fromHeader(correlationId));
        return ResponseEntity.ok(ClaimView.of(c, service.lines(id)));
    }

    @GetMapping("/{id}/events")
    public ResponseEntity<List<ClaimEventView>> events(@RequestHeader("X-Tenant-ID") String tenantId, @PathVariable UUID id) {
        service.get(UUID.fromString(tenantId), id); // tenant guard
        return ResponseEntity.ok(service.events(id).stream().map(ClaimEventView::of).toList());
    }

    @GetMapping("/{id}/explanation-of-benefits")
    public ResponseEntity<EobView> eob(@RequestHeader("X-Tenant-ID") String tenantId, @PathVariable UUID id) {
        UUID tid = UUID.fromString(tenantId);
        ClaimEntity c = service.get(tid, id);
        List<ClaimLineEntity> lines = service.lines(id);
        List<EobLine> eobLines = lines.stream().map(l -> new EobLine(
                l.getDescription() != null ? l.getDescription() : l.getBenefitCode(),
                l.getSubmittedAmount(), l.getAllowedAmount(), l.getApprovedAmount(), l.getDeniedAmount(),
                l.getPatientResponsibility(), l.getLineStatus(), plainLanguage(l.getLineStatus(), l.getReasonCodes()))).toList();
        BigDecimal charged = sum(lines, ClaimLineEntity::getSubmittedAmount);
        EobView eob = new EobView(c.getClaimNumber(), c.getFacilityId(), c.getProviderId(), c.getStatus(),
                charged, c.getApprovedAmount(), c.getPatientResponsibility(), c.getSubmittedAt(), eobLines,
                "You may appeal a denied or partially paid claim within the plan's appeal window.");
        return ResponseEntity.ok(eob);
    }

    @PostMapping("/{id}/void")
    public ResponseEntity<ClaimView> voidClaim(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId,
            @PathVariable UUID id, @RequestBody(required = false) VoidRequest req) {
        ClaimEntity c = service.voidClaim(UUID.fromString(tenantId), id, req != null ? req.reason() : null,
                CorrelationIds.fromHeader(correlationId));
        return ResponseEntity.ok(ClaimView.of(c, service.lines(id)));
    }

    @PostMapping("/{id}/reverse")
    public ResponseEntity<ClaimView> reverse(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId,
            @PathVariable UUID id, @RequestBody(required = false) VoidRequest req) {
        ClaimEntity c = service.reverse(UUID.fromString(tenantId), id, req != null ? req.reason() : null,
                CorrelationIds.fromHeader(correlationId));
        return ResponseEntity.ok(ClaimView.of(c, service.lines(id)));
    }

    @PostMapping("/{id}/replace")
    public ResponseEntity<ClaimView> replace(
            @RequestHeader("X-Tenant-ID") String tenantId, @RequestHeader("X-Pod-ID") String podId,
            @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId,
            @PathVariable UUID id) {
        ClaimEntity c = service.replace(UUID.fromString(tenantId), podId, id, CorrelationIds.fromHeader(correlationId));
        return ResponseEntity.status(HttpStatus.CREATED).body(ClaimView.of(c, service.lines(c.getId())));
    }

    private static BigDecimal sum(List<ClaimLineEntity> lines, java.util.function.Function<ClaimLineEntity, BigDecimal> f) {
        return lines.stream().map(f).filter(java.util.Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static String plainLanguage(String status, String reasonCodes) {
        return switch (status) {
            case "APPROVED" -> "This service was covered by your plan.";
            case "PARTIALLY_APPROVED" -> "This service was partly covered; you owe the balance.";
            case "DENIED" -> reasonCodes != null && reasonCodes.contains("AUTHORISATION_REQUIRED")
                    ? "Not paid — prior authorisation was required and not found."
                    : reasonCodes != null && reasonCodes.contains("BENEFIT_NOT_COVERED")
                        ? "Not paid — this service is not covered by your plan."
                        : reasonCodes != null && reasonCodes.contains("BENEFIT_LIMIT_EXCEEDED")
                            ? "Not paid — your benefit limit for this service is exhausted."
                            : "This service was not paid by your plan.";
            default -> "Awaiting adjudication.";
        };
    }
}
