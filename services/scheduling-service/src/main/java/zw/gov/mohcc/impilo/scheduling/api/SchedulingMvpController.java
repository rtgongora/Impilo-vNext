package zw.gov.mohcc.impilo.scheduling.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.scheduling.core.SchedulingSlotService;

import java.util.Map;

@RestController
@RequestMapping("/v1")
public class SchedulingMvpController {

    private final SchedulingSlotService slotService;

    public SchedulingMvpController(SchedulingSlotService slotService) {
        this.slotService = slotService;
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of(
                "status", "UP",
                "engine", "scheduling-service",
                "description", "Tenant-scoped slot holds persisted in Postgres with transactional outbox");
    }

    @GetMapping("/slots")
    public ResponseEntity<Map<String, Object>> slots(
            @RequestParam("resource_id") String resourceId,
            @RequestParam("date") String date) {
        return ResponseEntity.ok(slotService.listSlots(resourceId, date));
    }

    public record ReserveSlotRequest(String resource_id, String date, String start_time, String hold_token) {}

    @PostMapping("/slots/reserve")
    public ResponseEntity<Map<String, Object>> reserve(@RequestBody ReserveSlotRequest body) {
        return ResponseEntity.ok(slotService.reserve(
                body.resource_id(), body.date(), body.start_time(), body.hold_token()));
    }

    @PostMapping("/slots/release")
    public ResponseEntity<Map<String, Object>> release(
            @RequestParam("resource_id") String resourceId,
            @RequestParam("date") String date,
            @RequestParam("start_time") String startTime,
            @RequestParam("hold_token") String holdToken) {
        return ResponseEntity.ok(slotService.release(resourceId, date, startTime, holdToken));
    }

    @GetMapping("/engine/capacity-rules")
    public Map<String, Object> capacityRules() {
        return slotService.capacityRules();
    }
}
