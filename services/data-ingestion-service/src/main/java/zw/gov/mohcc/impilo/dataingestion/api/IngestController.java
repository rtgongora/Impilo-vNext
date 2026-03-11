package zw.gov.mohcc.impilo.dataingestion.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.companion.context.RequestContext;
import zw.gov.mohcc.impilo.companion.context.RequestContextHolder;
import zw.gov.mohcc.impilo.companion.error.ErrorEnvelope;
import zw.gov.mohcc.impilo.dataingestion.api.dto.BatchIngestRequest;
import zw.gov.mohcc.impilo.dataingestion.api.dto.BatchIngestResponse;
import zw.gov.mohcc.impilo.dataingestion.api.dto.BatchIngestResponse.BatchItemResult;
import zw.gov.mohcc.impilo.dataingestion.api.dto.HealthResponse;
import zw.gov.mohcc.impilo.dataingestion.api.dto.IngestEventRequest;
import zw.gov.mohcc.impilo.dataingestion.api.dto.IngestEventResponse;
import zw.gov.mohcc.impilo.dataingestion.core.IngestService;
import zw.gov.mohcc.impilo.dataingestion.core.IngestService.ValidationResult;
import zw.gov.mohcc.impilo.dataingestion.repository.DeadLetterEventRepository;

import jakarta.servlet.http.HttpServletRequest;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
public class IngestController {

    private static final Logger log = LoggerFactory.getLogger(IngestController.class);
    private static final int MAX_BATCH_SIZE = 200;

    private final IngestService ingestService;
    private final ObjectMapper objectMapper;
    private final DeadLetterEventRepository deadLetterRepository;

    public IngestController(IngestService ingestService, ObjectMapper objectMapper,
                            DeadLetterEventRepository deadLetterRepository) {
        this.ingestService = ingestService;
        this.objectMapper = objectMapper;
        this.deadLetterRepository = deadLetterRepository;
    }

    // ── POST /internal/v1/ingest/events — Single event ingestion ──

    @PostMapping("/internal/v1/ingest/events")
    public ResponseEntity<?> ingestEvent(@RequestBody String rawBody,
                                          HttpServletRequest httpRequest) {

        RequestContext ctx = RequestContextHolder.require();
        String idempotencyKey = httpRequest.getHeader(CompanionHeaders.IDEMPOTENCY_KEY);

        log.info("Ingest single event correlationId={}", ctx.correlationId());

        IngestEventRequest request;
        try {
            request = objectMapper.readValue(rawBody, IngestEventRequest.class);
        } catch (JsonProcessingException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ErrorEnvelope.of("INVALID_JSON",
                            "Failed to parse request body: " + e.getOriginalMessage(),
                            ctx.requestId(), ctx.correlationId()));
        }

