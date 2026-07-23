package zw.gov.mohcc.impilo.telemonitoring.api.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.telemonitoring.api.dto.DeviceAssignmentDtos.ActivateRequest;
import zw.gov.mohcc.impilo.telemonitoring.api.dto.DeviceAssignmentDtos.AssignRequest;
import zw.gov.mohcc.impilo.telemonitoring.api.dto.DeviceAssignmentDtos.AssignmentResponse;
import zw.gov.mohcc.impilo.telemonitoring.api.dto.DeviceAssignmentDtos.LifecycleRequest;
import zw.gov.mohcc.impilo.telemonitoring.api.dto.DeviceAssignmentDtos.ResolvedAssignmentResponse;
import zw.gov.mohcc.impilo.telemonitoring.core.DeviceAssignmentService;
import zw.gov.mohcc.impilo.telemonitoring.core.TelemonitoringDomainException;

import java.util.List;
import java.util.UUID;

/**
 * Device-assignment surface (OF-B24). Two faces on one aggregate:
 *
 * <ul>
 *   <li><b>Public lifecycle</b> ({@code /v1/device-assignments}) — reached through the
 *       experience-BFF with the caller's trust context: assign under an ACTIVE plan
 *       (fail-closed on unverifiable devices), activate with training confirmation,
 *       suspend/resume/return/lost — all reason-bound.</li>
 *   <li><b>The assignment gate</b>
 *       ({@code GET /internal/v1/device-assignments/by-device/{deviceRef}}) — the
 *       service-to-service lookup OF-B25 ingestion calls per reading to attribute a
 *       device to {patient, plan} and learn the calibration posture. Asset-registry
 *       problems are stamped on the answer, never blocking it.</li>
 * </ul>
 */
@RestController
public class DeviceAssignmentController {

    private final DeviceAssignmentService assignmentService;

    public DeviceAssignmentController(DeviceAssignmentService assignmentService) {
        this.assignmentService = assignmentService;
    }

    // ── Public lifecycle ──

    @PostMapping("/v1/device-assignments")
    public ResponseEntity<AssignmentResponse> assign(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @Valid @RequestBody AssignRequest request,
            HttpServletRequest http) {
        String assignedBy = request.assignedBy() != null && !request.assignedBy().isBlank()
                ? request.assignedBy() : actorId;
        var assignment = assignmentService.assign(new DeviceAssignmentService.AssignCommand(
                tenantId,
                request.planId(),
                request.deviceRef(),
                request.assetRef(),
                request.assignmentType(),
                assignedBy,
                request.notes()), http);
        return ResponseEntity.status(HttpStatus.CREATED).body(AssignmentResponse.from(assignment));
    }

    @GetMapping("/v1/device-assignments/{assignmentId}")
    public AssignmentResponse get(@PathVariable UUID assignmentId) {
        return AssignmentResponse.from(assignmentService.getAssignment(assignmentId));
    }

    @GetMapping("/v1/device-assignments")
    public List<AssignmentResponse> list(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @RequestParam(required = false) UUID planId,
            @RequestParam(required = false) String patientCpid) {
        List<zw.gov.mohcc.impilo.telemonitoring.persistence.entity.DeviceAssignmentEntity> rows;
        if (planId != null) {
            rows = assignmentService.listByPlan(tenantId, planId);
        } else if (patientCpid != null && !patientCpid.isBlank()) {
            rows = assignmentService.listByPatient(tenantId, patientCpid);
        } else {
            throw new TelemonitoringDomainException("TM_INVALID_REQUEST", 400,
                    "Provide planId or patientCpid to list device assignments");
        }
        return rows.stream().map(AssignmentResponse::from).toList();
    }

    @PostMapping("/v1/device-assignments/{assignmentId}/activate")
    public AssignmentResponse activate(
            @PathVariable UUID assignmentId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @RequestBody ActivateRequest request) {
        String actor = request.actor() != null && !request.actor().isBlank() ? request.actor() : actorId;
        return AssignmentResponse.from(assignmentService.activate(
                assignmentId, actor, request.trainingConfirmed(), request.notes()));
    }

    @PostMapping("/v1/device-assignments/{assignmentId}/suspend")
    public AssignmentResponse suspend(
            @PathVariable UUID assignmentId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @Valid @RequestBody LifecycleRequest request) {
        return AssignmentResponse.from(assignmentService.suspend(
                assignmentId, request.reason(), actor(request, actorId)));
    }

    @PostMapping("/v1/device-assignments/{assignmentId}/resume")
    public AssignmentResponse resume(
            @PathVariable UUID assignmentId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @Valid @RequestBody LifecycleRequest request) {
        return AssignmentResponse.from(assignmentService.resume(
                assignmentId, request.reason(), actor(request, actorId)));
    }

    @PostMapping("/v1/device-assignments/{assignmentId}/return")
    public AssignmentResponse markReturned(
            @PathVariable UUID assignmentId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @Valid @RequestBody LifecycleRequest request) {
        return AssignmentResponse.from(assignmentService.markReturned(
                assignmentId, request.reason(), actor(request, actorId)));
    }

    @PostMapping("/v1/device-assignments/{assignmentId}/lost")
    public AssignmentResponse markLost(
            @PathVariable UUID assignmentId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @Valid @RequestBody LifecycleRequest request) {
        return AssignmentResponse.from(assignmentService.markLost(
                assignmentId, request.reason(), actor(request, actorId)));
    }

    // ── The assignment gate (OF-B25 consumer) ──

    @GetMapping("/internal/v1/device-assignments/by-device/{deviceRef}")
    public ResolvedAssignmentResponse resolveByDevice(
            @PathVariable String deviceRef,
            HttpServletRequest http) {
        return ResolvedAssignmentResponse.from(assignmentService.resolveAssignment(deviceRef, http));
    }

    private static String actor(LifecycleRequest request, String actorId) {
        return request.actor() != null && !request.actor().isBlank() ? request.actor() : actorId;
    }
}
