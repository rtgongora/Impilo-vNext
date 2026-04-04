package zw.gov.mohcc.impilo.experience.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.domain.Workspace;
import zw.gov.mohcc.impilo.experience.repository.WorkspaceRepository;
import zw.gov.mohcc.impilo.experience.service.OutboxService;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Workspace management endpoints.
 * GET  /internal/v1/workspaces — list workspaces for a facility.
 * POST /internal/v1/workspaces/{id}/activate — activate a workspace.
 */
@RestController
@RequestMapping("/internal/v1/workspaces")
public class WorkspaceController {

    private final WorkspaceRepository workspaceRepository;
    private final OutboxService outboxService;

    public WorkspaceController(WorkspaceRepository workspaceRepository, OutboxService outboxService) {
        this.workspaceRepository = workspaceRepository;
        this.outboxService = outboxService;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> listWorkspaces(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(name = "facility_id") UUID facilityId) {

        List<Workspace> workspaces = workspaceRepository.findByFacilityIdAndTenantId(facilityId, tenantId);

        List<Map<String, Object>> data = workspaces.stream().map(ws -> {
            Map<String, Object> attrs = new LinkedHashMap<>();
            attrs.put("name", ws.getName());
            attrs.put("workspaceType", ws.getWorkspaceType());
            attrs.put("facilityId", ws.getFacilityId());
            attrs.put("status", ws.getStatus());
            return Map.<String, Object>of(
                    "id", ws.getId().toString(),
                    "type", "workspace",
                    "attributes", attrs);
        }).toList();

        return ResponseEntity.ok(Map.of(
                "data", data,
                "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
    }

    @PostMapping("/{id}/activate")
    @Transactional
    public ResponseEntity<Map<String, Object>> activateWorkspace(
            @PathVariable UUID id,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.POD_ID) String podId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @RequestBody(required = false) Map<String, Object> body) {

        Workspace workspace = workspaceRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Workspace not found: " + id));

        String actorId = body != null && body.containsKey("actor_id")
                ? body.get("actor_id").toString()
                : "system";

        workspace.activate(actorId);
        workspaceRepository.save(workspace);

        outboxService.writeOutboxEvent(
                "impilo.experience.workspace.activated.v1",
                correlationId,
                requestId,
                idempotencyKey != null ? idempotencyKey : requestId,
                tenantId,
                podId,
                "Workspace",
                id.toString(),
                Map.of(
                        "workspace_id", id.toString(),
                        "activated_by", actorId,
                        "status", "ACTIVE"
                ),
                Map.of()
        );

        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("name", workspace.getName());
        attributes.put("workspace_type", workspace.getWorkspaceType());
        attributes.put("status", workspace.getStatus());
        attributes.put("activated_at", workspace.getActivatedAt());
        attributes.put("activated_by", workspace.getActivatedBy());

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", Map.of(
                "id", id.toString(),
                "type", "Workspace",
                "attributes", attributes
        ));
        response.put("meta", Map.of(
                "request_id", requestId,
                "correlation_id", correlationId
        ));

        return ResponseEntity.ok(response);
    }
}
