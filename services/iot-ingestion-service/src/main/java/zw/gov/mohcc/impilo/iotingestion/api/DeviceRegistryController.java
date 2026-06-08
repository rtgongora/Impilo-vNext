package zw.gov.mohcc.impilo.iotingestion.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.RequestContext;
import zw.gov.mohcc.impilo.companion.context.RequestContextHolder;
import zw.gov.mohcc.impilo.iotingestion.core.DeviceRegistryService;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/internal/v1/devices")
public class DeviceRegistryController {

    private final DeviceRegistryService service;

    public DeviceRegistryController(DeviceRegistryService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> list(
            @RequestParam(name = "owner_health_id", required = false) String ownerHealthId) {
        RequestContext ctx = RequestContextHolder.require();
        UUID tenantId = UUID.fromString(ctx.tenantId());
        List<Map<String, Object>> devices = service.listDevices(tenantId, ownerHealthId);
        return ResponseEntity.ok(envelope(devices, ctx));
    }

    @PostMapping("/register")
    public ResponseEntity<Map<String, Object>> register(@RequestBody Map<String, Object> body) {
        RequestContext ctx = RequestContextHolder.require();
        UUID tenantId = UUID.fromString(ctx.tenantId());
        Map<String, Object> device = service.register(tenantId, ctx.actorId(), body);
        return ResponseEntity.status(HttpStatus.CREATED).body(single(device, ctx));
    }

    @PostMapping("/{deviceId}/revoke")
    public ResponseEntity<Map<String, Object>> revoke(
            @PathVariable UUID deviceId,
            @RequestBody(required = false) Map<String, Object> body) {
        RequestContext ctx = RequestContextHolder.require();
        UUID tenantId = UUID.fromString(ctx.tenantId());
        String reason = body != null && body.get("reason") != null ? String.valueOf(body.get("reason")) : "USER_REVOKED";
        Map<String, Object> device = service.revoke(tenantId, deviceId, ctx.actorId(), reason);
        return ResponseEntity.ok(single(device, ctx));
    }

    @GetMapping("/{deviceId}/trust")
    public ResponseEntity<Map<String, Object>> trust(@PathVariable UUID deviceId) {
        RequestContext ctx = RequestContextHolder.require();
        UUID tenantId = UUID.fromString(ctx.tenantId());
        Map<String, Object> trust = service.trustAssessment(tenantId, deviceId);
        return ResponseEntity.ok(single(trust, ctx));
    }

    private static Map<String, Object> envelope(List<Map<String, Object>> data, RequestContext ctx) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("data", data);
        body.put("meta", Map.of(
                "request_id", ctx.requestId(),
                "correlation_id", ctx.correlationId(),
                "total", data.size()));
        return body;
    }

    private static Map<String, Object> single(Map<String, Object> data, RequestContext ctx) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("data", data);
        body.put("meta", Map.of(
                "request_id", ctx.requestId(),
                "correlation_id", ctx.correlationId()));
        return body;
    }
}
