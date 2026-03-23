package zw.gov.mohcc.impilo.tuso.api.controller;

import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;
import zw.gov.mohcc.impilo.tuso.api.dto.CreateWorkspaceRequest;
import zw.gov.mohcc.impilo.tuso.api.dto.UpdateWorkspaceRequest;
import zw.gov.mohcc.impilo.tuso.api.dto.WorkspaceOverrideRequest;
import zw.gov.mohcc.impilo.tuso.api.dto.WorkspaceResponse;
import zw.gov.mohcc.impilo.tuso.api.dto.WorkspaceRuleDto;
import zw.gov.mohcc.impilo.tuso.core.WorkspaceService;
import zw.gov.mohcc.impilo.tuso.persistence.entity.WorkspaceEntity;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/internal")
public class WorkspaceController {

    private static final Logger log = LoggerFactory.getLogger(WorkspaceController.class);

    private final WorkspaceService workspaceService;

    public WorkspaceController(WorkspaceService workspaceService) {
        this.workspaceService = workspaceService;
    }

    @PostMapping("/facilities/{facilityId}/workspaces")
    public ResponseEntity<ApiResponse<WorkspaceResponse>> createWorkspace(
            @PathVariable Long facilityId,
            @Valid @RequestBody CreateWorkspaceRequest request) {

        TrustContext ctx = TrustContextHolder.require();
        log.info("Creating workspace [facilityId={}, name={}, type={}] correlationId={}",
                facilityId, request.name(), request.workspaceType(), ctx.correlationId());

        WorkspaceEntity entity = workspaceService.createWorkspace(facilityId, toServiceCreate(request));

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(toResponse(entity), ctx.correlationId().toString()));
    }

    @GetMapping("/facilities/{facilityId}/workspaces")
    public ResponseEntity<ApiResponse<List<WorkspaceResponse>>> listWorkspaces(
            @PathVariable Long facilityId) {

        TrustContext ctx = TrustContextHolder.require();
        log.info("Listing workspaces [facilityId={}] correlationId={}", facilityId, ctx.correlationId());

        List<WorkspaceResponse> response = workspaceService.listWorkspaces(facilityId)
                .stream().map(this::toResponse).toList();

        return ResponseEntity.ok(ApiResponse.ok(response, ctx.correlationId().toString()));
    }

    @GetMapping("/workspaces/{workspaceId}")
    public ResponseEntity<ApiResponse<WorkspaceResponse>> getWorkspace(
            @PathVariable UUID workspaceId) {

        TrustContext ctx = TrustContextHolder.require();
        log.info("Fetching workspace [workspaceId={}] correlationId={}", workspaceId, ctx.correlationId());

        WorkspaceResponse response = toResponse(workspaceService.getWorkspace(workspaceId));

        return ResponseEntity.ok(ApiResponse.ok(response, ctx.correlationId().toString()));
    }

    @PutMapping("/workspaces/{workspaceId}")
    public ResponseEntity<ApiResponse<WorkspaceResponse>> updateWorkspace(
            @PathVariable UUID workspaceId,
            @Valid @RequestBody UpdateWorkspaceRequest request) {

        TrustContext ctx = TrustContextHolder.require();
        log.info("Updating workspace [workspaceId={}] correlationId={}", workspaceId, ctx.correlationId());

        WorkspaceEntity entity = workspaceService.updateWorkspace(workspaceId, toServiceUpdate(request));

        return ResponseEntity.ok(ApiResponse.ok(toResponse(entity), ctx.correlationId().toString()));
    }

    @PostMapping("/workspaces/{workspaceId}/override")
    public ResponseEntity<ApiResponse<WorkspaceResponse>> overrideWorkspace(
            @PathVariable UUID workspaceId,
            @Valid @RequestBody WorkspaceOverrideRequest request) {

        TrustContext ctx = TrustContextHolder.require();
        log.info("Workspace override [workspaceId={}, type={}, reason={}] correlationId={}",
                workspaceId, request.overrideType(), request.reason(), ctx.correlationId());

        WorkspaceEntity entity = workspaceService.overrideWorkspace(
                workspaceId, request.overrideType(), request.reason(),
                request.oldValue(), request.newValue()).getWorkspace();

        return ResponseEntity.ok(ApiResponse.ok(toResponse(entity), ctx.correlationId().toString()));
    }

    // ---- Mappers: API request → service DTO ----

    private WorkspaceService.CreateWorkspaceRequest toServiceCreate(CreateWorkspaceRequest api) {
        List<WorkspaceService.WorkspaceRuleData> rules = api.rules() != null
                ? api.rules().stream()
                    .map(r -> new WorkspaceService.WorkspaceRuleData(
                            r.ruleType(), r.actorType(), r.role(), r.privilege(), r.required(), null))
                    .toList()
                : null;
        List<WorkspaceService.DashboardData> dashboards = api.dashboards() != null
                ? api.dashboards().stream()
                    .map(name -> new WorkspaceService.DashboardData(name, null, null, null))
                    .toList()
                : null;
        return new WorkspaceService.CreateWorkspaceRequest(
                api.name(), api.workspaceType(), api.description(),
                api.queueConfig(), api.defaultPanels(), rules, dashboards);
    }

    private WorkspaceService.UpdateWorkspaceRequest toServiceUpdate(UpdateWorkspaceRequest api) {
        return new WorkspaceService.UpdateWorkspaceRequest(
                api.name(), api.workspaceType(), api.description(),
                api.queueConfig(), api.defaultPanels(), null);
    }

    // ---- Mappers: entity/detail → response DTO ----

    private WorkspaceResponse toResponse(WorkspaceEntity ws) {
        return new WorkspaceResponse(
                ws.getId(),
                ws.getFacility().getId(),
                ws.getName(),
                ws.getWorkspaceType(),
                ws.getZiboValidated() != null && ws.getZiboValidated(),
                ws.getDescription(),
                ws.getQueueConfig(),
                ws.getDefaultPanels(),
                ws.getActive() != null && ws.getActive(),
                List.of(),
                List.of(),
                ws.getCreatedAt(),
                ws.getUpdatedAt(),
                ws.getCreatedBy(),
                ws.getUpdatedBy());
    }

    private WorkspaceResponse toResponse(WorkspaceService.WorkspaceDetail detail) {
        WorkspaceEntity ws = detail.workspace();
        List<WorkspaceRuleDto> rules = detail.rules().stream()
                .map(r -> new WorkspaceRuleDto(
                        r.getRuleType(), r.getActorType(), r.getRole(), r.getPrivilege(),
                        r.getRequired() != null && r.getRequired()))
                .toList();
        List<WorkspaceResponse.DashboardDetail> dashboards = detail.dashboards().stream()
                .map(d -> new WorkspaceResponse.DashboardDetail(
                        d.getId(), d.getPanelName(), d.getPanelType(), d.getConfig(),
                        d.getSortOrder() != null ? d.getSortOrder() : 0,
                        d.getActive() != null && d.getActive()))
                .toList();
        return new WorkspaceResponse(
                ws.getId(),
                ws.getFacility().getId(),
                ws.getName(),
                ws.getWorkspaceType(),
                ws.getZiboValidated() != null && ws.getZiboValidated(),
                ws.getDescription(),
                ws.getQueueConfig(),
                ws.getDefaultPanels(),
                ws.getActive() != null && ws.getActive(),
                rules,
                dashboards,
                ws.getCreatedAt(),
                ws.getUpdatedAt(),
                ws.getCreatedBy(),
                ws.getUpdatedBy());
    }
}
