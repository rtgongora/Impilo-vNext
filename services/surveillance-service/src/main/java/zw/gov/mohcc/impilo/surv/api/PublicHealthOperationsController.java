package zw.gov.mohcc.impilo.surv.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.constraints.NotBlank;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.RequestContext;
import zw.gov.mohcc.impilo.companion.context.RequestContextHolder;
import zw.gov.mohcc.impilo.companion.error.ErrorEnvelope;
import zw.gov.mohcc.impilo.surv.core.CounterService;
import zw.gov.mohcc.impilo.surv.core.IngestService;
import zw.gov.mohcc.impilo.surv.core.Severity;
import zw.gov.mohcc.impilo.surv.core.SignalService;
import zw.gov.mohcc.impilo.surv.persistence.entity.DailyCounterEntity;
import zw.gov.mohcc.impilo.surv.persistence.entity.SignalEntity;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Dedicated public-health operations API surface.
 *
 * This gives the Data Plane a sovereign route family for weekly/outbreak/field lifecycles
 * while still using bounded internal surveillance primitives behind the service boundary.
 */
@RestController
@RequestMapping("/internal/v1/public-health")
public class PublicHealthOperationsController {

    private final CounterService counterService;
    private final SignalService signalService;
    private final IngestService ingestService;
    private final ObjectMapper objectMapper;

    public PublicHealthOperationsController(
            CounterService counterService,
            SignalService signalService,
            IngestService ingestService,
            ObjectMapper objectMapper) {
        this.counterService = counterService;
        this.signalService = signalService;
        this.ingestService = ingestService;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/weekly-idsr")
    public ResponseEntity<?> weeklyIdsr(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        RequestContext ctx = RequestContextHolder.require();
        UUID tenantId = UUID.fromString(ctx.tenantId());

        List<DailyCounterEntity> counters = counterService.getCounters(tenantId, from, to);
        Map<String, Long> totalsBySyndrome = counters.stream()
                .collect(Collectors.groupingBy(DailyCounterEntity::getSyndromeCode, Collectors.summingLong(DailyCounterEntity::getEventCount)));

        List<Map<String, Object>> rows = totalsBySyndrome.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> Map.<String, Object>of(
                        "syndrome_code", entry.getKey(),
                        "event_count", entry.getValue(),
                        "period_start", from != null ? from.toString() : "",
                        "period_end", to != null ? to.toString() : ""))
                .toList();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("report_type", "WEEKLY_IDSR_AGGREGATE");
        body.put("derivation", "SURVEILLANCE_COUNTERS");
        body.put("rows", rows);
        body.put("total_events", rows.stream().mapToLong(r -> ((Number) r.get("event_count")).longValue()).sum());
        return ResponseEntity.ok(body);
    }

    @PostMapping("/outbreaks")
    public ResponseEntity<?> recordOutbreak(@RequestBody CreateOutbreakRequest request) {
        RequestContext ctx = RequestContextHolder.require();
        UUID tenantId = UUID.fromString(ctx.tenantId());
        UUID correlationId = UUID.fromString(ctx.correlationId());

        if (request.name() == null || request.name().isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ErrorEnvelope.of("INVALID_REQUEST", "name is required", ctx.requestId(), ctx.correlationId()));
        }

        SignalEntity signal = signalService.createSignal(
                tenantId,
                ctx.podId(),
                correlationId,
                request.name(),
                request.description(),
                request.eventType() != null && !request.eventType().isBlank() ? request.eventType() : "OUTBREAK",
                request.conditionField() != null && !request.conditionField().isBlank() ? request.conditionField() : "syndrome_code",
                request.threshold() > 0 ? request.threshold() : 1,
                request.windowHours() > 0 ? request.windowHours() : 24,
                parseSeverity(request.severity()));

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("outbreak_id", signal.getId());
        response.put("lifecycle_status", "RECORDED");
        response.put("signal_reference", signal.getId());
        response.put("name", signal.getName());
        response.put("severity", signal.getSeverity());
        response.put("event_type", signal.getEventType());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/field-operations")
    public ResponseEntity<?> recordFieldOperation(@RequestBody CreateFieldOperationRequest request) {
        RequestContext ctx = RequestContextHolder.require();
        UUID tenantId = UUID.fromString(ctx.tenantId());
        UUID correlationId = UUID.fromString(ctx.correlationId());

        if (request.facilityId() == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ErrorEnvelope.of("INVALID_REQUEST", "facility_id is required", ctx.requestId(), ctx.correlationId()));
        }

        String payload;
        try {
            payload = objectMapper.writeValueAsString(Map.of(
                    "operation_type", textOrDefault(request.operationType(), "FIELD_OPERATION"),
                    "status", textOrDefault(request.status(), "PLANNED"),
                    "occurred_on", textOrDefault(request.occurredOn(), LocalDate.now().toString()),
                    "details", request.details() != null ? request.details() : Map.of()));
        } catch (JsonProcessingException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ErrorEnvelope.of("INVALID_REQUEST", "details payload must be serializable", ctx.requestId(), ctx.correlationId()));
        }

        IngestService.IngestResult result = ingestService.ingest(
                tenantId,
                ctx.podId(),
                correlationId,
                "FIELD_OPERATION",
                payload,
                request.facilityId(),
                ctx.correlationId());

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("operation_id", UUID.randomUUID().toString());
        response.put("lifecycle_status", "ACCEPTED");
        response.put("signals_matched", result.signalsMatched());
        response.put("hits_recorded", result.hitsRecorded());
        response.put("cases_opened", result.casesOpened());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    private Severity parseSeverity(String rawSeverity) {
        if (rawSeverity == null || rawSeverity.isBlank()) {
            return Severity.HIGH;
        }
        try {
            return Severity.valueOf(rawSeverity.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return Severity.HIGH;
        }
    }

    private String textOrDefault(String value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? fallback : trimmed;
    }

    public record CreateOutbreakRequest(
            @NotBlank String name,
            String description,
            String eventType,
            String conditionField,
            int threshold,
            int windowHours,
            String severity) {
    }

    public record CreateFieldOperationRequest(
            UUID facilityId,
            String operationType,
            String status,
            String occurredOn,
            Map<String, Object> details) {
    }
}
