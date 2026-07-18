package zw.gov.mohcc.impilo.indawo.api;

import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.RequestContext;
import zw.gov.mohcc.impilo.companion.context.RequestContextHolder;
import zw.gov.mohcc.impilo.indawo.api.dto.SurveillanceDtos;
import zw.gov.mohcc.impilo.indawo.core.SurveillanceService;
import zw.gov.mohcc.impilo.indawo.domain.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Public-health surveillance / outbreak / case-investigation / field-team API
 * (Indawo SoR, net-new). Tenant-scoped; authz via existing ext_authz; policies
 * {@code INDAWO-MODE}, {@code INSPECTION} (spec-only, track P).
 */
@RestController
@RequestMapping("/internal/v1/surveillance")
public class SurveillanceController {

    private static final Logger log = LoggerFactory.getLogger(SurveillanceController.class);

    private final SurveillanceService service;

    public SurveillanceController(SurveillanceService service) {
        this.service = service;
    }

    // ── Place-mode summary (Indawo cockpit context) ─────────────────────────

    @GetMapping("/place-mode/summary")
    public ResponseEntity<Map<String, Object>> placeModeSummary() {
        RequestContext ctx = RequestContextHolder.require();
        UUID tenantId = UUID.fromString(ctx.tenantId());
        return ResponseEntity.ok(Map.of("data", service.placeModeSummary(tenantId)));
    }

    // ── Alerts ──────────────────────────────────────────────────────────────

    @GetMapping("/alerts")
    public ResponseEntity<Map<String, Object>> listAlerts(@RequestParam(required = false) String status) {
        RequestContext ctx = RequestContextHolder.require();
        UUID tenantId = UUID.fromString(ctx.tenantId());
        List<SurveillanceDtos.AlertResponse> out = service.listAlerts(tenantId, status).stream()
                .map(SurveillanceController::toAlert).toList();
        return ResponseEntity.ok(Map.of("data", out));
    }

