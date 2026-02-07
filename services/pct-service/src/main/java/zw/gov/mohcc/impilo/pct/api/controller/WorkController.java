package zw.gov.mohcc.impilo.pct.api.controller;

import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.pct.api.dto.StartShiftRequest;
import zw.gov.mohcc.impilo.pct.api.dto.WorkContextResponse;
import zw.gov.mohcc.impilo.pct.core.WorkspaceSessionService;
import zw.gov.mohcc.impilo.pct.persistence.entity.WorkspaceSessionEntity;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;

/**
 * REST controller for workspace session (shift) management.
 *
 * <p>Provides endpoints for starting and ending clinical shifts and
 * retrieving the current work context. The work context carries the
 * session, facility, workspace, and shift identifiers needed to scope
 * all subsequent PCT operations.</p>
 */
@RestController
@RequestMapping("/v1/work")
public class WorkController {

    private static final Logger log = LoggerFactory.getLogger(WorkController.class);

    private final WorkspaceSessionService workspaceSessionService;

    public WorkController(WorkspaceSessionService workspaceSessionService) {
        this.workspaceSessionService = workspaceSessionService;
    }

    /**
     * Start a new shift at a facility workspace.
     *
     * @param request the shift start request containing facility, workspace, and duty mode
     * @return the created workspace session as a work context response
     */
    @PostMapping("/start")
    public ResponseEntity<ApiResponse<WorkContextResponse>> startShift(
            @Valid @RequestBody StartShiftRequest request) {
        String correlationId = TrustContextHolder.require().correlationId().toString();

        WorkspaceSessionEntity session = workspaceSessionService.startShift(
                request.facilityId(), request.workspaceId(), request.dutyMode());

        WorkContextResponse response = toWorkContext(session);
        return ResponseEntity.ok(ApiResponse.ok(response, correlationId));
    }

    /**
     * End the current actor's active shift.
     *
     * @return acknowledgement of the shift end
     */
    @PostMapping("/end")
    public ResponseEntity<ApiResponse<String>> endShift() {
        String correlationId = TrustContextHolder.require().correlationId().toString();

        workspaceSessionService.endShift();

        return ResponseEntity.ok(ApiResponse.ok("Shift ended", correlationId));
    }

    /**
     * Retrieve the current actor's active work context.
     *
     * @return the active workspace session, or a 404 if no active session exists
     */
    @GetMapping("/context")
    public ResponseEntity<ApiResponse<WorkContextResponse>> getWorkContext() {
        String correlationId = TrustContextHolder.require().correlationId().toString();

        WorkspaceSessionEntity session = workspaceSessionService.getActiveSession();
        if (session == null) {
            return ResponseEntity.status(404)
                    .body(ApiResponse.error("NO_ACTIVE_SESSION",
                            "No active workspace session found for the current actor",
                            404, correlationId));
        }

        WorkContextResponse response = toWorkContext(session);
        return ResponseEntity.ok(ApiResponse.ok(response, correlationId));
    }

    private WorkContextResponse toWorkContext(WorkspaceSessionEntity session) {
        return new WorkContextResponse(
                session.getSessionId(),
                session.getFacilityId(),
                session.getWorkspaceId(),
                session.getDutyMode(),
                session.getShiftId(),
                session.getStartedAt()
        );
    }
}
