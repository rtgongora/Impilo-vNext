package zw.gov.mohcc.impilo.obs.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.RequestContext;
import zw.gov.mohcc.impilo.companion.context.RequestContextHolder;
import zw.gov.mohcc.impilo.obs.core.OpsService;
import zw.gov.mohcc.impilo.obs.persistence.entity.ServiceHeartbeatEntity;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/internal/v1/ops")
public class OpsController {

    private final OpsService opsService;

    public OpsController(OpsService opsService) {
        this.opsService = opsService;
    }

    @GetMapping("/health/summary")
    public ResponseEntity<Map<String, Object>> getHealthSummary() {
        return ResponseEntity.ok(opsService.getHealthSummary());
    }

    @GetMapping("/metrics/lag")
    public ResponseEntity<Map<String, Object>> getMetricsLag() {
        return ResponseEntity.ok(opsService.getMetricsLag());
    }

    @PostMapping("/heartbeat")
    public ResponseEntity<Map<String, Object>> recordHeartbeat(
            @Valid @RequestBody HeartbeatRequest request) {
        RequestContext ctx = RequestContextHolder.require();

        ServiceHeartbeatEntity heartbeat = opsService.recordHeartbeat(
                UUID.fromString(ctx.tenantId()),
                request.serviceName(),
                request.instanceId(),
                request.status(),
                request.versionTag(),
                request.metadata());

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("service_name", heartbeat.getServiceName());
        response.put("instance_id", heartbeat.getInstanceId());
        response.put("status", heartbeat.getStatus());
        response.put("last_heartbeat", heartbeat.getLastHeartbeat().toString());
        response.put("acknowledged", true);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    public record HeartbeatRequest(
            @NotBlank String serviceName,
            @NotBlank String instanceId,
            String status,
            String versionTag,
            String metadata) {}
}
