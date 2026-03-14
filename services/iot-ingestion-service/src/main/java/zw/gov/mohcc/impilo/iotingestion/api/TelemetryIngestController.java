package zw.gov.mohcc.impilo.iotingestion.api;

import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.companion.context.RequestContext;
import zw.gov.mohcc.impilo.companion.context.RequestContextHolder;
import zw.gov.mohcc.impilo.iotingestion.api.dto.BatchIngestRequest;
import zw.gov.mohcc.impilo.iotingestion.api.dto.BatchIngestResponse;
import zw.gov.mohcc.impilo.iotingestion.api.dto.IngestTelemetryRequest;
import zw.gov.mohcc.impilo.iotingestion.api.dto.TelemetryResponse;
import zw.gov.mohcc.impilo.iotingestion.core.TelemetryIngestionService;
import zw.gov.mohcc.impilo.iotingestion.domain.TelemetryReadingEntity;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@RestController
public class TelemetryIngestController {

    private static final Logger log = LoggerFactory.getLogger(TelemetryIngestController.class);
    private final TelemetryIngestionService ingestionService;

    public TelemetryIngestController(TelemetryIngestionService ingestionService) {
        this.ingestionService = ingestionService;
    }

    @PostMapping("/internal/v1/telemetry/ingest")
    public ResponseEntity<?> ingestSingle(
            @Valid @RequestBody IngestTelemetryRequest request) {

        RequestContext ctx = RequestContextHolder.require();
        log.info("Ingest telemetry [device={}, metric={}] correlationId={}",
                request.deviceId(), request.metricType(), ctx.correlationId());

        TelemetryIngestionService.IngestResult result = ingestionService.ingestReading(
                UUID.fromString(ctx.tenantId()),
                ctx.podId(),
                ctx.correlationId(),
                request,
                "HTTP");

        if (result.rejected()) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("status", "rejected");
            body.put("dlq_id", result.dlqId().toString());
            body.put("reason", "Invalid schema_version");
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(body);
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "accepted");
        body.put("reading_id", result.readingId().toString());
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    @PostMapping("/internal/v1/telemetry/ingest/batch")
    public ResponseEntity<BatchIngestResponse> ingestBatch(
            @Valid @RequestBody BatchIngestRequest request) {

        RequestContext ctx = RequestContextHolder.require();
        log.info("Batch ingest telemetry [count={}] correlationId={}",
                request.readings().size(), ctx.correlationId());

        TelemetryIngestionService.BatchIngestResult result = ingestionService.ingestBatch(
                UUID.fromString(ctx.tenantId()),
                ctx.podId(),
                ctx.correlationId(),
                request.readings(),
                "HTTP");

        BatchIngestResponse response = new BatchIngestResponse(
                result.accepted(), result.rejected(), result.readingIds());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/internal/v1/telemetry/readings")
    public ResponseEntity<?> listReadings(
            @RequestParam(name = "device_id", required = false) String deviceId,
            @RequestParam(name = "metric_type", required = false) String metricType,
            @RequestParam(defaultValue = "0") int cursor,
            @RequestParam(defaultValue = "50") int limit) {

        RequestContext ctx = RequestContextHolder.require();
        log.info("List telemetry readings [device_id={}, metric_type={}, cursor={}, limit={}] correlationId={}",
                deviceId, metricType, cursor, limit, ctx.correlationId());

        Page<TelemetryReadingEntity> page = ingestionService.listReadings(
                UUID.fromString(ctx.tenantId()), deviceId, metricType, cursor, limit);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("items", page.getContent().stream().map(this::toResponse).toList());
        body.put("cursor", page.hasNext() ? String.valueOf(cursor + 1) : null);
        body.put("limit", limit);
        body.put("total_elements", page.getTotalElements());
        body.put("has_more", page.hasNext());
        return ResponseEntity.ok(body);
    }

    private TelemetryResponse toResponse(TelemetryReadingEntity entity) {
        return new TelemetryResponse(
                entity.getReadingId(),
                entity.getDeviceId(),
                entity.getTenantId(),
                entity.getMetricType(),
                entity.getMetricValue(),
                entity.getUnit(),
                entity.getSchemaVersion(),
                entity.getRecordedAt(),
                entity.getIngestedAt(),
                entity.getSource());
    }
}
