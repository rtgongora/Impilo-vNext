package zw.gov.mohcc.impilo.pipeline.api.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.RequestContext;
import zw.gov.mohcc.impilo.companion.context.RequestContextHolder;
import zw.gov.mohcc.impilo.companion.error.ErrorEnvelope;
import zw.gov.mohcc.impilo.pipeline.api.dto.CreateSourceRequest;
import zw.gov.mohcc.impilo.pipeline.core.SourceService;
import zw.gov.mohcc.impilo.pipeline.persistence.entity.IngestionSourceEntity;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for managing ingestion sources.
 */
@RestController
@RequestMapping("/internal/v1")
public class SourceController {

    private final SourceService sourceService;

    public SourceController(SourceService sourceService) {
        this.sourceService = sourceService;
    }

    /**
     * Register a new ingestion source.
     *
     * @param request the source registration request
     * @return the created source entity
     */
    @PostMapping("/sources")
    public ResponseEntity<?> createSource(@RequestBody CreateSourceRequest request) {
        RequestContext ctx = RequestContextHolder.require();
        UUID tenantId = UUID.fromString(ctx.tenantId());

        if (request.sourceId() == null || request.sourceId().isBlank()) {
            return ResponseEntity.badRequest().body(
                    ErrorEnvelope.of("VALIDATION_FAILED", "sourceId is required",
                            ctx.requestId(), ctx.correlationId()));
        }
        if (request.displayName() == null || request.displayName().isBlank()) {
            return ResponseEntity.badRequest().body(
                    ErrorEnvelope.of("VALIDATION_FAILED", "displayName is required",
                            ctx.requestId(), ctx.correlationId()));
        }

        try {
            IngestionSourceEntity source = sourceService.createSource(request, tenantId);
            return ResponseEntity.ok(source);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(409).body(
                    ErrorEnvelope.of("CONFLICT", e.getMessage(),
                            ctx.requestId(), ctx.correlationId()));
        }
    }

    /**
     * List all registered sources for the tenant.
     */
    @GetMapping("/sources")
    public ResponseEntity<List<IngestionSourceEntity>> listSources() {
        RequestContext ctx = RequestContextHolder.require();
        UUID tenantId = UUID.fromString(ctx.tenantId());
        return ResponseEntity.ok(sourceService.listSources(tenantId));
    }
}
