package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpClientErrorException;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.IotIngestionServiceClient;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Device Registry BFF — proxies sovereign device registry in iot-ingestion-service.
 */
@RestController
@RequestMapping("/internal/v1/devices")
public class DeviceRegistryController {

    private static final Logger log = LoggerFactory.getLogger(DeviceRegistryController.class);

    private final IotIngestionServiceClient iotClient;
    private final ObjectMapper objectMapper;

    public DeviceRegistryController(IotIngestionServiceClient iotClient, ObjectMapper objectMapper) {
        this.iotClient = iotClient;
        this.objectMapper = objectMapper;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> listDevices(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId) {

        log.info("Listing devices: tenant={}, requestId={}", tenantId, requestId);
        try {
            JsonNode devices = iotClient.listDevices(actorId);
            return ResponseEntity.ok(wrap(devices, requestId, correlationId, devices != null && devices.isArray() ? devices.size() : null));
        } catch (HttpClientErrorException e) {
            return upstreamError(e, requestId, correlationId);
        } catch (Exception e) {
            log.warn("Device list failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(error("IOT_REGISTRY_UNAVAILABLE", e.getMessage(), requestId, correlationId));
        }
    }

    @PostMapping("/register")
    public ResponseEntity<Map<String, Object>> registerDevice(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody Map<String, Object> body) {

        log.info("Registering device: tenant={}, requestId={}", tenantId, requestId);
        try {
            JsonNode device = iotClient.registerDevice(body);
            return ResponseEntity.status(HttpStatus.CREATED).body(wrap(device, requestId, correlationId, null));
        } catch (HttpClientErrorException e) {
            return upstreamError(e, requestId, correlationId);
        } catch (Exception e) {
            log.warn("Device register failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(error("IOT_REGISTRY_UNAVAILABLE", e.getMessage(), requestId, correlationId));
        }
    }

    @PostMapping("/{deviceId}/revoke")
    public ResponseEntity<Map<String, Object>> revokeDevice(
            @PathVariable String deviceId,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody(required = false) Map<String, Object> body) {

        log.info("Revoking device: tenant={}, deviceId={}, requestId={}", tenantId, deviceId, requestId);
        try {
            JsonNode revoked = iotClient.revokeDevice(deviceId, body != null ? body : Map.of("reason", "USER_REVOKED"));
            return ResponseEntity.ok(wrap(revoked, requestId, correlationId, null));
        } catch (HttpClientErrorException e) {
            return upstreamError(e, requestId, correlationId);
        } catch (Exception e) {
            log.warn("Device revoke failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(error("IOT_REGISTRY_UNAVAILABLE", e.getMessage(), requestId, correlationId));
        }
    }

    @GetMapping("/{deviceId}/trust")
    public ResponseEntity<Map<String, Object>> getDeviceTrust(
            @PathVariable String deviceId,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {

        log.info("Getting device trust: tenant={}, deviceId={}, requestId={}", tenantId, deviceId, requestId);
        try {
            JsonNode trust = iotClient.getDeviceTrust(deviceId);
            return ResponseEntity.ok(wrap(trust, requestId, correlationId, null));
        } catch (HttpClientErrorException e) {
            return upstreamError(e, requestId, correlationId);
        } catch (Exception e) {
            log.warn("Device trust failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(error("IOT_REGISTRY_UNAVAILABLE", e.getMessage(), requestId, correlationId));
        }
    }

    private Map<String, Object> wrap(JsonNode data, String requestId, String correlationId, Integer total) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", objectMapper.convertValue(data, Object.class));
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("request_id", requestId);
        meta.put("correlation_id", correlationId);
        if (total != null) {
            meta.put("total", total);
        }
        response.put("meta", meta);
        return response;
    }

    private ResponseEntity<Map<String, Object>> upstreamError(HttpClientErrorException e, String requestId, String correlationId) {
        return ResponseEntity.status(e.getStatusCode()).body(error("IOT_REGISTRY_ERROR", e.getMessage(), requestId, correlationId));
    }

    private Map<String, Object> error(String code, String message, String requestId, String correlationId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", Map.of("code", code, "message", message != null ? message : code));
        body.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
        return body;
    }
}
