package zw.gov.mohcc.impilo.telemonitoring.api.controller;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.telemonitoring.api.dto.MonitoringPlanDtos.AmendThresholdsRequest;
import zw.gov.mohcc.impilo.telemonitoring.api.dto.MonitoringPlanDtos.ApproveRequest;
import zw.gov.mohcc.impilo.telemonitoring.api.dto.MonitoringPlanDtos.CreatePlanRequest;
import zw.gov.mohcc.impilo.telemonitoring.api.dto.MonitoringPlanDtos.LifecycleRequest;
import zw.gov.mohcc.impilo.telemonitoring.api.dto.MonitoringPlanDtos.PlanResponse;
import zw.gov.mohcc.impilo.telemonitoring.api.dto.MonitoringPlanDtos.ThresholdProfileResponse;
import zw.gov.mohcc.impilo.telemonitoring.core.MonitoringPlanService;

import java.util.List;
import java.util.UUID;

/**
 * Monitoring-plan API (OF-B22). Reached through the experience-BFF with the caller's trust
 * context; the acting clinician defaults to the trust-plane actor header, with explicit
 * body fields taking precedence for delegated flows.
 */
@RestController
@RequestMapping("/v1/monitoring-plans")
public class MonitoringPlanController {

    private final MonitoringPlanService planService;

    public MonitoringPlanController(MonitoringPlanService planService) {
        this.planService = planService;
    }

    @PostMapping
    public ResponseEntity<PlanResponse> create(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @Valid @RequestBody CreatePlanRequest request) {
        var plan = planService.createDraft(new MonitoringPlanService.CreatePlanCommand(
                tenantId,
                request.patientCpid(),
                request.programmeCode(),
                request.orosOrderId(),
                request.clinicalIndication(),
                request.monitoringSetting(),
                request.reviewCadence(),
                request.careTeamJson(),
                request.initialThresholdsJson(),
                request.suggestedBy(),
                request.suggestedSystemVersion(),
                actorId), true);
        return ResponseEntity.status(HttpStatus.CREATED).body(PlanResponse.from(plan));
    }

    @GetMapping("/{planId}")
    public PlanResponse get(@PathVariable UUID planId) {
        return PlanResponse.from(planService.getPlan(planId));
    }

    @GetMapping
    public List<PlanResponse> listByPatient(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @RequestParam String patientCpid) {
        return planService.listByPatient(tenantId, patientCpid).stream()
                .map(PlanResponse::from)
                .toList();
    }

    @PostMapping("/{planId}/approve")
    public PlanResponse approve(
            @PathVariable UUID planId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @RequestBody(required = false) ApproveRequest request) {
        String approvedBy = request != null && request.approvedBy() != null && !request.approvedBy().isBlank()
                ? request.approvedBy() : actorId;
        return PlanResponse.from(planService.approve(planId, approvedBy));
    }

    @PostMapping("/{planId}/suspend")
    public PlanResponse suspend(
            @PathVariable UUID planId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @RequestBody LifecycleRequest request) {
        return PlanResponse.from(planService.suspend(planId, request.reason(), actor(request, actorId)));
    }

    @PostMapping("/{planId}/resume")
    public PlanResponse resume(
            @PathVariable UUID planId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @RequestBody LifecycleRequest request) {
        return PlanResponse.from(planService.resume(planId, request.reason(), actor(request, actorId)));
    }

    @PostMapping("/{planId}/complete")
    public PlanResponse complete(
            @PathVariable UUID planId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @RequestBody LifecycleRequest request) {
        return PlanResponse.from(planService.complete(planId, request.reason(), actor(request, actorId)));
    }

    @PostMapping("/{planId}/cancel")
    public PlanResponse cancel(
            @PathVariable UUID planId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @RequestBody LifecycleRequest request) {
        return PlanResponse.from(planService.cancel(planId, request.reason(), actor(request, actorId)));
    }

    @PostMapping("/{planId}/threshold-profiles")
    public ResponseEntity<ThresholdProfileResponse> amendThresholds(
            @PathVariable UUID planId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @Valid @RequestBody AmendThresholdsRequest request) {
        String amendedBy = request.amendedBy() != null && !request.amendedBy().isBlank()
                ? request.amendedBy() : actorId;
        var profile = planService.amendThresholds(planId, new MonitoringPlanService.AmendThresholdsCommand(
                request.parametersJson(), request.reason(), amendedBy, request.expectedCurrentVersion()));
        return ResponseEntity.status(HttpStatus.CREATED).body(ThresholdProfileResponse.from(profile));
    }

    @GetMapping("/{planId}/threshold-profiles")
    public List<ThresholdProfileResponse> thresholdVersions(@PathVariable UUID planId) {
        return planService.listThresholdVersions(planId).stream()
                .map(ThresholdProfileResponse::from)
                .toList();
    }

    private static String actor(LifecycleRequest request, String actorId) {
        return request.actor() != null && !request.actor().isBlank() ? request.actor() : actorId;
    }
}
