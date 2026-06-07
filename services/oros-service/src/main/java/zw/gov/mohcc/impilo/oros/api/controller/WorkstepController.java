package zw.gov.mohcc.impilo.oros.api.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.oros.api.dto.WorkstepDto;
import zw.gov.mohcc.impilo.oros.core.WorkstepEngine;
import zw.gov.mohcc.impilo.oros.persistence.entity.WorkstepEntity;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * REST controller for workstep management.
 *
 * <p>Provides endpoints for viewing worksteps on an order and
 * for starting and completing individual worksteps.</p>
 */
@RestController
@RequestMapping("/v1")
public class WorkstepController {

    private static final Logger log = LoggerFactory.getLogger(WorkstepController.class);

    private final WorkstepEngine workstepEngine;

    public WorkstepController(WorkstepEngine workstepEngine) {
        this.workstepEngine = workstepEngine;
    }

    /**
     * Get all worksteps for an order, in creation order.
     */
    @GetMapping({"/orders/{orderId}/worksteps", "/worksteps"})
    public ResponseEntity<ApiResponse<List<WorkstepDto>>> getWorksteps(
            @PathVariable(required = false) String orderId,
            @RequestParam(required = false) String orderIdQuery) {
        String correlationId = TrustContextHolder.require().correlationId().toString();
        String targetOrderId = orderId != null ? orderId : orderIdQuery;
        if (targetOrderId == null || targetOrderId.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("OROS_MISSING_ORDER_ID", "orderId is required", 400, correlationId));
        }

        List<WorkstepDto> steps = workstepEngine.getWorksteps(targetOrderId).stream()
                .map(WorkstepDto::from)
                .collect(Collectors.toList());

        return ResponseEntity.ok(ApiResponse.ok(steps, correlationId));
    }

    /**
     * Start a workstep. Only PENDING worksteps can be started.
     */
    @PostMapping("/worksteps/{workstepId}/start")
    public ResponseEntity<ApiResponse<WorkstepDto>> startWorkstep(
            @PathVariable UUID workstepId) {
        String correlationId = TrustContextHolder.require().correlationId().toString();

        WorkstepEntity step = workstepEngine.startWorkstep(workstepId);
        return ResponseEntity.ok(ApiResponse.ok(WorkstepDto.from(step), correlationId));
    }

    /**
     * Complete a workstep. Only IN_PROGRESS worksteps can be completed.
     */
    @PostMapping("/worksteps/{workstepId}/complete")
    public ResponseEntity<ApiResponse<WorkstepDto>> completeWorkstep(
            @PathVariable UUID workstepId) {
        String correlationId = TrustContextHolder.require().correlationId().toString();

        WorkstepEntity step = workstepEngine.completeWorkstep(workstepId);
        return ResponseEntity.ok(ApiResponse.ok(WorkstepDto.from(step), correlationId));
    }
}
