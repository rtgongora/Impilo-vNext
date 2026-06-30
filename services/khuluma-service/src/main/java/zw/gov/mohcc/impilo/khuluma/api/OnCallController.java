package zw.gov.mohcc.impilo.khuluma.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.khuluma.core.OnCallService;
import zw.gov.mohcc.impilo.khuluma.domain.PresenceEntity;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Vashandi duty / on-call presence API (Phase 5 / W7, G-KH-04). */
@RestController
@RequestMapping("/internal/v1/khuluma")
public class OnCallController {

    private final OnCallService service;

    public OnCallController(OnCallService service) {
        this.service = service;
    }

    @PutMapping("/duty")
    public ResponseEntity<Map<String, Object>> setDuty(
            @RequestHeader("X-Tenant-ID") UUID tenantId,
            @RequestHeader("X-Actor-ID") String actorId,
            @RequestHeader(value = "X-Actor-Type", required = false) String actorType,
            @RequestBody Map<String, Object> body) {
        Object d = body.get("dutyStatus");
        PresenceEntity p = service.setDuty(tenantId, actorId, actorType, d == null ? null : d.toString());
        return ResponseEntity.ok(Map.of("data", view(p)));
    }

    @GetMapping("/on-call")
    public ResponseEntity<Map<String, Object>> roster(@RequestHeader("X-Tenant-ID") UUID tenantId) {
        return ResponseEntity.ok(Map.of("data", service.onCallRoster(tenantId).stream().map(OnCallController::view).toList()));
    }

    private static Map<String, Object> view(PresenceEntity p) {
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("actorId", p.getActorId());
        v.put("actorType", p.getActorType());
        v.put("dutyStatus", p.getDutyStatus());
        return v;
    }
}
