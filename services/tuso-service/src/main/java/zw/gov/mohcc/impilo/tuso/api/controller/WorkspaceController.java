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
import zw.gov.mohcc.impilo.tuso.core.WorkspaceService;

import java.util.List;
import java.util.UUID;

/**
 * Internal REST API for workspace management within facilities.
 *
 * Workspaces represent operational contexts (e.g., OPD, Lab, Pharmacy)
 * within a facility. They define eligibility rules, queue configuration,
 * and dashboard panels for clinical workflows.
 */
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

        var response = workspaceService.createWorkspace(facilityId, request);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(response, ctx.correlationId().toString()));
    }

    @GetMapping("/facilities/{facilityId}/workspaces")
    public ResponseEntity<ApiResponse<List<WorkspaceResponse>>> listWorkspaces(
            @PathVariable Long facilityId) {

        TrustContext ctx = TrustContextHolder.require();
        log.info("Listing workspaces [facilityId={}] correlationId={}", facilityId, ctx.correlationId());

        var response = workspaceService.listWorkspaces(facilityId);

        return ResponseEntity.ok(ApiResponse.ok(response, ctx.correlationId().toString()));
    }

    @GetMapping("/workspaces/{workspaceId}")
    public ResponseEntity<ApiResponse<WorkspaceResponse>> getWorkspace(
            @PathVariable UUID workspaceId) {

        TrustContext ctx = TrustContextHolder.require();
        log.info("Fetching workspace [workspaceId={}] correlationId={}", workspaceId, ctx.correlationId());

        var response = workspaceService.getWorkspace(workspaceId);

        return ResponseEntity.ok(ApiResponse.ok(response, ctx.correlationId().toString()));
    }

    @PutMapping("/workspaces/{workspaceId}")
    public ResponseEntity<ApiResponse<WorkspaceResponse>> updateWorkspace(
            @PathVariable UUID workspaceId,
            @Valid @RequestBody UpdateWorkspaceRequest request) {

        TrustContext ctx = TrustContextHolder.require();
        log.info("Updating workspace [workspaceId={}] correlationId={}", workspaceId, ctx.correlationId());

        var response = workspaceService.updateWorkspace(workspaceId, request);

        return ResponseEntity.ok(ApiResponse.ok(response, ctx.correlationId().toString()));
    }

    @PostMapping("/workspaces/{workspaceId}/override")
    public ResponseEntity<ApiResponse<WorkspaceResponse>> overrideWorkspace(
            @PathVariable UUID workspaceId,
            @Valid @RequestBody WorkspaceOverrideRequest request) {

        TrustContext ctx = TrustContextHolder.require();
        log.info("Workspace override [workspaceId={}, type={}, reason={}] correlationId={}",
                workspaceId, request.overrideType(), request.reason(), ctx.correlationId());

        var response = workspaceService.overrideWorkspace(
                workspaceId, request.overrideType(), request.reason(),
                request.oldValue(), request.newValue());

        return ResponseEntity.ok(ApiResponse.ok(response, ctx.correlationId().toString()));
    }
}