    @PostMapping("/alerts")
    public ResponseEntity<Map<String, Object>> createAlert(
            @Valid @RequestBody SurveillanceDtos.CreateAlertRequest req) {
        RequestContext ctx = RequestContextHolder.require();
        UUID tenantId = UUID.fromString(ctx.tenantId());
        SurveillanceAlertEntity e = new SurveillanceAlertEntity();
        e.setSiteId(req.siteId());
        e.setSignalType(req.signalType());
        e.setConditionCode(req.conditionCode());
        if (req.severity() != null) e.setSeverity(req.severity());
        e.setCaseCount(req.caseCount() != null ? req.caseCount() : 0);
        e.setDescription(req.description());
        SurveillanceAlertEntity saved = service.createAlert(tenantId, actor(ctx), e);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("data", toAlert(saved)));
    }

    // ── Outbreaks ───────────────────────────────────────────────────────────

    @GetMapping("/outbreaks")
    public ResponseEntity<Map<String, Object>> listOutbreaks(@RequestParam(required = false) String status) {
        RequestContext ctx = RequestContextHolder.require();
        UUID tenantId = UUID.fromString(ctx.tenantId());
        List<SurveillanceDtos.OutbreakResponse> out = service.listOutbreaks(tenantId, status).stream()
                .map(SurveillanceController::toOutbreak).toList();
        return ResponseEntity.ok(Map.of("data", out));
    }

    @PostMapping("/outbreaks")
    public ResponseEntity<Map<String, Object>> createOutbreak(
            @Valid @RequestBody SurveillanceDtos.CreateOutbreakRequest req) {
        RequestContext ctx = RequestContextHolder.require();
        UUID tenantId = UUID.fromString(ctx.tenantId());
        OutbreakEntity e = new OutbreakEntity();
        e.setConditionCode(req.conditionCode());
        e.setName(req.name());
        if (req.severity() != null) e.setSeverity(req.severity());
        e.setAreaDescription(req.areaDescription());
        e.setPrimarySiteId(req.primarySiteId());
        OutbreakEntity saved = service.createOutbreak(tenantId, actor(ctx), e);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("data", toOutbreak(saved)));
    }

    @PostMapping("/outbreaks/{outbreakId}/status")
    public ResponseEntity<Map<String, Object>> updateOutbreakStatus(
            @PathVariable UUID outbreakId, @RequestBody Map<String, String> body) {
        RequestContext ctx = RequestContextHolder.require();
        UUID tenantId = UUID.fromString(ctx.tenantId());
        String status = body.get("status");
        OutbreakEntity saved = service.updateOutbreakStatus(tenantId, outbreakId, status, actor(ctx));
        return ResponseEntity.ok(Map.of("data", toOutbreak(saved)));
    }

    // ── Case investigations ─────────────────────────────────────────────────

    @GetMapping("/cases")
    public ResponseEntity<Map<String, Object>> listCases(@RequestParam(required = false) UUID outbreakId) {
        RequestContext ctx = RequestContextHolder.require();
        UUID tenantId = UUID.fromString(ctx.tenantId());
        List<SurveillanceDtos.CaseResponse> out = service.listCases(tenantId, outbreakId).stream()
                .map(SurveillanceController::toCase).toList();
        return ResponseEntity.ok(Map.of("data", out));
    }

    @PostMapping("/cases")
    public ResponseEntity<Map<String, Object>> createCase(
            @Valid @RequestBody SurveillanceDtos.CreateCaseRequest req) {
        RequestContext ctx = RequestContextHolder.require();
        UUID tenantId = UUID.fromString(ctx.tenantId());
        CaseInvestigationEntity e = new CaseInvestigationEntity();
        e.setOutbreakId(req.outbreakId());
        e.setAlertId(req.alertId());
        e.setSiteId(req.siteId());
        e.setCpid(req.cpid());
        if (req.classification() != null) e.setClassification(req.classification());
        if (req.onsetDate() != null) e.setOnsetDate(LocalDate.parse(req.onsetDate()));
        e.setFindings(req.findings());
        e.setInvestigatedBy(actor(ctx));
        CaseInvestigationEntity saved = service.createCase(tenantId, actor(ctx), e);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("data", toCase(saved)));
    }

    // ── Field teams + deployments ───────────────────────────────────────────

    @GetMapping("/field-teams")
    public ResponseEntity<Map<String, Object>> listTeams() {
        RequestContext ctx = RequestContextHolder.require();
        UUID tenantId = UUID.fromString(ctx.tenantId());
        List<SurveillanceDtos.TeamResponse> out = service.listTeams(tenantId).stream()
                .map(SurveillanceController::toTeam).toList();
        return ResponseEntity.ok(Map.of("data", out));
    }

    @PostMapping("/field-teams")
    public ResponseEntity<Map<String, Object>> createTeam(
            @Valid @RequestBody SurveillanceDtos.CreateTeamRequest req) {
        RequestContext ctx = RequestContextHolder.require();
        UUID tenantId = UUID.fromString(ctx.tenantId());
        FieldTeamEntity e = new FieldTeamEntity();
        e.setName(req.name());
        if (req.teamType() != null) e.setTeamType(req.teamType());
        e.setLeadHealthId(req.leadHealthId()); // actor-plane HID
        e.setMemberCount(req.memberCount() != null ? req.memberCount() : 0);
        e.setBaseSiteId(req.baseSiteId());
        FieldTeamEntity saved = service.createTeam(tenantId, actor(ctx), e);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("data", toTeam(saved)));
    }

    @PostMapping("/field-teams/{teamId}/deploy")
    public ResponseEntity<Map<String, Object>> deploy(
            @PathVariable UUID teamId, @RequestBody SurveillanceDtos.DeployRequest req) {
        RequestContext ctx = RequestContextHolder.require();
        UUID tenantId = UUID.fromString(ctx.tenantId());
        FieldTeamDeploymentEntity saved = service.deployTeam(
                tenantId, actor(ctx), teamId, req.outbreakId(), req.siteId(), req.objective());
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("data", toDeployment(saved)));
    }

    @PostMapping("/deployments/{deploymentId}/recall")
    public ResponseEntity<Map<String, Object>> recall(@PathVariable UUID deploymentId) {
        RequestContext ctx = RequestContextHolder.require();
        UUID tenantId = UUID.fromString(ctx.tenantId());
        FieldTeamDeploymentEntity saved = service.recallDeployment(tenantId, deploymentId, actor(ctx));
        return ResponseEntity.ok(Map.of("data", toDeployment(saved)));
    }

    // ── Error mapping ───────────────────────────────────────────────────────

    /**
     * Validation failures (null/unknown status, illegal transition, missing entity)
     * are client errors, not server faults. Surface them as 400 rather than 500.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleBadRequest(IllegalArgumentException ex) {
        log.warn("Surveillance bad request: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", "BAD_REQUEST", "message",
                        ex.getMessage() != null ? ex.getMessage() : "Invalid request"));
    }

    // ── Mappers ─────────────────────────────────────────────────────────────

    private static SurveillanceDtos.AlertResponse toAlert(SurveillanceAlertEntity e) {
        return new SurveillanceDtos.AlertResponse(e.getAlertId().toString(),
                e.getSiteId() != null ? e.getSiteId().toString() : null, e.getSignalType(),
                e.getConditionCode(), e.getSeverity(), e.getStatus(), e.getCaseCount(),
                e.getDescription(), e.getDetectedAt() != null ? e.getDetectedAt().toString() : null);
    }

    private static SurveillanceDtos.OutbreakResponse toOutbreak(OutbreakEntity e) {
        return new SurveillanceDtos.OutbreakResponse(e.getOutbreakId().toString(), e.getConditionCode(),
                e.getName(), e.getSeverity(), e.getStatus(), e.getAreaDescription(),
                e.getCaseCount(), e.getDeathCount(),
                e.getDeclaredAt() != null ? e.getDeclaredAt().toString() : null);
    }

    private static SurveillanceDtos.CaseResponse toCase(CaseInvestigationEntity e) {
        return new SurveillanceDtos.CaseResponse(e.getCaseId().toString(),
                e.getOutbreakId() != null ? e.getOutbreakId().toString() : null,
                e.getCpid(), e.getClassification(), e.getStatus(),
                e.getOnsetDate() != null ? e.getOnsetDate().toString() : null, e.getInvestigatedBy());
    }

    private static SurveillanceDtos.TeamResponse toTeam(FieldTeamEntity e) {
        return new SurveillanceDtos.TeamResponse(e.getTeamId().toString(), e.getName(), e.getTeamType(),
                e.getStatus(), e.getLeadHealthId(), e.getMemberCount()); // actor-plane HID
    }

    private static SurveillanceDtos.DeploymentResponse toDeployment(FieldTeamDeploymentEntity e) {
        return new SurveillanceDtos.DeploymentResponse(e.getDeploymentId().toString(), e.getTeamId().toString(),
                e.getOutbreakId() != null ? e.getOutbreakId().toString() : null,
                e.getSiteId() != null ? e.getSiteId().toString() : null, e.getStatus(), e.getObjective());
    }

    private static String actor(RequestContext ctx) {
        if (ctx != null && ctx.principal() != null
                && ctx.principal().getName() != null && !ctx.principal().getName().isBlank()) {
            return ctx.principal().getName();
        }
        return "system";
    }
}
