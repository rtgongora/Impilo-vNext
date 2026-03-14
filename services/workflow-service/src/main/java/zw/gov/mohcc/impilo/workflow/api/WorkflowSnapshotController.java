package zw.gov.mohcc.impilo.workflow.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.RequestContext;
import zw.gov.mohcc.impilo.companion.context.RequestContextHolder;
import zw.gov.mohcc.impilo.workflow.api.dto.DefinitionResponse;
import zw.gov.mohcc.impilo.workflow.api.dto.InstanceResponse;
import zw.gov.mohcc.impilo.workflow.core.WorkflowService;
import zw.gov.mohcc.impilo.workflow.domain.WorkflowDefinitionEntity;
import zw.gov.mohcc.impilo.workflow.domain.WorkflowInstanceEntity;

import java.time.OffsetDateTime;
import java.util.*;

@RestController
public class WorkflowSnapshotController {

    private final WorkflowService workflowService;
    private final ObjectMapper objectMapper;

    public WorkflowSnapshotController(WorkflowService workflowService, ObjectMapper objectMapper) {
        this.workflowService = workflowService;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/internal/v1/snapshots/workflows")
    public ResponseEntity<?> workflowSnapshot(@RequestParam(defaultValue = "0") int cursor,
                                                @RequestParam(defaultValue = "100") int limit,
                                                @RequestParam(name = "as_of", required = false) String asOfParam) {
        RequestContext ctx = RequestContextHolder.require();
        OffsetDateTime asOf = asOfParam != null ? OffsetDateTime.parse(asOfParam) : OffsetDateTime.now();
        Page<WorkflowDefinitionEntity> page = workflowService.getDefinitionSnapshot(
                UUID.fromString(ctx.tenantId()), asOf, cursor, limit);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("items", page.getContent().stream().map(this::toDefResponse).toList());
        body.put("cursor", page.hasNext() ? String.valueOf(cursor + 1) : null);
        body.put("limit", limit);
        body.put("has_more", page.hasNext());
        body.put("as_of", asOfParam != null ? asOfParam : asOf.toString());
        return ResponseEntity.ok(body);
    }

    @GetMapping("/internal/v1/snapshots/instances")
    public ResponseEntity<?> instanceSnapshot(@RequestParam(defaultValue = "0") int cursor,
                                                @RequestParam(defaultValue = "100") int limit,
                                                @RequestParam(name = "as_of", required = false) String asOfParam) {
        RequestContext ctx = RequestContextHolder.require();
        OffsetDateTime asOf = asOfParam != null ? OffsetDateTime.parse(asOfParam) : OffsetDateTime.now();
        Page<WorkflowInstanceEntity> page = workflowService.getInstanceSnapshot(
                UUID.fromString(ctx.tenantId()), asOf, cursor, limit);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("items", page.getContent().stream().map(this::toInstResponse).toList());
        body.put("cursor", page.hasNext() ? String.valueOf(cursor + 1) : null);
        body.put("limit", limit);
        body.put("has_more", page.hasNext());
        body.put("as_of", asOfParam != null ? asOfParam : asOf.toString());
        return ResponseEntity.ok(body);
    }

    private DefinitionResponse toDefResponse(WorkflowDefinitionEntity d) {
        return new DefinitionResponse(d.getDefinitionId(), d.getTenantId(), d.getCode(), d.getName(),
                d.getDescription(), d.getCategory(), d.getVersion(), d.getStatus(),
                parseJsonSafe(d.getSchemaJson()), parseJsonSafe(d.getStepsJson()),
                d.getCreatedAt(), d.getUpdatedAt(), d.getPublishedAt());
    }

    private InstanceResponse toInstResponse(WorkflowInstanceEntity i) {
        return new InstanceResponse(i.getInstanceId(), i.getDefinitionId(), i.getTenantId(),
                i.getStatus(), i.getCurrentStep(), parseJsonSafe(i.getContextJson()),
                i.getInitiatorRef(), i.getSubjectRef(),
                i.getStartedAt(), i.getCompletedAt(), i.getCreatedAt(), i.getUpdatedAt());
    }

    private Object parseJsonSafe(String json) {
        if (json == null) return null;
        try { return objectMapper.readValue(json, Object.class); }
        catch (Exception e) { return json; }
    }
}
