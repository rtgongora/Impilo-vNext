package zw.gov.mohcc.impilo.governance.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.governance.core.BootstrapGovernanceService;
import zw.gov.mohcc.impilo.governance.core.PlatformOriginService;
import zw.gov.mohcc.impilo.governance.persistence.AccessRequestEntity;
import zw.gov.mohcc.impilo.governance.persistence.CountryOperationEntity;
import zw.gov.mohcc.impilo.governance.persistence.NationalAppointmentEntity;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Platform Origin governance API (IATG WS-A). Every mutating endpoint follows
 * the initiate → two-person approve → execute lifecycle enforced by
 * {@link PlatformOriginService} and {@code TwoPersonApprovalService}; nothing
 * here mutates platform truth without an APPROVED PLATFORM_ACTION access
 * request carrying two distinct approvals.
 *
 * <p>Authz via existing ext_authz (tshepo-authz V031 policy rules:
 * PLATFORM_ORIGIN_ADMINISTRATOR full access, NATIONAL_ADMINISTRATOR reads).</p>
 */
@RestController
@RequestMapping("/internal/v1/platform-origin")
public class PlatformOriginController {

    private final PlatformOriginService platformOriginService;
    private final BootstrapGovernanceService bootstrapGovernanceService;

    public PlatformOriginController(PlatformOriginService platformOriginService,
                                    BootstrapGovernanceService bootstrapGovernanceService) {
        this.platformOriginService = platformOriginService;
        this.bootstrapGovernanceService = bootstrapGovernanceService;
    }

    /** Initiate CREATE_COUNTRY_OPERATION — returns the PENDING PLATFORM_ACTION access request. */
    @PostMapping("/country-operations")
    public ResponseEntity<ApiResponse<Map<String, Object>>> initiateCountryOperation(
            @RequestBody Map<String, Object> body) {
        AccessRequestEntity request = platformOriginService.initiateCountryOperation(tenant(), actor(), body);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(actionView(request), corr()));
    }

    /** Record one approver's decision; transitions to APPROVED once the two-person rule is satisfied. */
    @PostMapping("/actions/{accessRequestId}/approve")
    public ResponseEntity<ApiResponse<Map<String, Object>>> approve(
            @PathVariable UUID accessRequestId,
            @RequestBody(required = false) Map<String, Object> body) {
        Map<String, Object> b = body != null ? body : Map.of();
        Map<String, Object> out = platformOriginService.approve(
                accessRequestId,
                str(b.getOrDefault("approverUserId", actor())),
                str(b.get("approverRole")),
                str(b.get("decision")),
                str(b.get("note")));
        return ResponseEntity.ok(ApiResponse.ok(out, corr()));
    }

    /** Execute an APPROVED platform action exactly once. */
    @PostMapping("/actions/{accessRequestId}/execute")
    public ResponseEntity<ApiResponse<Map<String, Object>>> execute(@PathVariable UUID accessRequestId) {
        return ResponseEntity.ok(ApiResponse.ok(
                platformOriginService.execute(accessRequestId, actor()), corr()));
    }

    @GetMapping("/country-operations")
    public ResponseEntity<ApiResponse<List<CountryOperationEntity>>> listCountryOperations() {
        return ResponseEntity.ok(ApiResponse.ok(platformOriginService.listCountryOperations(), corr()));
    }

    /** Country operation detail plus a read-through of the tenant's bootstrap governance state. */
    @GetMapping("/country-operations/{id}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getCountryOperation(@PathVariable UUID id) {
        CountryOperationEntity operation = platformOriginService.getCountryOperation(id);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("countryOperation", operation);
        out.put("bootstrap", bootstrapGovernanceService.stateWithCountryOperation(operation.getTenantId()));
        return ResponseEntity.ok(ApiResponse.ok(out, corr()));
    }

    @GetMapping("/country-operations/{id}/appointments")
    public ResponseEntity<ApiResponse<List<NationalAppointmentEntity>>> listAppointments(
            @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(platformOriginService.listAppointments(id), corr()));
    }

    /** Initiate APPOINT_NATIONAL_ADMIN — returns the PENDING PLATFORM_ACTION access request. */
    @PostMapping("/country-operations/{id}/appointments")
    public ResponseEntity<ApiResponse<Map<String, Object>>> initiateAppointment(
            @PathVariable UUID id, @RequestBody Map<String, Object> body) {
        AccessRequestEntity request = platformOriginService.initiateAppointment(tenant(), actor(), id, body);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(actionView(request), corr()));
    }

    /** Initiate REVOKE_APPOINTMENT (also two-person) — returns the PENDING PLATFORM_ACTION access request. */
    @DeleteMapping("/country-operations/{id}/appointments/{appointmentId}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> initiateRevocation(
            @PathVariable UUID id, @PathVariable UUID appointmentId,
            @RequestBody(required = false) Map<String, Object> body) {
        String reason = body != null ? str(body.get("reason")) : null;
        AccessRequestEntity request = platformOriginService.initiateRevocation(
                tenant(), actor(), id, appointmentId, reason);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ApiResponse.ok(actionView(request), corr()));
    }

    private static Map<String, Object> actionView(AccessRequestEntity request) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("accessRequestId", request.getId().toString());
        out.put("requestType", request.getRequestType());
        out.put("status", request.getStatus());
        out.put("approvalsRequired", request.getApprovalsRequired());
        return out;
    }

    private UUID tenant() {
        UUID t = TrustContextHolder.require().tenantId();
        if (t == null) throw new IllegalArgumentException("Missing X-Tenant-ID trust header");
        return t;
    }

    private String corr() {
        TrustContext ctx = TrustContextHolder.get();
        return ctx != null && ctx.correlationId() != null ? ctx.correlationId().toString() : "";
    }

    private String actor() {
        TrustContext ctx = TrustContextHolder.get();
        return ctx != null && ctx.actorId() != null ? ctx.actorId() : "system";
    }

    private static String str(Object value) {
        return value == null ? null : value.toString();
    }
}
