package zw.gov.mohcc.impilo.pipeline.api.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.companion.context.RequestContext;
import zw.gov.mohcc.impilo.companion.context.RequestContextHolder;
import zw.gov.mohcc.impilo.companion.error.ErrorEnvelope;
import zw.gov.mohcc.impilo.pipeline.api.dto.EventEnvelope;
import zw.gov.mohcc.impilo.pipeline.api.dto.IngestResponse;
import zw.gov.mohcc.impilo.pipeline.core.IngestionService;
import zw.gov.mohcc.impilo.pipeline.domain.IngestionStatus;

import java.util.UUID;

/**
 * REST controller for event ingestion.
 *
 * <p>Accepts v1.1 EventEnvelope JSON payloads via POST and delegates to the
 * IngestionService for validation, dedup, persistence, and outbox emission.</p>
 */
@RestController
@RequestMapping("/internal/v1")
public class IngestController {

    private static final Logger log = LoggerFactory.getLogger(IngestController.class);

    private final IngestionService ingestionService;

    public IngestController(IngestionService ingestionService) {
        this.ingestionService = ingestionService;
    }

    /**
     * Ingest a single v1.1 EventEnvelope.
     *
     * @param envelope the event to ingest
     * @return the ingestion result
     */
    @PostMapping("/ingest")
    public ResponseEntity<?> ingest(@RequestBody EventEnvelope envelope) {
        RequestContext ctx = RequestContextHolder.require();
        UUID tenantId = UUID.fromString(ctx.tenantId());

        // Validate required fields
        if (envelope.eventId() == null || envelope.eventId().isBlank()) {
            return ResponseEntity.badRequest().body(
                    ErrorEnvelope.of("VALIDATION_FAILED", "eventId is required",
                            ctx.requestId(), ctx.correlationId()));
        }
        if (envelope.sourceId() == null || envelope.sourceId().isBlank()) {
            return ResponseEntity.badRequest().body(
                    ErrorEnvelope.of("VALIDATION_FAILED", "sourceId is required",
                            ctx.requestId(), ctx.correlationId()));
        }
        if (envelope.eventType() == null || envelope.eventType().isBlank()) {
            return ResponseEntity.badRequest().body(
                    ErrorEnvelope.of("VALIDATION_FAILED", "eventType is required",
                            ctx.requestId(), ctx.correlationId()));
        }
        if (envelope.occurredAt() == null) {
            return ResponseEntity.badRequest().body(
                    ErrorEnvelope.of("VALIDATION_FAILED", "occurredAt is required",
                            ctx.requestId(), ctx.correlationId()));
        }
        if (envelope.podId() == null || envelope.podId().isBlank()) {
            return ResponseEntity.badRequest().body(
                    ErrorEnvelope.of("VALIDATION_FAILED", "podId is required",
                            ctx.requestId(), ctx.correlationId()));
        }

        IngestResponse result = ingestionService.ingest(envelope, tenantId);

        if (IngestionStatus.REJECTED.name().equals(result.status())) {
            return ResponseEntity.unprocessableEntity().body(result);
        }

        return ResponseEntity.ok(result);
    }
}
