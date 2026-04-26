package zw.gov.mohcc.impilo.scheduling.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MVP slot surface — in-memory reservations until Postgres + Tuso/PCT bridges land.
 */
@RestController
@RequestMapping("/v1")
public class SchedulingMvpController {

    private final ConcurrentHashMap<String, String> reserved = new ConcurrentHashMap<>();

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of(
                "status", "UP",
                "engine", "scheduling-service-mvp",
                "description", "Slot templates + in-memory holds — BFF `/internal/v1/appointments/availability` calls this service when `impilo.scheduling.use-remote-slots=true`");
    }

    /**
     * List deterministic 20-minute slots for a resource on a calendar day (UTC day boundary).
     */
    @GetMapping("/slots")
    public ResponseEntity<Map<String, Object>> slots(
            @RequestParam("resource_id") String resourceId,
            @RequestParam("date") String date) {
        LocalDate d = LocalDate.parse(date);
        List<Map<String, Object>> out = new ArrayList<>();
        LocalTime t = LocalTime.of(8, 0);
        LocalTime end = LocalTime.of(17, 0);
        while (t.isBefore(end)) {
            String key = resourceId + "|" + d + "|" + t;
            out.add(Map.of(
                    "start", d.atTime(t).toString(),
                    "duration_minutes", 20,
                    "available", !reserved.containsKey(key)));
            t = t.plusMinutes(20);
        }
        return ResponseEntity.ok(Map.of(
                "resource_id", resourceId,
                "date", d.toString(),
                "slots", out));
    }

    public record ReserveSlotRequest(String resource_id, String date, String start_time, String hold_token) {}

    @PostMapping("/slots/reserve")
    public ResponseEntity<Map<String, Object>> reserve(@RequestBody ReserveSlotRequest body) {
        LocalDate d = LocalDate.parse(body.date());
        String key = body.resource_id() + "|" + d + "|" + LocalTime.parse(body.start_time());
        String token = body.hold_token() != null && !body.hold_token().isBlank()
                ? body.hold_token()
                : UUID.randomUUID().toString();
        String prev = reserved.putIfAbsent(key, token);
        if (prev != null && !prev.equals(token)) {
            return ResponseEntity.status(409).body(Map.of(
                    "error", "slot_already_reserved",
                    "resource_id", body.resource_id(),
                    "start", body.start_time()));
        }
        return ResponseEntity.ok(Map.of(
                "status", "RESERVED",
                "hold_token", token,
                "resource_id", body.resource_id(),
                "date", body.date(),
                "start_time", body.start_time()));
    }

    @PostMapping("/slots/release")
    public ResponseEntity<Map<String, Object>> release(
            @RequestParam("resource_id") String resourceId,
            @RequestParam("date") String date,
            @RequestParam("start_time") String startTime,
            @RequestParam("hold_token") String holdToken) {
        LocalDate d = LocalDate.parse(date);
        String key = resourceId + "|" + d + "|" + LocalTime.parse(startTime);
        reserved.remove(key, holdToken);
        return ResponseEntity.ok(Map.of("status", "RELEASED", "key", key));
    }

    @GetMapping("/engine/capacity-rules")
    public Map<String, Object> capacityRules() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("version", "mvp-0.1");
        body.put("overbooking_factor", 1.0);
        body.put("waitlist_enabled", false);
        body.put("holiday_calendar", "NOT_CONFIGURED");
        body.put("notes", "Replace with persisted rules + Tshepo ABAC when scheduling-service is promoted.");
        return body;
    }
}
