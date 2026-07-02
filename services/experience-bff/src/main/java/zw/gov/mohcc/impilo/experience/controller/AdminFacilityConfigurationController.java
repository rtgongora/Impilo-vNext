package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.HttpStatusCodeException;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.TusoServiceClient;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Admin proxy for the TUSO Facility Configuration Console (service points, workspaces, derived queue
 * definitions, setup checklist, downstream readiness). Composes nothing and persists nothing — it forwards
 * to TUSO (the facility source of truth) and wraps responses in the shell envelope.
 *
 * <p><b>Policy.</b> All routes sit under {@code /internal/v1/admin/**}, which {@code SecurityConfig}
 * restricts to admin roles (FACILITY_ADMIN / SYSTEM_ADMIN / DEVELOPER / SUPER_ADMIN). Facility-configuration
 * mutation is therefore not exposed to ordinary users. Downstream 4xx (validation/conflict) are propagated
 * with their status via {@code HttpStatusCodeException}, not masked as 502. TUSO owns queue definitions;
 * this is not a queue editor — queue definitions are read-only here.</p>
 */
@RestController
@RequestMapping("/internal/v1/admin/facilities")
public class AdminFacilityConfigurationController {

    private static final Logger log = LoggerFactory.getLogger(AdminFacilityConfigurationController.class);

    private final TusoServiceClient tusoClient;

    public AdminFacilityConfigurationController(TusoServiceClient tusoClient) {
        this.tusoClient = tusoClient;
    }

    // ── Reads ──────────────────────────────────────────────────────────

    @GetMapping("/{facilityId}/configuration")
    public ResponseEntity<Map<String, Object>> configuration(
            @PathVariable long facilityId,
            @RequestHeader(value = CompanionHeaders.REQUEST_ID, required = false) String requestId,
            @RequestHeader(value = CompanionHeaders.CORRELATION_ID, required = false) String correlationId) {
        return proxyRead(() -> tusoClient.getFacilityConfiguration(facilityId), requestId, correlationId,
                "CONFIGURATION_UNAVAILABLE", "Unable to load facility configuration from TUSO", facilityId);
    }

    @GetMapping("/{facilityId}/setup-checklist")
    public ResponseEntity<Map<String, Object>> setupChecklist(
            @PathVariable long facilityId,
            @RequestHeader(value = CompanionHeaders.REQUEST_ID, required = false) String requestId,
            @RequestHeader(value = CompanionHeaders.CORRELATION_ID, required = false) String correlationId) {
        return proxyRead(() -> tusoClient.getFacilitySetupChecklist(facilityId), requestId, correlationId,
                "SETUP_CHECKLIST_UNAVAILABLE", "Unable to load facility setup checklist from TUSO", facilityId);
    }

    @GetMapping("/{facilityId}/service-points")
    public ResponseEntity<Map<String, Object>> listServicePoints(
            @PathVariable long facilityId,
            @RequestHeader(value = CompanionHeaders.REQUEST_ID, required = false) String requestId,
            @RequestHeader(value = CompanionHeaders.CORRELATION_ID, required = false) String correlationId) {
        return proxyRead(() -> tusoClient.listServicePoints(facilityId), requestId, correlationId,
                "SERVICE_POINTS_UNAVAILABLE", "Unable to load service points from TUSO", facilityId);
    }

    @GetMapping("/{facilityId}/workspaces")
    public ResponseEntity<Map<String, Object>> listWorkspaces(
            @PathVariable long facilityId,
            @RequestHeader(value = CompanionHeaders.REQUEST_ID, required = false) String requestId,
            @RequestHeader(value = CompanionHeaders.CORRELATION_ID, required = false) String correlationId) {
        return proxyRead(() -> tusoClient.listWorkspaces(facilityId), requestId, correlationId,
                "WORKSPACES_UNAVAILABLE", "Unable to load workspaces from TUSO", facilityId);
    }

    @GetMapping("/{facilityId}/queue-definitions")
    public ResponseEntity<Map<String, Object>> queueDefinitions(
            @PathVariable long facilityId,
            @RequestHeader(value = CompanionHeaders.REQUEST_ID, required = false) String requestId,
            @RequestHeader(value = CompanionHeaders.CORRELATION_ID, required = false) String correlationId) {
        return proxyRead(() -> tusoClient.getFacilityConfigQueueDefinitions(facilityId), requestId, correlationId,
                "QUEUE_DEFINITIONS_UNAVAILABLE", "Unable to load derived queue definitions from TUSO", facilityId);
    }

    @GetMapping("/{facilityId}/downstream-readiness")
    public ResponseEntity<Map<String, Object>> downstreamReadiness(
            @PathVariable long facilityId,
            @RequestHeader(value = CompanionHeaders.REQUEST_ID, required = false) String requestId,
            @RequestHeader(value = CompanionHeaders.CORRELATION_ID, required = false) String correlationId) {
        return proxyRead(() -> tusoClient.getFacilityDownstreamReadiness(facilityId), requestId, correlationId,
                "DOWNSTREAM_READINESS_UNAVAILABLE", "Unable to load downstream readiness from TUSO", facilityId);
    }

    @GetMapping("/{facilityId}/configuration/history")
    public ResponseEntity<Map<String, Object>> configurationHistory(
            @PathVariable long facilityId,
            @RequestHeader(value = CompanionHeaders.REQUEST_ID, required = false) String requestId,
            @RequestHeader(value = CompanionHeaders.CORRELATION_ID, required = false) String correlationId) {
        return proxyRead(() -> tusoClient.getFacilityConfigurationHistory(facilityId), requestId, correlationId,
                "CONFIG_HISTORY_UNAVAILABLE", "Unable to load configuration history from TUSO", facilityId);
    }

    // ── Writes (RBAC-protected admin namespace; business rules live in TUSO) ──

    @PostMapping("/{facilityId}/service-points")
    public ResponseEntity<Map<String, Object>> createServicePoint(
            @PathVariable long facilityId, @RequestBody(required = false) Map<String, Object> body,
            @RequestHeader(value = CompanionHeaders.REQUEST_ID, required = false) String requestId,
            @RequestHeader(value = CompanionHeaders.CORRELATION_ID, required = false) String correlationId) {
        return proxyWrite(() -> tusoClient.createServicePoint(facilityId, body), requestId, correlationId,
                "SERVICE_POINT_CREATE_FAILED", "Unable to create service point via TUSO");
    }

    @PatchMapping("/{facilityId}/service-points/{servicePointId}")
    public ResponseEntity<Map<String, Object>> updateServicePoint(
            @PathVariable long facilityId, @PathVariable String servicePointId,
            @RequestBody(required = false) Map<String, Object> body,
            @RequestHeader(value = CompanionHeaders.REQUEST_ID, required = false) String requestId,
            @RequestHeader(value = CompanionHeaders.CORRELATION_ID, required = false) String correlationId) {
        return proxyWrite(() -> tusoClient.updateServicePoint(facilityId, servicePointId, body),
                requestId, correlationId, "SERVICE_POINT_UPDATE_FAILED", "Unable to update service point via TUSO");
    }

    @PostMapping("/{facilityId}/service-points/{servicePointId}/retire")
    public ResponseEntity<Map<String, Object>> retireServicePoint(
            @PathVariable long facilityId, @PathVariable String servicePointId,
            @RequestHeader(value = CompanionHeaders.REQUEST_ID, required = false) String requestId,
            @RequestHeader(value = CompanionHeaders.CORRELATION_ID, required = false) String correlationId) {
        return proxyWrite(() -> tusoClient.retireServicePoint(facilityId, servicePointId),
                requestId, correlationId, "SERVICE_POINT_RETIRE_FAILED", "Unable to retire service point via TUSO");
    }

    @PostMapping("/{facilityId}/workspaces")
    public ResponseEntity<Map<String, Object>> createWorkspace(
            @PathVariable long facilityId, @RequestBody(required = false) Map<String, Object> body,
            @RequestHeader(value = CompanionHeaders.REQUEST_ID, required = false) String requestId,
            @RequestHeader(value = CompanionHeaders.CORRELATION_ID, required = false) String correlationId) {
        return proxyWrite(() -> tusoClient.createWorkspace(facilityId, body), requestId, correlationId,
                "WORKSPACE_CREATE_FAILED", "Unable to create workspace via TUSO");
    }

    @PatchMapping("/{facilityId}/workspaces/{workspaceId}")
    public ResponseEntity<Map<String, Object>> updateWorkspace(
            @PathVariable long facilityId, @PathVariable String workspaceId,
            @RequestBody(required = false) Map<String, Object> body,
            @RequestHeader(value = CompanionHeaders.REQUEST_ID, required = false) String requestId,
            @RequestHeader(value = CompanionHeaders.CORRELATION_ID, required = false) String correlationId) {
        return proxyWrite(() -> tusoClient.updateWorkspace(workspaceId, body), requestId, correlationId,
                "WORKSPACE_UPDATE_FAILED", "Unable to update workspace via TUSO");
    }

    @PostMapping("/{facilityId}/workspaces/{workspaceId}/retire")
    public ResponseEntity<Map<String, Object>> retireWorkspace(
            @PathVariable long facilityId, @PathVariable String workspaceId,
            @RequestHeader(value = CompanionHeaders.REQUEST_ID, required = false) String requestId,
            @RequestHeader(value = CompanionHeaders.CORRELATION_ID, required = false) String correlationId) {
        return proxyWrite(() -> tusoClient.retireWorkspace(workspaceId), requestId, correlationId,
                "WORKSPACE_RETIRE_FAILED", "Unable to retire workspace via TUSO");
    }

    @PostMapping("/{facilityId}/configuration/publish")
    public ResponseEntity<Map<String, Object>> publishConfiguration(
            @PathVariable long facilityId,
            @RequestHeader(value = CompanionHeaders.REQUEST_ID, required = false) String requestId,
            @RequestHeader(value = CompanionHeaders.CORRELATION_ID, required = false) String correlationId) {
        return proxyWrite(() -> tusoClient.publishFacilityConfiguration(facilityId), requestId, correlationId,
                "CONFIG_PUBLISH_FAILED", "Unable to publish facility configuration via TUSO");
    }

    // ── Helpers ────────────────────────────────────────────────────────

    private ResponseEntity<Map<String, Object>> proxyRead(
            java.util.function.Supplier<JsonNode> call, String requestId, String correlationId,
            String errorCode, String errorMessage, long facilityId) {
        JsonNode data;
        try {
            data = call.get();
        } catch (HttpStatusCodeException e) {
            return passthrough(e, requestId, correlationId);
        } catch (Exception e) {
            return badGateway(requestId, correlationId, errorCode, errorMessage);
        }
        if (data == null || data.isNull()) {
            return notFound(requestId, correlationId, "FACILITY_NOT_FOUND", "Facility not found: " + facilityId);
        }
        return ResponseEntity.ok(Map.of("data", data, "meta", meta(requestId, correlationId)));
    }

    private ResponseEntity<Map<String, Object>> proxyWrite(
            java.util.function.Supplier<JsonNode> call, String requestId, String correlationId,
            String errorCode, String errorMessage) {
        try {
            JsonNode data = call.get();
            return ResponseEntity.ok(Map.of("data", data, "meta", meta(requestId, correlationId)));
        } catch (HttpStatusCodeException e) {
            return passthrough(e, requestId, correlationId);
        } catch (Exception e) {
            return badGateway(requestId, correlationId, errorCode, errorMessage);
        }
    }

    private ResponseEntity<Map<String, Object>> passthrough(
            HttpStatusCodeException e, String requestId, String correlationId) {
        String message = e.getResponseBodyAsString();
        return ResponseEntity.status(e.getStatusCode()).body(Map.of(
                "error", Map.of("code", "FACILITY_CONFIG_REJECTED",
                        "message", message == null || message.isBlank() ? e.getStatusText() : message),
                "meta", meta(requestId, correlationId)));
    }

    private static Map<String, Object> meta(String requestId, String correlationId) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("request_id", requestId);
        meta.put("correlation_id", correlationId);
        return meta;
    }

    private ResponseEntity<Map<String, Object>> badGateway(String requestId, String correlationId,
                                                           String code, String message) {
        log.warn("TUSO facility-config admin proxy failure [{}]: {}", code, message);
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                "error", Map.of("code", code, "message", message),
                "meta", meta(requestId, correlationId)));
    }

    private ResponseEntity<Map<String, Object>> notFound(String requestId, String correlationId,
                                                         String code, String message) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                "error", Map.of("code", code, "message", message),
                "meta", meta(requestId, correlationId)));
    }
}
