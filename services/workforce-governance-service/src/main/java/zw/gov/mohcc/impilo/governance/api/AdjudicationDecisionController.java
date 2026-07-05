package zw.gov.mohcc.impilo.governance.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.governance.core.AdjudicationDecisionService;
import zw.gov.mohcc.impilo.governance.core.AdjudicationDecisionService.RecordDecisionCommand;
import zw.gov.mohcc.impilo.governance.persistence.AdjudicationDecisionEntity;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * IATG adjudication decision record API (WGV SoR for adjudication outcomes).
 *
 * <p>Decisions are APPEND-ONLY: POST always inserts a new row; superseding a
 * prior decision is done by posting a new one for the same subject. GET
 * resolves the latest-effective decision plus the full preserved history.</p>
 *
 * <p>Trust context: served under {@code /internal/v1/*}. Depending on branch
 * convergence the shared {@code TrustContextFilter} may or may not yet be
 * registered for this prefix, so trust values are read from
 * {@link TrustContextHolder} when populated and fall back to the raw trust
 * headers otherwise. Authz via existing ext_authz at the mesh edge.</p>
 */
@RestController
@RequestMapping("/internal/v1/workforce-governance/adjudications")
public class AdjudicationDecisionController {

    private final AdjudicationDecisionService service;

    public AdjudicationDecisionController(AdjudicationDecisionService service) {
        this.service = service;
    }

    public record RecordDecisionRequest(
            String workflowInstanceId,
            @NotBlank String subjectType,
            @NotBlank String subjectRef,
            @NotBlank String decision,
            @NotBlank String reason,
            String authorityUserId,
            String authorityRole,
            String effectiveAt,
            String expiresAt,
            Map<String, Object> permissions,
            Map<String, Object> restrictions,
            List<String> evidenceRefs,
            String accessRequestId
    ) {}

    public record DecisionResponse(
            String id,
            String workflowInstanceId,
            String subjectType,
            String subjectRef,
            String decision,
            String reason,
            String authorityUserId,
            String authorityRole,
            String effectiveAt,
            String expiresAt,
            Map<String, Object> permissions,
            Map<String, Object> restrictions,
            List<String> evidenceRefs,
            String accessRequestId,
            String createdAt,
            String createdBy
    ) {}

    /** Latest-effective decision (may be null) plus full append-only history. */
    public record DecisionQueryResponse(
            String subjectType,
            String subjectRef,
            DecisionResponse latestEffective,
            List<DecisionResponse> history
    ) {}

    @PostMapping("/decisions")
    public ResponseEntity<ApiResponse<DecisionResponse>> recordDecision(
            @Valid @RequestBody RecordDecisionRequest req, HttpServletRequest http) {
        RecordDecisionCommand cmd = new RecordDecisionCommand(
                parseUuid(req.workflowInstanceId(), "workflowInstanceId"),
                req.subjectType(),
                req.subjectRef(),
                req.decision(),
                req.reason(),
                req.authorityUserId(),
                req.authorityRole(),
                parseInstant(req.effectiveAt(), "effectiveAt"),
                parseInstant(req.expiresAt(), "expiresAt"),
                req.permissions(),
                req.restrictions(),
                req.evidenceRefs(),
                parseUuid(req.accessRequestId(), "accessRequestId"));
        AdjudicationDecisionEntity saved = service.record(tenant(http), actor(http), cmd, corr(http));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(toResponse(saved), corr(http)));
    }

    @GetMapping("/decisions")
    public ResponseEntity<ApiResponse<DecisionQueryResponse>> queryDecisions(
            @RequestParam("subjectType") String subjectType,
            @RequestParam("subjectRef") String subjectRef,
            HttpServletRequest http) {
        UUID tenantId = tenant(http);
        List<AdjudicationDecisionEntity> history = service.history(tenantId, subjectType, subjectRef);
        DecisionResponse latest = service.latestEffective(tenantId, subjectType, subjectRef, Instant.now())
                .map(AdjudicationDecisionController::toResponse)
                .orElse(null);
        DecisionQueryResponse body = new DecisionQueryResponse(
                subjectType.trim().toUpperCase(),
                subjectRef.trim(),
                latest,
                history.stream().map(AdjudicationDecisionController::toResponse).toList());
        return ResponseEntity.ok(ApiResponse.ok(body, corr(http)));
    }

    private static DecisionResponse toResponse(AdjudicationDecisionEntity e) {
        return new DecisionResponse(
                e.getId().toString(),
                e.getWorkflowInstanceId() != null ? e.getWorkflowInstanceId().toString() : null,
                e.getSubjectType(),
                e.getSubjectRef(),
                e.getDecision(),
                e.getReason(),
                e.getAuthorityUserId(),
                e.getAuthorityRole(),
                e.getEffectiveAt() != null ? e.getEffectiveAt().toString() : null,
                e.getExpiresAt() != null ? e.getExpiresAt().toString() : null,
                e.getPermissions(),
                e.getRestrictions(),
                e.getEvidenceRefs(),
                e.getAccessRequestId() != null ? e.getAccessRequestId().toString() : null,
                e.getCreatedAt() != null ? e.getCreatedAt().toString() : null,
                e.getCreatedBy());
    }

    private static UUID parseUuid(String value, String field) {
        if (value == null || value.isBlank()) return null;
        try {
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid UUID for " + field + ": " + value);
        }
    }

    private static Instant parseInstant(String value, String field) {
        if (value == null || value.isBlank()) return null;
        try {
            return Instant.parse(value.trim());
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(
                    "Invalid timestamp for " + field + " (expected ISO-8601 instant, e.g. 2026-07-05T00:00:00Z): " + value);
        }
    }

    // ── Trust helpers: prefer TrustContextHolder, tolerate the filter not yet
    //    being registered for /internal/v1/* by falling back to raw headers. ──

    private static UUID tenant(HttpServletRequest http) {
        TrustContext ctx = TrustContextHolder.get();
        if (ctx != null && ctx.tenantId() != null) return ctx.tenantId();
        UUID fromHeader = parseUuid(http.getHeader(TrustContext.H_TENANT_ID), "X-Tenant-ID");
        if (fromHeader == null) throw new IllegalArgumentException("Missing or invalid X-Tenant-ID trust header");
        return fromHeader;
    }

    private static String actor(HttpServletRequest http) {
        TrustContext ctx = TrustContextHolder.get();
        if (ctx != null && ctx.actorId() != null && !ctx.actorId().isBlank()) return ctx.actorId();
        String header = http.getHeader(TrustContext.H_ACTOR_ID);
        return header != null && !header.isBlank() ? header : "system";
    }

    private static String corr(HttpServletRequest http) {
        TrustContext ctx = TrustContextHolder.get();
        if (ctx != null && ctx.correlationId() != null) return ctx.correlationId().toString();
        String header = http.getHeader(TrustContext.H_CORRELATION_ID);
        return header != null ? header : "";
    }
}
