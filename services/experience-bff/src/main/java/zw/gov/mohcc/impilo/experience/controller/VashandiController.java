package zw.gov.mohcc.impilo.experience.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.vashandi.VashandiDtos;
import zw.gov.mohcc.impilo.experience.vashandi.VashandiService;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Vashandi operational workforce BFF — proxies to vashandi-workforce-service with OPA precheck and audit.
 */
@RestController
@RequestMapping("/internal/v1/vashandi")
public class VashandiController {

    private final VashandiService vashandiService;

    public VashandiController(VashandiService vashandiService) {
        this.vashandiService = vashandiService;
    }

    @PostMapping("/precheck")
    public ResponseEntity<Map<String, Object>> precheck(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.ACTOR_ID) String actorId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.PROVIDER_ID, required = false) String providerId,
            @RequestHeader(value = CompanionHeaders.FACILITY_ID, required = false) String facilityId,
            @RequestHeader(value = CompanionHeaders.PURPOSE_OF_USE, required = false) String purposeOfUse,
            @RequestBody VashandiDtos.PrecheckRequest body) {
        return wrap(vashandiService.precheck(tenantId, actorId, providerId, hasFacility(facilityId),
                requestId, correlationId, purposeOfUse, body));
    }

    @GetMapping("/workforce-profiles")
    public ResponseEntity<Map<String, Object>> listWorkforceProfiles(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.ACTOR_ID) String actorId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.PROVIDER_ID, required = false) String providerId,
            @RequestHeader(value = CompanionHeaders.FACILITY_ID, required = false) String facilityId,
            @RequestHeader(value = CompanionHeaders.PURPOSE_OF_USE, required = false) String purposeOfUse,
            @RequestParam Map<String, String> queryParams) {
        return wrap(vashandiService.proxyGet("WORKFORCE_PROFILE_LIST", "/workforce-profiles", queryParams,
                tenantId, actorId, providerId, hasFacility(facilityId), requestId, correlationId, purposeOfUse,
                "vashandi.workforce_profile.created"));
    }

    @GetMapping("/workforce-profiles/{id}")
    public ResponseEntity<Map<String, Object>> getWorkforceProfile(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.ACTOR_ID) String actorId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.PROVIDER_ID, required = false) String providerId,
            @RequestHeader(value = CompanionHeaders.FACILITY_ID, required = false) String facilityId,
            @RequestHeader(value = CompanionHeaders.PURPOSE_OF_USE, required = false) String purposeOfUse,
            @PathVariable String id) {
        return wrap(vashandiService.proxyGet("WORKFORCE_PROFILE_VIEW", "/workforce-profiles/" + id, Map.of(),
                tenantId, actorId, providerId, hasFacility(facilityId), requestId, correlationId, purposeOfUse,
                "vashandi.workforce_profile.created"));
    }

    @PostMapping("/workforce-profiles/reconcile")
    public ResponseEntity<Map<String, Object>> reconcileWorkforceProfile(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.ACTOR_ID) String actorId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.PROVIDER_ID, required = false) String providerId,
            @RequestHeader(value = CompanionHeaders.FACILITY_ID, required = false) String facilityId,
            @RequestHeader(value = CompanionHeaders.PURPOSE_OF_USE, required = false) String purposeOfUse,
            @RequestBody Map<String, Object> body) {
        return wrap(vashandiService.proxyPost("WORKFORCE_PROFILE_RECONCILE", "/workforce-profiles/reconcile", body,
                tenantId, actorId, providerId, hasFacility(facilityId), requestId, correlationId, purposeOfUse,
                "vashandi.workforce_profile.reconciled"));
    }

    @PostMapping("/workforce-profiles/import-bridge")
    public ResponseEntity<Map<String, Object>> importWorkforceBridge(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.ACTOR_ID) String actorId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.PROVIDER_ID, required = false) String providerId,
            @RequestHeader(value = CompanionHeaders.FACILITY_ID, required = false) String facilityId,
            @RequestHeader(value = CompanionHeaders.PURPOSE_OF_USE, required = false) String purposeOfUse,
            @RequestBody Map<String, Object> body) {
        return wrap(vashandiService.proxyPost("WORKFORCE_PROFILE_IMPORT_BRIDGE", "/workforce-profiles/import-bridge", body,
                tenantId, actorId, providerId, hasFacility(facilityId), requestId, correlationId, purposeOfUse,
                "vashandi.workforce_profile.created"));
    }

    @GetMapping("/assignments")
    public ResponseEntity<Map<String, Object>> listAssignments(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.ACTOR_ID) String actorId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.PROVIDER_ID, required = false) String providerId,
            @RequestHeader(value = CompanionHeaders.FACILITY_ID, required = false) String facilityId,
            @RequestHeader(value = CompanionHeaders.PURPOSE_OF_USE, required = false) String purposeOfUse,
            @RequestParam Map<String, String> queryParams) {
        return wrap(vashandiService.proxyGet("ASSIGNMENT_LIST", "/assignments", queryParams,
                tenantId, actorId, providerId, hasFacility(facilityId), requestId, correlationId, purposeOfUse,
                "vashandi.assignment.requested"));
    }

    @PostMapping("/assignments")
    public ResponseEntity<Map<String, Object>> createAssignment(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.ACTOR_ID) String actorId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.PROVIDER_ID, required = false) String providerId,
            @RequestHeader(value = CompanionHeaders.FACILITY_ID, required = false) String facilityId,
            @RequestHeader(value = CompanionHeaders.PURPOSE_OF_USE, required = false) String purposeOfUse,
            @RequestBody Map<String, Object> body) {
        return wrap(vashandiService.proxyPost("ASSIGNMENT_CREATE", "/assignments", body,
                tenantId, actorId, providerId, hasFacility(facilityId), requestId, correlationId, purposeOfUse,
                "vashandi.assignment.requested"));
    }

    @GetMapping("/assignments/{id}")
    public ResponseEntity<Map<String, Object>> getAssignment(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.ACTOR_ID) String actorId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.PROVIDER_ID, required = false) String providerId,
            @RequestHeader(value = CompanionHeaders.FACILITY_ID, required = false) String facilityId,
            @RequestHeader(value = CompanionHeaders.PURPOSE_OF_USE, required = false) String purposeOfUse,
            @PathVariable String id) {
        return wrap(vashandiService.proxyGet("ASSIGNMENT_LIST", "/assignments/" + id, Map.of(),
                tenantId, actorId, providerId, hasFacility(facilityId), requestId, correlationId, purposeOfUse,
                "vashandi.assignment.requested"));
    }

    @PatchMapping("/assignments/{id}")
    public ResponseEntity<Map<String, Object>> updateAssignment(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.ACTOR_ID) String actorId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.PROVIDER_ID, required = false) String providerId,
            @RequestHeader(value = CompanionHeaders.FACILITY_ID, required = false) String facilityId,
            @RequestHeader(value = CompanionHeaders.PURPOSE_OF_USE, required = false) String purposeOfUse,
            @PathVariable String id,
            @RequestBody Map<String, Object> body) {
        return wrap(vashandiService.proxyPatch("ASSIGNMENT_UPDATE", "/assignments/" + id, body,
                tenantId, actorId, providerId, hasFacility(facilityId), requestId, correlationId, purposeOfUse,
                "vashandi.assignment.requested"));
    }

    @PostMapping("/assignments/{id}/precheck")
    public ResponseEntity<Map<String, Object>> precheckAssignment(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.ACTOR_ID) String actorId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.PROVIDER_ID, required = false) String providerId,
            @RequestHeader(value = CompanionHeaders.FACILITY_ID, required = false) String facilityId,
            @RequestHeader(value = CompanionHeaders.PURPOSE_OF_USE, required = false) String purposeOfUse,
            @PathVariable String id,
            @RequestBody(required = false) Map<String, Object> body) {
        return wrap(vashandiService.proxyPost("ASSIGNMENT_PRECHECK", "/assignments/" + id + "/precheck",
                body != null ? body : Map.of(), tenantId, actorId, providerId, hasFacility(facilityId),
                requestId, correlationId, purposeOfUse, "vashandi.assignment.prechecked"));
    }

    @PostMapping("/assignments/{id}/approve")
    public ResponseEntity<Map<String, Object>> approveAssignment(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.ACTOR_ID) String actorId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.PROVIDER_ID, required = false) String providerId,
            @RequestHeader(value = CompanionHeaders.FACILITY_ID, required = false) String facilityId,
            @RequestHeader(value = CompanionHeaders.PURPOSE_OF_USE, required = false) String purposeOfUse,
            @PathVariable String id,
            @RequestBody(required = false) Map<String, Object> body) {
        return wrap(vashandiService.proxyPost("ASSIGNMENT_APPROVE", "/assignments/" + id + "/approve",
                body != null ? body : Map.of(), tenantId, actorId, providerId, hasFacility(facilityId),
                requestId, correlationId, purposeOfUse, "vashandi.assignment.approved"));
    }

    @PostMapping("/assignments/{id}/activate")
    public ResponseEntity<Map<String, Object>> activateAssignment(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.ACTOR_ID) String actorId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.PROVIDER_ID, required = false) String providerId,
            @RequestHeader(value = CompanionHeaders.FACILITY_ID, required = false) String facilityId,
            @RequestHeader(value = CompanionHeaders.PURPOSE_OF_USE, required = false) String purposeOfUse,
            @PathVariable String id,
            @RequestBody(required = false) Map<String, Object> body) {
        return wrap(vashandiService.proxyPost("ASSIGNMENT_ACTIVATE", "/assignments/" + id + "/activate",
                body != null ? body : Map.of(), tenantId, actorId, providerId, hasFacility(facilityId),
                requestId, correlationId, purposeOfUse, "vashandi.assignment.activated"));
    }

    @PostMapping("/assignments/{id}/suspend")
    public ResponseEntity<Map<String, Object>> suspendAssignment(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.ACTOR_ID) String actorId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.PROVIDER_ID, required = false) String providerId,
            @RequestHeader(value = CompanionHeaders.FACILITY_ID, required = false) String facilityId,
            @RequestHeader(value = CompanionHeaders.PURPOSE_OF_USE, required = false) String purposeOfUse,
            @PathVariable String id,
            @RequestBody(required = false) Map<String, Object> body) {
        return wrap(vashandiService.proxyPost("ASSIGNMENT_SUSPEND", "/assignments/" + id + "/suspend",
                body != null ? body : Map.of(), tenantId, actorId, providerId, hasFacility(facilityId),
                requestId, correlationId, purposeOfUse, "vashandi.assignment.suspended"));
    }

    @PostMapping("/assignments/{id}/end")
    public ResponseEntity<Map<String, Object>> endAssignment(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.ACTOR_ID) String actorId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.PROVIDER_ID, required = false) String providerId,
            @RequestHeader(value = CompanionHeaders.FACILITY_ID, required = false) String facilityId,
            @RequestHeader(value = CompanionHeaders.PURPOSE_OF_USE, required = false) String purposeOfUse,
            @PathVariable String id,
            @RequestBody(required = false) Map<String, Object> body) {
        return wrap(vashandiService.proxyPost("ASSIGNMENT_END", "/assignments/" + id + "/end",
                body != null ? body : Map.of(), tenantId, actorId, providerId, hasFacility(facilityId),
                requestId, correlationId, purposeOfUse, "vashandi.assignment.ended"));
    }

    @GetMapping("/rosters")
    public ResponseEntity<Map<String, Object>> listRosters(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.ACTOR_ID) String actorId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.PROVIDER_ID, required = false) String providerId,
            @RequestHeader(value = CompanionHeaders.FACILITY_ID, required = false) String facilityId,
            @RequestHeader(value = CompanionHeaders.PURPOSE_OF_USE, required = false) String purposeOfUse,
            @RequestParam Map<String, String> queryParams) {
        return wrap(vashandiService.proxyGet("ROSTER_LIST", "/rosters", queryParams,
                tenantId, actorId, providerId, hasFacility(facilityId), requestId, correlationId, purposeOfUse,
                "vashandi.roster.created"));
    }

    @PostMapping("/rosters")
    public ResponseEntity<Map<String, Object>> createRoster(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.ACTOR_ID) String actorId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.PROVIDER_ID, required = false) String providerId,
            @RequestHeader(value = CompanionHeaders.FACILITY_ID, required = false) String facilityId,
            @RequestHeader(value = CompanionHeaders.PURPOSE_OF_USE, required = false) String purposeOfUse,
            @RequestBody Map<String, Object> body) {
        return wrap(vashandiService.proxyPost("ROSTER_CREATE", "/rosters", body,
                tenantId, actorId, providerId, hasFacility(facilityId), requestId, correlationId, purposeOfUse,
                "vashandi.roster.created"));
    }

    @GetMapping("/rosters/{id}")
    public ResponseEntity<Map<String, Object>> getRoster(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.ACTOR_ID) String actorId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.PROVIDER_ID, required = false) String providerId,
            @RequestHeader(value = CompanionHeaders.FACILITY_ID, required = false) String facilityId,
            @RequestHeader(value = CompanionHeaders.PURPOSE_OF_USE, required = false) String purposeOfUse,
            @PathVariable String id) {
        return wrap(vashandiService.proxyGet("ROSTER_LIST", "/rosters/" + id, Map.of(),
                tenantId, actorId, providerId, hasFacility(facilityId), requestId, correlationId, purposeOfUse,
                "vashandi.roster.created"));
    }

    @PostMapping("/rosters/{id}/approve")
    public ResponseEntity<Map<String, Object>> approveRoster(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.ACTOR_ID) String actorId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.PROVIDER_ID, required = false) String providerId,
            @RequestHeader(value = CompanionHeaders.FACILITY_ID, required = false) String facilityId,
            @RequestHeader(value = CompanionHeaders.PURPOSE_OF_USE, required = false) String purposeOfUse,
            @PathVariable String id,
            @RequestBody(required = false) Map<String, Object> body) {
        return wrap(vashandiService.proxyPost("ROSTER_APPROVE", "/rosters/" + id + "/approve",
                body != null ? body : Map.of(), tenantId, actorId, providerId, hasFacility(facilityId),
                requestId, correlationId, purposeOfUse, "vashandi.roster.approved"));
    }

    @PostMapping("/shifts")
    public ResponseEntity<Map<String, Object>> createShift(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.ACTOR_ID) String actorId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.PROVIDER_ID, required = false) String providerId,
            @RequestHeader(value = CompanionHeaders.FACILITY_ID, required = false) String facilityId,
            @RequestHeader(value = CompanionHeaders.PURPOSE_OF_USE, required = false) String purposeOfUse,
            @RequestBody Map<String, Object> body) {
        return wrap(vashandiService.proxyPost("SHIFT_CREATE", "/shifts", body,
                tenantId, actorId, providerId, hasFacility(facilityId), requestId, correlationId, purposeOfUse,
                "vashandi.shift.created"));
    }

    @PatchMapping("/shifts/{id}")
    public ResponseEntity<Map<String, Object>> updateShift(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.ACTOR_ID) String actorId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.PROVIDER_ID, required = false) String providerId,
            @RequestHeader(value = CompanionHeaders.FACILITY_ID, required = false) String facilityId,
            @RequestHeader(value = CompanionHeaders.PURPOSE_OF_USE, required = false) String purposeOfUse,
            @PathVariable String id,
            @RequestBody Map<String, Object> body) {
        return wrap(vashandiService.proxyPatch("SHIFT_UPDATE", "/shifts/" + id, body,
                tenantId, actorId, providerId, hasFacility(facilityId), requestId, correlationId, purposeOfUse,
                "vashandi.shift.updated"));
    }

    @PostMapping("/attendance/check-in")
    public ResponseEntity<Map<String, Object>> checkIn(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.ACTOR_ID) String actorId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.PROVIDER_ID, required = false) String providerId,
            @RequestHeader(value = CompanionHeaders.FACILITY_ID, required = false) String facilityId,
            @RequestHeader(value = CompanionHeaders.PURPOSE_OF_USE, required = false) String purposeOfUse,
            @RequestBody Map<String, Object> body) {
        return wrap(vashandiService.proxyPost("ATTENDANCE_CHECK_IN", "/attendance/check-in", body,
                tenantId, actorId, providerId, hasFacility(facilityId), requestId, correlationId, purposeOfUse,
                "vashandi.attendance.checked_in"));
    }

    @PostMapping("/attendance/check-out")
    public ResponseEntity<Map<String, Object>> checkOut(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.ACTOR_ID) String actorId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.PROVIDER_ID, required = false) String providerId,
            @RequestHeader(value = CompanionHeaders.FACILITY_ID, required = false) String facilityId,
            @RequestHeader(value = CompanionHeaders.PURPOSE_OF_USE, required = false) String purposeOfUse,
            @RequestBody Map<String, Object> body) {
        return wrap(vashandiService.proxyPost("ATTENDANCE_CHECK_OUT", "/attendance/check-out", body,
                tenantId, actorId, providerId, hasFacility(facilityId), requestId, correlationId, purposeOfUse,
                "vashandi.attendance.checked_out"));
    }

    @PostMapping("/attendance/supervisor-confirm")
    public ResponseEntity<Map<String, Object>> supervisorConfirmAttendance(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.ACTOR_ID) String actorId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.PROVIDER_ID, required = false) String providerId,
            @RequestHeader(value = CompanionHeaders.FACILITY_ID, required = false) String facilityId,
            @RequestHeader(value = CompanionHeaders.PURPOSE_OF_USE, required = false) String purposeOfUse,
            @RequestBody Map<String, Object> body) {
        return wrap(vashandiService.proxyPost("ATTENDANCE_SUPERVISOR_CONFIRM", "/attendance/supervisor-confirm", body,
                tenantId, actorId, providerId, hasFacility(facilityId), requestId, correlationId, purposeOfUse,
                "vashandi.attendance.supervisor_confirmed"));
    }

    @GetMapping("/attendance")
    public ResponseEntity<Map<String, Object>> listAttendance(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.ACTOR_ID) String actorId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.PROVIDER_ID, required = false) String providerId,
            @RequestHeader(value = CompanionHeaders.FACILITY_ID, required = false) String facilityId,
            @RequestHeader(value = CompanionHeaders.PURPOSE_OF_USE, required = false) String purposeOfUse,
            @RequestParam Map<String, String> queryParams) {
        return wrap(vashandiService.proxyGet("ATTENDANCE_LIST", "/attendance", queryParams,
                tenantId, actorId, providerId, hasFacility(facilityId), requestId, correlationId, purposeOfUse,
                "vashandi.attendance.checked_in"));
    }

    @GetMapping("/availability")
    public ResponseEntity<Map<String, Object>> listAvailability(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.ACTOR_ID) String actorId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.PROVIDER_ID, required = false) String providerId,
            @RequestHeader(value = CompanionHeaders.FACILITY_ID, required = false) String facilityId,
            @RequestHeader(value = CompanionHeaders.PURPOSE_OF_USE, required = false) String purposeOfUse,
            @RequestParam Map<String, String> queryParams) {
        return wrap(vashandiService.proxyGet("AVAILABILITY_LIST", "/availability", queryParams,
                tenantId, actorId, providerId, hasFacility(facilityId), requestId, correlationId, purposeOfUse,
                "vashandi.leave.updated"));
    }

    @PostMapping("/leave")
    public ResponseEntity<Map<String, Object>> createLeave(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.ACTOR_ID) String actorId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.PROVIDER_ID, required = false) String providerId,
            @RequestHeader(value = CompanionHeaders.FACILITY_ID, required = false) String facilityId,
            @RequestHeader(value = CompanionHeaders.PURPOSE_OF_USE, required = false) String purposeOfUse,
            @RequestBody Map<String, Object> body) {
        return wrap(vashandiService.proxyPost("LEAVE_CREATE", "/leave", body,
                tenantId, actorId, providerId, hasFacility(facilityId), requestId, correlationId, purposeOfUse,
                "vashandi.leave.created"));
    }

    @PatchMapping("/leave/{id}")
    public ResponseEntity<Map<String, Object>> updateLeave(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.ACTOR_ID) String actorId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.PROVIDER_ID, required = false) String providerId,
            @RequestHeader(value = CompanionHeaders.FACILITY_ID, required = false) String facilityId,
            @RequestHeader(value = CompanionHeaders.PURPOSE_OF_USE, required = false) String purposeOfUse,
            @PathVariable String id,
            @RequestBody Map<String, Object> body) {
        return wrap(vashandiService.proxyPatch("LEAVE_UPDATE", "/leave/" + id, body,
                tenantId, actorId, providerId, hasFacility(facilityId), requestId, correlationId, purposeOfUse,
                "vashandi.leave.updated"));
    }

    @GetMapping("/access-risks")
    public ResponseEntity<Map<String, Object>> listAccessRisks(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.ACTOR_ID) String actorId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.PROVIDER_ID, required = false) String providerId,
            @RequestHeader(value = CompanionHeaders.FACILITY_ID, required = false) String facilityId,
            @RequestHeader(value = CompanionHeaders.PURPOSE_OF_USE, required = false) String purposeOfUse,
            @RequestParam Map<String, String> queryParams) {
        return wrap(vashandiService.proxyGet("ACCESS_RISK_LIST", "/access-risks", queryParams,
                tenantId, actorId, providerId, hasFacility(facilityId), requestId, correlationId, purposeOfUse,
                "vashandi.access_risk.detected"));
    }

    @PostMapping("/access-risks/scan")
    public ResponseEntity<Map<String, Object>> scanAccessRisks(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.ACTOR_ID) String actorId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.PROVIDER_ID, required = false) String providerId,
            @RequestHeader(value = CompanionHeaders.FACILITY_ID, required = false) String facilityId,
            @RequestHeader(value = CompanionHeaders.PURPOSE_OF_USE, required = false) String purposeOfUse,
            @RequestBody(required = false) Map<String, Object> body) {
        return wrap(vashandiService.proxyPost("ACCESS_RISK_SCAN", "/access-risks/scan",
                body != null ? body : Map.of(), tenantId, actorId, providerId, hasFacility(facilityId),
                requestId, correlationId, purposeOfUse, "vashandi.access_risk.detected"));
    }

    @PostMapping("/access-risks/{id}/resolve")
    public ResponseEntity<Map<String, Object>> resolveAccessRisk(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.ACTOR_ID) String actorId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.PROVIDER_ID, required = false) String providerId,
            @RequestHeader(value = CompanionHeaders.FACILITY_ID, required = false) String facilityId,
            @RequestHeader(value = CompanionHeaders.PURPOSE_OF_USE, required = false) String purposeOfUse,
            @PathVariable String id,
            @RequestBody(required = false) Map<String, Object> body) {
        return wrap(vashandiService.proxyPost("ACCESS_RISK_RESOLVE", "/access-risks/" + id + "/resolve",
                body != null ? body : Map.of(), tenantId, actorId, providerId, hasFacility(facilityId),
                requestId, correlationId, purposeOfUse, "vashandi.access_risk.resolved"));
    }

    @PostMapping("/access-risks/{id}/revoke-access")
    public ResponseEntity<Map<String, Object>> revokeAccess(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.ACTOR_ID) String actorId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.PROVIDER_ID, required = false) String providerId,
            @RequestHeader(value = CompanionHeaders.FACILITY_ID, required = false) String facilityId,
            @RequestHeader(value = CompanionHeaders.PURPOSE_OF_USE, required = false) String purposeOfUse,
            @PathVariable String id,
            @RequestBody(required = false) Map<String, Object> body) {
        return wrap(vashandiService.proxyPost("ACCESS_RISK_REVOKE_ACCESS", "/access-risks/" + id + "/revoke-access",
                body != null ? body : Map.of(), tenantId, actorId, providerId, hasFacility(facilityId),
                requestId, correlationId, purposeOfUse, "vashandi.access_revoked"));
    }

    @GetMapping("/analytics/staffing")
    public ResponseEntity<Map<String, Object>> analyticsStaffing(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.ACTOR_ID) String actorId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.PROVIDER_ID, required = false) String providerId,
            @RequestHeader(value = CompanionHeaders.FACILITY_ID, required = false) String facilityId,
            @RequestHeader(value = CompanionHeaders.PURPOSE_OF_USE, required = false) String purposeOfUse,
            @RequestParam Map<String, String> queryParams) {
        return wrap(vashandiService.proxyGet("ANALYTICS_STAFFING", "/analytics/staffing", queryParams,
                tenantId, actorId, providerId, hasFacility(facilityId), requestId, correlationId, purposeOfUse,
                "vashandi.workforce_profile.created"));
    }

    @GetMapping("/analytics/roster-coverage")
    public ResponseEntity<Map<String, Object>> analyticsRosterCoverage(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.ACTOR_ID) String actorId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.PROVIDER_ID, required = false) String providerId,
            @RequestHeader(value = CompanionHeaders.FACILITY_ID, required = false) String facilityId,
            @RequestHeader(value = CompanionHeaders.PURPOSE_OF_USE, required = false) String purposeOfUse,
            @RequestParam Map<String, String> queryParams) {
        return wrap(vashandiService.proxyGet("ANALYTICS_ROSTER_COVERAGE", "/analytics/roster-coverage", queryParams,
                tenantId, actorId, providerId, hasFacility(facilityId), requestId, correlationId, purposeOfUse,
                "vashandi.roster.created"));
    }

    @GetMapping("/analytics/attendance")
    public ResponseEntity<Map<String, Object>> analyticsAttendance(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.ACTOR_ID) String actorId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.PROVIDER_ID, required = false) String providerId,
            @RequestHeader(value = CompanionHeaders.FACILITY_ID, required = false) String facilityId,
            @RequestHeader(value = CompanionHeaders.PURPOSE_OF_USE, required = false) String purposeOfUse,
            @RequestParam Map<String, String> queryParams) {
        return wrap(vashandiService.proxyGet("ANALYTICS_ATTENDANCE", "/analytics/attendance", queryParams,
                tenantId, actorId, providerId, hasFacility(facilityId), requestId, correlationId, purposeOfUse,
                "vashandi.attendance.checked_in"));
    }

    @GetMapping("/analytics/vacancies")
    public ResponseEntity<Map<String, Object>> analyticsVacancies(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.ACTOR_ID) String actorId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.PROVIDER_ID, required = false) String providerId,
            @RequestHeader(value = CompanionHeaders.FACILITY_ID, required = false) String facilityId,
            @RequestHeader(value = CompanionHeaders.PURPOSE_OF_USE, required = false) String purposeOfUse,
            @RequestParam Map<String, String> queryParams) {
        return wrap(vashandiService.proxyGet("ANALYTICS_VACANCIES", "/analytics/vacancies", queryParams,
                tenantId, actorId, providerId, hasFacility(facilityId), requestId, correlationId, purposeOfUse,
                "vashandi.workforce_profile.created"));
    }

    @GetMapping("/analytics/access-risk")
    public ResponseEntity<Map<String, Object>> analyticsAccessRisk(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.ACTOR_ID) String actorId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.PROVIDER_ID, required = false) String providerId,
            @RequestHeader(value = CompanionHeaders.FACILITY_ID, required = false) String facilityId,
            @RequestHeader(value = CompanionHeaders.PURPOSE_OF_USE, required = false) String purposeOfUse,
            @RequestParam Map<String, String> queryParams) {
        return wrap(vashandiService.proxyGet("ANALYTICS_ACCESS_RISK", "/analytics/access-risk", queryParams,
                tenantId, actorId, providerId, hasFacility(facilityId), requestId, correlationId, purposeOfUse,
                "vashandi.access_risk.detected"));
    }

    private static boolean hasFacility(String facilityId) {
        return facilityId != null && !facilityId.isBlank();
    }

    private static ResponseEntity<Map<String, Object>> wrap(VashandiDtos.ActionResponse response) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", !"denied".equals(response.status()) && !"upstream_unavailable".equals(response.status()));
        body.put("status", response.status());
        body.put("requestId", response.requestId());
        body.put("correlationId", response.correlationId());
        body.put("integrationStatus", response.integrationStatus() != null ? response.integrationStatus().name() : null);
        body.put("integrationMessage", response.integrationMessage());
        body.put("auditStatus", response.auditStatus());
        body.put("auditEventId", response.auditEventId());
        body.put("auditCorrelationId", response.auditCorrelationId());
        body.put("policyDecision", response.policyDecision());
        body.put("friendlyTitle", response.friendlyTitle());
        body.put("friendlyMessage", response.friendlyMessage());
        body.put("blockedReason", response.blockedReason());
        body.put("data", response.data());
        HttpStatus httpStatus = switch (response.status()) {
            case "denied" -> HttpStatus.FORBIDDEN;
            case "upstream_unavailable" -> HttpStatus.BAD_GATEWAY;
            default -> HttpStatus.OK;
        };
        return ResponseEntity.status(httpStatus).body(body);
    }
}
