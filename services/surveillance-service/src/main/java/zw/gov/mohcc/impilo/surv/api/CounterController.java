package zw.gov.mohcc.impilo.surv.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.RequestContext;
import zw.gov.mohcc.impilo.companion.context.RequestContextHolder;
import zw.gov.mohcc.impilo.surv.core.CounterService;
import zw.gov.mohcc.impilo.surv.persistence.entity.AlertDefinitionEntity;
import zw.gov.mohcc.impilo.surv.persistence.entity.AlertEventEntity;
import zw.gov.mohcc.impilo.surv.persistence.entity.DailyCounterEntity;

import java.time.LocalDate;
import java.util.*;

@RestController
public class CounterController {

    private static final Logger log = LoggerFactory.getLogger(CounterController.class);
    private final CounterService counterService;

    public CounterController(CounterService counterService) {
        this.counterService = counterService;
    }

    @GetMapping("/internal/v1/surveillance/counters")
    public ResponseEntity<?> getCounters(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {

        RequestContext ctx = RequestContextHolder.require();
        UUID tenantId = UUID.fromString(ctx.tenantId());

        log.info("Get counters [from={}, to={}] correlationId={}", from, to, ctx.correlationId());

        List<DailyCounterEntity> counters = counterService.getCounters(tenantId, from, to);

        List<Map<String, Object>> items = counters.stream()
                .map(c -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("facility_id", c.getFacilityId());
                    item.put("syndrome_code", c.getSyndromeCode());
                    item.put("count_date", c.getCountDate().toString());
                    item.put("event_count", c.getEventCount());
                    item.put("last_updated_at", c.getLastUpdatedAt().toString());
                    return item;
                })
                .toList();

        return ResponseEntity.ok(Map.of("counters", items, "total", items.size()));
    }

    @PostMapping("/internal/v1/surveillance/counters")
    public ResponseEntity<?> incrementCounter(@RequestBody Map<String, String> body) {
        RequestContext ctx = RequestContextHolder.require();
        UUID tenantId = UUID.fromString(ctx.tenantId());

        UUID facilityId = UUID.fromString(body.get("facility_id"));
        String syndromeCode = body.get("syndrome_code");
        LocalDate date = body.containsKey("count_date")
                ? LocalDate.parse(body.get("count_date"))
                : LocalDate.now();

        DailyCounterEntity counter = counterService.incrementCounter(tenantId, facilityId, syndromeCode, date);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("facility_id", counter.getFacilityId());
        result.put("syndrome_code", counter.getSyndromeCode());
        result.put("count_date", counter.getCountDate().toString());
        result.put("event_count", counter.getEventCount());

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(result);
    }

    @GetMapping("/internal/v1/surveillance/alerts")
    public ResponseEntity<?> getAlerts(
            @RequestParam(defaultValue = "false") boolean unacknowledgedOnly) {

        RequestContext ctx = RequestContextHolder.require();
        UUID tenantId = UUID.fromString(ctx.tenantId());

        log.info("Get alerts [unacknowledgedOnly={}] correlationId={}", unacknowledgedOnly, ctx.correlationId());

        List<AlertEventEntity> alerts = counterService.getAlerts(tenantId, unacknowledgedOnly);

        List<Map<String, Object>> items = alerts.stream()
                .map(a -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("id", a.getId());
                    item.put("alert_definition_id", a.getAlertDefinitionId());
                    item.put("facility_id", a.getFacilityId());
                    item.put("syndrome_code", a.getSyndromeCode());
                    item.put("trigger_count", a.getTriggerCount());
                    item.put("threshold", a.getThreshold());
                    item.put("triggered_at", a.getTriggeredAt().toString());
                    item.put("acknowledged", a.isAcknowledged());
                    return item;
                })
                .toList();

        return ResponseEntity.ok(Map.of("alerts", items, "total", items.size()));
    }

    @PostMapping("/internal/v1/surveillance/alerts/{alertId}/acknowledge")
    public ResponseEntity<?> acknowledgeAlert(@PathVariable Long alertId) {
        RequestContext ctx = RequestContextHolder.require();
        UUID tenantId = UUID.fromString(ctx.tenantId());

        Optional<AlertEventEntity> acknowledged = counterService.acknowledgeAlert(tenantId, alertId);
        if (acknowledged.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                    "error", "Alert not found"
            ));
        }

        AlertEventEntity a = acknowledged.get();
        return ResponseEntity.ok(Map.of(
                "id", a.getId(),
                "acknowledged", a.isAcknowledged()
        ));
    }

    @PostMapping("/internal/v1/surveillance/alerts/definitions")
    public ResponseEntity<?> createAlertDefinition(@RequestBody Map<String, Object> body) {
        RequestContext ctx = RequestContextHolder.require();
        UUID tenantId = UUID.fromString(ctx.tenantId());

        String name = (String) body.get("name");
        String syndromeCode = (String) body.get("syndrome_code");
        int threshold = body.containsKey("threshold") ? ((Number) body.get("threshold")).intValue() : 5;
        int windowDays = body.containsKey("window_days") ? ((Number) body.get("window_days")).intValue() : 1;
        String severity = (String) body.getOrDefault("severity", "MEDIUM");

        AlertDefinitionEntity def = counterService.createAlertDefinition(
                tenantId, name, syndromeCode, threshold, windowDays, severity);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", def.getId());
        result.put("name", def.getName());
        result.put("syndrome_code", def.getSyndromeCode());
        result.put("threshold", def.getThreshold());
        result.put("severity", def.getSeverity());

        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    @GetMapping("/internal/v1/surveillance/alerts/definitions")
    public ResponseEntity<?> listAlertDefinitions() {
        RequestContext ctx = RequestContextHolder.require();
        UUID tenantId = UUID.fromString(ctx.tenantId());

        List<AlertDefinitionEntity> defs = counterService.getAlertDefinitions(tenantId);

        List<Map<String, Object>> items = defs.stream()
                .map(d -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("id", d.getId());
                    item.put("name", d.getName());
                    item.put("syndrome_code", d.getSyndromeCode());
                    item.put("threshold", d.getThreshold());
                    item.put("window_days", d.getWindowDays());
                    item.put("severity", d.getSeverity());
                    item.put("active", d.isActive());
                    return item;
                })
                .toList();

        return ResponseEntity.ok(Map.of("definitions", items, "total", items.size()));
    }
}
