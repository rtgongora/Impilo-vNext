package zw.gov.mohcc.impilo.surv.api;

import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.companion.context.RequestContext;
import zw.gov.mohcc.impilo.companion.context.RequestContextHolder;
import zw.gov.mohcc.impilo.companion.error.ErrorEnvelope;
import zw.gov.mohcc.impilo.surv.core.IngestService;

@RestController
@RequestMapping("/internal/v1/ingest")
public class IngestController {

    private static final Logger log = LoggerFactory.getLogger(IngestController.class);

    private final IngestService ingestService;

    public IngestController(IngestService ingestService) {
        this.ingestService = ingestService;
    }

    @PostMapping
    public ResponseEntity<?> ingest(@RequestBody IngestRequest request,
                                    HttpServletRequest httpRequest) {

        RequestContext ctx = RequestContextHolder.require();
        String idempotencyKey = httpRequest.getHeader(CompanionHeaders.IDEMPOTENCY_KEY);

        log.info("Ingest event [eventType={}, facilityId={}] correlationId={}",
                request.eventType(), request.facilityId(), ctx.correlationId());

        if (request.eventType() == null || request.eventType().isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ErrorEnvelope.of("INVALID_REQUEST",
                            "eventType is required",
                            ctx.requestId(), ctx.correlationId()));
        }

        IngestService.IngestResult result = ingestService.ingest(
                UUID.fromString(ctx.tenantId()),
                ctx.podId(),
                ctx.correlationId(),
                request.eventType(),
                request.payload(),
                request.facilityId(),
                idempotencyKey != null ? idempotencyKey : ctx.correlationId().toString());

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(result);
    }

    public record IngestRequest(
            String eventType,
            String payload,
            UUID facilityId
    ) {}
}
