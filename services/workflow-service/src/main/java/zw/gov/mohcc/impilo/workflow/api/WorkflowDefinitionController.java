package zw.gov.mohcc.impilo.workflow.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.companion.context.RequestContext;
import zw.gov.mohcc.impilo.companion.context.RequestContextHolder;
import zw.gov.mohcc.impilo.companion.error.ErrorEnvelope;
import zw.gov.mohcc.impilo.workflow.api.dto.*;
import zw.gov.mohcc.impilo.workflow.core.WorkflowService;
import zw.gov.mohcc.impilo.workflow.domain.WorkflowDefinitionEntity;

import java.util.*;

@RestController
public class WorkflowDefinitionController {

    private final WorkflowService workflowService;
    private final ObjectMapper objectMapper;

    public WorkflowDefinitionController(WorkflowService workflowService, ObjectMapper objectMapper) {
        this.workflowService = workflowService;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/internal/v1/workflows/definitions")
    public ResponseEntity<?> createDefinition(@Valid @RequestBody CreateDefinitionRequest request,
                                                jakarta.servlet.http.HttpServletRequest httpRequest) {
        RequestContext ctx = RequestContextHolder.require();
        String idempotencyKey = httpRequest.getHeader(CompanionHeaders.IDEMPOTENCY_KEY);
        WorkflowDefinitionEntity def = workflowService.createDefinition(UUID.fromString(ctx.tenantId()),
                ctx.podId(), ctx.correlationId(), idempotencyKey, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(def));
    }

    @PostMapping("/internal/v1/workflows/definitions/{definition_id}/publish")
    public ResponseEntity<?> publishDefinition(@PathVariable("definition_id") UUID definitionId,
                                                 jakarta.servlet.http.HttpServletRequest httpRequest) {
        RequestContext ctx = RequestContextHolder.require();
        String idempotencyKey = httpRequest.getHeader(CompanionHeaders.IDEMPOTENCY_KEY);
        try {
            WorkflowDefinitionEntity def = workflowService.publishDefinition(definitionId,
                    UUID.fromString(ctx.tenantId()), ctx.podId(), ctx.correlationId(), idempotencyKey);
            return ResponseEntity.ok(toResponse(def));
        } catch (WorkflowService.NotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ErrorEnvelope.of("NOT_FOUND", e.getMessage(), ctx.requestId(), ctx.correlationId()));
        }
    }

    @GetMapping("/internal/v1/workflows/definitions/{definition_id}")
    public ResponseEntity<?> getDefinition(@PathVariable("definition_id") UUID definitionId) {
        RequestContext ctx = RequestContextHolder.require();
        return workflowService.getDefinition(definitionId)
                .map(d -> ResponseEntity.ok((Object) toResponse(d)))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ErrorEnvelope.of("NOT_FOUND", "Definition not found", ctx.requestId(), ctx.correlationId())));
    }

    @GetMapping("/internal/v1/workflows/definitions")
    public ResponseEntity<?> listDefinitions(@RequestParam(required = false) String status,
                                               @RequestParam(required = false) String category,
                                               @RequestParam(defaultValue = "0") int cursor,
                                               @RequestParam(defaultValue = "20") int limit) {
        RequestContext ctx = RequestContextHolder.require();
        Page<WorkflowDefinitionEntity> page = workflowService.listDefinitions(
                UUID.fromString(ctx.tenantId()), status, category, cursor, limit);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("items", page.getContent().stream().map(this::toResponse).toList());
        body.put("cursor", page.hasNext() ? String.valueOf(cursor + 1) : null);
        body.put("limit", limit);
        body.put("total_elements", page.getTotalElements());
        body.put("has_more", page.hasNext());
        return ResponseEntity.ok(body);
    }

    private DefinitionResponse toResponse(WorkflowDefinitionEntity d) {
        return new DefinitionResponse(d.getDefinitionId(), d.getTenantId(), d.getCode(), d.getName(),
                d.getDescription(), d.getCategory(), d.getVersion(), d.getStatus(),
                parseJsonSafe(d.getSchemaJson()), parseJsonSafe(d.getStepsJson()),
                d.getCreatedAt(), d.getUpdatedAt(), d.getPublishedAt());
    }

    private Object parseJsonSafe(String json) {
        if (json == null) return null;
        try { return objectMapper.readValue(json, Object.class); }
        catch (Exception e) { return json; }
    }
}
