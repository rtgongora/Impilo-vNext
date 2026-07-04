package zw.gov.mohcc.impilo.khuluma.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.khuluma.core.OnCallService;
import zw.gov.mohcc.impilo.khuluma.domain.PresenceEntity;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Vashandi duty / on-call presence API (Phase 5 / W7, G-KH-04; specialty scoping in W2). */
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
        Object s = body.get("specialty");
        PresenceEntity p = service.setDuty(tenantId, actorId, actorType,
                d == null ? null : d.toString(),
                s == null ? null : s.toString());
        return ResponseEntity.ok(Map.of("data", view(p)));
    }

    @GetMapping("/on-call")
    public ResponseEntity<Map<String, Object>> roster(
            @RequestHeader("X-Tenant-ID") UUID tenantId,
            @RequestParam(required = false) String specialty) {
        return ResponseEntity.ok(Map.of("data",
                service.onCallRoster(tenantId, specialty).stream().map(OnCallController::view).toList()));
    }

    private static Map<String, Object> view(PresenceEntity p) {
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("actorId", p.getActorId());
        v.put("actorType", p.getActorType());
        v.put("dutyStatus", p.getDutyStatus());
        v.put("specialty", p.getSpecialty());
        return v;
    }
}