        ValidationResult validation = ingestService.validate(request);
        if (validation != null) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                    .body(ErrorEnvelope.of(validation.code(), validation.message(),
                            ctx.requestId(), ctx.correlationId()));
        }

        IngestEventResponse response = ingestService.ingestEvent(
                request, rawBody,
                UUID.fromString(ctx.tenantId()), ctx.podId(),
                ctx.requestId(), ctx.correlationId(),
                idempotencyKey);

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    // ── POST /internal/v1/ingest/batch — Batch event ingestion ──

    @PostMapping("/internal/v1/ingest/batch")
    public ResponseEntity<?> ingestBatch(@RequestBody String rawBody,
                                          HttpServletRequest httpRequest) {

        RequestContext ctx = RequestContextHolder.require();
        String idempotencyKey = httpRequest.getHeader(CompanionHeaders.IDEMPOTENCY_KEY);

        log.info("Ingest batch correlationId={}", ctx.correlationId());

        BatchIngestRequest batchRequest;
        try {
            batchRequest = objectMapper.readValue(rawBody, BatchIngestRequest.class);
        } catch (JsonProcessingException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ErrorEnvelope.of("INVALID_JSON",
                            "Failed to parse batch request: " + e.getOriginalMessage(),
                            ctx.requestId(), ctx.correlationId()));
        }

        if (batchRequest.items() == null || batchRequest.items().isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ErrorEnvelope.of("INVALID_BATCH",
                            "Batch must contain at least one item",
                            ctx.requestId(), ctx.correlationId()));
        }

        if (batchRequest.items().size() > MAX_BATCH_SIZE) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ErrorEnvelope.of("BATCH_TOO_LARGE",
                            "Batch exceeds maximum size of " + MAX_BATCH_SIZE + " items",
                            ctx.requestId(), ctx.correlationId()));
        }

        // Extract individual item JSON strings from the raw body
        List<String> itemJsonStrings = extractItemJsonStrings(rawBody);

        UUID tenantId = UUID.fromString(ctx.tenantId());
        List<BatchItemResult> results = new ArrayList<>();
        int accepted = 0;
        int replayed = 0;
        int rejected = 0;

        for (int i = 0; i < batchRequest.items().size(); i++) {
            IngestEventRequest item = batchRequest.items().get(i);
            String itemJson = i < itemJsonStrings.size() ? itemJsonStrings.get(i) : toJson(item);

            // Per-item idempotency key: batch key + index
            String itemIdempotencyKey = idempotencyKey + ":" + i;

            ValidationResult validation = ingestService.validate(item);
            if (validation != null) {
                results.add(BatchItemResult.rejected(i,
                        item.eventId() != null ? item.eventId() : "unknown",
                        validation.code() + ": " + validation.message()));
                rejected++;
                continue;
            }

            try {
                IngestEventResponse response = ingestService.ingestEvent(
                        item, itemJson, tenantId, ctx.podId(),
                        ctx.requestId(), ctx.correlationId(),
                        itemIdempotencyKey);

                if ("REPLAY".equals(response.dedupe())) {
                    results.add(BatchItemResult.replayed(i, response.receiptId(), response.eventId()));
                    replayed++;
                } else {
                    results.add(BatchItemResult.accepted(i, response.receiptId(), response.eventId()));
                    accepted++;
                }
            } catch (Exception e) {
                log.error("Failed to ingest batch item {} eventId={}", i, item.eventId(), e);
                results.add(BatchItemResult.rejected(i,
                        item.eventId() != null ? item.eventId() : "unknown",
                        "INTERNAL_ERROR: " + e.getMessage()));
                rejected++;
            }
        }

        BatchIngestResponse response = new BatchIngestResponse(accepted, replayed, rejected, results);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    // ── GET /internal/v1/ingestion/status — Ingestion status ──

    @GetMapping("/internal/v1/ingestion/status")
    public ResponseEntity<?> ingestionStatus() {
        RequestContext ctx = RequestContextHolder.require();

        Map<String, Object> status = new LinkedHashMap<>();
        status.put("status", "ok");
        status.put("bronze_event_count", ingestService.countBronzeEvents());
        status.put("outbox_event_count", ingestService.countOutboxEvents());
        status.put("dead_letter_count", deadLetterRepository.count());
        status.put("time", OffsetDateTime.now().toString());

        return ResponseEntity.ok(status);
    }

    // ── GET /internal/v1/ingest/health — Health check ──

    @GetMapping("/internal/v1/ingest/health")
    public ResponseEntity<HealthResponse> health() {
        return ResponseEntity.ok(new HealthResponse("ok", OffsetDateTime.now().toString()));
    }

    private List<String> extractItemJsonStrings(String rawBody) {
        try {
            JsonNode root = objectMapper.readTree(rawBody);
            JsonNode items = root.get("items");
            if (items != null && items.isArray()) {
                List<String> result = new ArrayList<>();
                for (JsonNode item : items) {
                    result.add(objectMapper.writeValueAsString(item));
                }
                return result;
            }
        } catch (JsonProcessingException e) {
            log.warn("Failed to extract item JSON strings from batch body", e);
        }
        return List.of();
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }
}
