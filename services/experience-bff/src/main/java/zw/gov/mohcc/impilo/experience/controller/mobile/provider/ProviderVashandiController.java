package zw.gov.mohcc.impilo.experience.controller.mobile.provider;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.vashandi.VashandiDtos;
import zw.gov.mohcc.impilo.experience.vashandi.VashandiService;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Mobile provider Vashandi routes — roster, attendance, availability proxied through governed BFF.
 */
@RestController
@RequestMapping("/internal/v1/mobile/provider/vashandi")
public class ProviderVashandiController {

    private final VashandiService vashandiService;

    public ProviderVashandiController(VashandiService vashandiService) {
        this.vashandiService = vashandiService;
    }

    @GetMapping("/roster")
    public ResponseEntity<Map<String, Object>> myRoster(
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

    @GetMapping("/attendance")
    public ResponseEntity<Map<String, Object>> myAttendance(
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

    @GetMapping("/availability")
    public ResponseEntity<Map<String, Object>> myAvailability(
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
        body.put("data", response.data());
        return ResponseEntity.ok(body);
    }
}
