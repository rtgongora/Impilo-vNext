package zw.gov.mohcc.impilo.oros.api.controller;

import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.oros.api.dto.OrderSummaryDto;
import zw.gov.mohcc.impilo.oros.core.FulfilmentWorkflowService;
import zw.gov.mohcc.impilo.oros.core.OrderQueryService;
import zw.gov.mohcc.impilo.oros.domain.OrderType;
import zw.gov.mohcc.impilo.oros.persistence.entity.OrderEntity;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Category-agnostic fulfilment-workflow API for non-imaging orders (lab, procedure, assessment).
 * Drives the generalised {@code workflow_state} via the per-category guard, and serves the
 * category fulfilment worklists. Imaging retains its dedicated endpoints on {@code OrderController}.
 * Authorization is enforced at the Envoy/TSHEPO edge.
 */
@RestController
@RequestMapping("/v1")
public class FulfilmentWorkflowController {

    private final FulfilmentWorkflowService fulfilmentWorkflowService;
    private final OrderQueryService orderQueryService;

    public FulfilmentWorkflowController(FulfilmentWorkflowService fulfilmentWorkflowService,
                                        OrderQueryService orderQueryService) {
        this.fulfilmentWorkflowService = fulfilmentWorkflowService;
        this.orderQueryService = orderQueryService;
    }

    public record TransitionRequest(@NotBlank String target, String reason) {}

    public record ScheduleRequest(OffsetDateTime scheduledAt, String note) {}

    /** Drive a guarded fine-grained workflow transition (lab/procedure/assessment). */
    @PostMapping("/orders/{orderId}/workflow/transition")
    public ResponseEntity<ApiResponse<OrderSummaryDto>> transition(
            @PathVariable String orderId,
            @RequestBody TransitionRequest request) {
        String cid = correlationId();
        OrderEntity order = fulfilmentWorkflowService.transition(orderId, request.target(), request.reason());
        return ResponseEntity.ok(ApiResponse.ok(OrderSummaryDto.from(order), cid));
    }

    /** Schedule a procedure/assessment appointment (sets time, drives workflow to SCHEDULED). */
    @PostMapping("/orders/{orderId}/workflow/schedule")
    public ResponseEntity<ApiResponse<OrderSummaryDto>> schedule(
            @PathVariable String orderId,
            @RequestBody(required = false) ScheduleRequest request) {
        String cid = correlationId();
        OrderEntity order = fulfilmentWorkflowService.schedule(orderId,
                request != null ? request.scheduledAt() : null,
                request != null ? request.note() : null);
        return ResponseEntity.ok(ApiResponse.ok(OrderSummaryDto.from(order), cid));
    }

    /** Legal next workflow states for an order (drives UI action buttons). */
    @GetMapping("/orders/{orderId}/workflow/allowed")
    public ResponseEntity<ApiResponse<Set<String>>> allowed(@PathVariable String orderId) {
        return ResponseEntity.ok(ApiResponse.ok(fulfilmentWorkflowService.allowedNext(orderId), correlationId()));
    }

    /** Category fulfilment worklist (e.g. {@code GET /v1/fulfilment/worklist?type=PROCEDURE}). */
    @GetMapping("/fulfilment/worklist")
    public ResponseEntity<ApiResponse<List<OrderSummaryDto>>> worklist(
            @RequestParam("type") OrderType type,
            @RequestParam(name = "states", required = false) List<String> states) {
        List<OrderSummaryDto> orders = orderQueryService.workflowWorklist(type, states).stream()
                .map(OrderSummaryDto::from).collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.ok(orders, correlationId()));
    }

    private String correlationId() {
        return TrustContextHolder.require().correlationId().toString();
    }
}
