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
import zw.gov.mohcc.impilo.workflow.domain.WorkflowInstanceEntity;
import zw.gov.mohcc.impilo.workflow.domain.WorkflowTaskEntity;

import java.util.*;

@RestController
public class WorkflowInstanceController {

    private final WorkflowService workflowService;
    private final ObjectMapper objectMapper;

    public WorkflowInstanceController(WorkflowService workflowService, ObjectMapper objectMapper) {
        this.workflowService = workflowService;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/internal/v1/workflows/instances")
    public ResponseEntity<?> startInstance(@Valid @RequestBody StartInstanceRequest request,
                                            jakarta.servlet.http.HttpServletRequest httpRequest) {
        RequestContext ctx = RequestContextHolder.require();
        String idempotencyKey = httpRequest.getHeader(CompanionHeaders.IDEMPOTENCY_KEY);
        try {
            WorkflowInstanceEntity instance = workflowService.startInstance(UUID.fromString(ctx.tenantId()),
                    ctx.podId(), ctx.correlationId(), idempotencyKey, request);
            return ResponseEntity.status(HttpStatus.CREATED).body(toInstanceResponse(instance));
        } catch (WorkflowService.NotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ErrorEnvelope.of("NOT_FOUND", e.getMessage(), ctx.requestId(), ctx.correlationId()));
        } catch (WorkflowService.InvalidTransitionException e) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                    .body(ErrorEnvelope.of("INVALID_TRANSITION", e.getMessage(), ctx.requestId(), ctx.correlationId()));
        }
    }

    @PostMapping("/internal/v1/workflows/instances/{instance_id}/transition")
    public ResponseEntity<?> transitionInstance(@PathVariable("instance_id") UUID instanceId,
                                                  @Valid @RequestBody TransitionRequest request,
                                                  jakarta.servlet.http.HttpServletRequest httpRequest) {
        RequestContext ctx = RequestContextHolder.require();
        String idempotencyKey = httpRequest.getHeader(CompanionHeaders.IDEMPOTENCY_KEY);
        try {
            WorkflowInstanceEntity instance = workflowService.transitionInstance(instanceId,
                    UUID.fromString(ctx.tenantId()), ctx.podId(), ctx.correlationId(), idempotencyKey, request);
            return ResponseEntity.ok(toInstanceResponse(instance));
        } catch (WorkflowService.NotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ErrorEnvelope.of("NOT_FOUND", e.getMessage(), ctx.requestId(), ctx.correlationId()));
        } catch (WorkflowService.InvalidTransitionException e) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                    .body(ErrorEnvelope.of("INVALID_TRANSITION", e.getMessage(), ctx.requestId(), ctx.correlationId()));
        }
    }

    @GetMapping("/internal/v1/workflows/instances/{instance_id}")
    public ResponseEntity<?> getInstance(@PathVariable("instance_id") UUID instanceId) {
        RequestContext ctx = RequestContextHolder.require();
        return workflowService.getInstance(instanceId)
                .map(i -> ResponseEntity.ok((Object) toInstanceResponse(i)))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ErrorEnvelope.of("NOT_FOUND", "Instance not found", ctx.requestId(), ctx.correlationId())));
    }

    @GetMapping("/internal/v1/workflows/instances")
    public ResponseEntity<?> listInstances(@RequestParam(required = false) String status,
                                             @RequestParam(defaultValue = "0") int cursor,
                                             @RequestParam(defaultValue = "20") int limit) {
        RequestContext ctx = RequestContextHolder.require();
        Page<WorkflowInstanceEntity> page = workflowService.listInstances(
                UUID.fromString(ctx.tenantId()), status, cursor, limit);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("items", page.getContent().stream().map(this::toInstanceResponse).toList());
        body.put("cursor", page.hasNext() ? String.valueOf(cursor + 1) : null);
        body.put("limit", limit);
        body.put("total_elements", page.getTotalElements());
        body.put("has_more", page.hasNext());
        return ResponseEntity.ok(body);
    }

    @GetMapping("/internal/v1/workflows/instances/{instance_id}/tasks")
    public ResponseEntity<?> getInstanceTasks(@PathVariable("instance_id") UUID instanceId) {
        RequestContextHolder.require();
        List<WorkflowTaskEntity> tasks = workflowService.getInstanceTasks(instanceId);
        return ResponseEntity.ok(Map.of("items", tasks.stream().map(this::toTaskResponse).toList(),
                "count", tasks.size()));
    }

    private InstanceResponse toInstanceResponse(WorkflowInstanceEntity i) {
        return new InstanceResponse(i.getInstanceId(), i.getDefinitionId(), i.getTenantId(),
                i.getStatus(), i.getCurrentStep(), parseJsonSafe(i.getContextJson()),
                i.getInitiatorRef(), i.getSubjectRef(),
                i.getStartedAt(), i.getCompletedAt(), i.getCreatedAt(), i.getUpdatedAt());
    }

    private TaskResponse toTaskResponse(WorkflowTaskEntity t) {
        return new TaskResponse(t.getTaskId(), t.getInstanceId(), t.getTenantId(),
                t.getStepName(), t.getStatus(), t.getAssigneeRef(),
                parseJsonSafe(t.getInputJson()), parseJsonSafe(t.getOutputJson()),
                t.getStartedAt(), t.getCompletedAt(), t.getCreatedAt(), t.getUpdatedAt());
    }

    private Object parseJsonSafe(String json) {
        if (json == null) return null;
        try { return objectMapper.readValue(json, Object.class); }
        catch (Exception e) { return json; }
    }
}
