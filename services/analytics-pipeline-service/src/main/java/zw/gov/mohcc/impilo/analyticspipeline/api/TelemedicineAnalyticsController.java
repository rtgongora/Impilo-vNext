package zw.gov.mohcc.impilo.analyticspipeline.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Telemedicine lifecycle analytics ingest and SLA aggregate queries.
 */
@RestController
@RequestMapping("/internal/v1/telemedicine")
public class TelemedicineAnalyticsController {

    private final AtomicLong eventsAccepted = new AtomicLong();
    private final Map<String, AtomicLong> eventsByType = new ConcurrentHashMap<>();

    @GetMapping("/sla")
    public ResponseEntity<Map<String, Object>> slaAggregates(
            @RequestParam(name = "facility_id", required = false) String facilityId,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("facilityId", facilityId);
        body.put("from", from);
        body.put("to", to);
        body.put("eventsAccepted", eventsAccepted.get());
        body.put("eventsByType", eventsByType.entrySet().stream()
                .collect(LinkedHashMap::new, (m, e) -> m.put(e.getKey(), e.getValue().get()), LinkedHashMap::putAll));
        body.put("generatedAt", Instant.now().toString());
        return ResponseEntity.ok(body);
    }

    @PostMapping("/events")
    public ResponseEntity<Map<String, Object>> ingestEvent(@RequestBody Map<String, Object> event) {
        String eventType = event.getOrDefault("eventType", "UNKNOWN").toString();
        eventsAccepted.incrementAndGet();
        eventsByType.computeIfAbsent(eventType, k -> new AtomicLong()).incrementAndGet();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("eventId", UUID.randomUUID().toString());
        body.put("status", "ACCEPTED");
        body.put("receivedAt", Instant.now().toString());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(body);
    }
}
