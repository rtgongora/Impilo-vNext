package zw.gov.mohcc.impilo.oros.api.controller;

import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.oros.api.dto.OrderSummaryDto;
import zw.gov.mohcc.impilo.oros.api.dto.RejectRequest;
import zw.gov.mohcc.impilo.oros.core.WorklistService;
import zw.gov.mohcc.impilo.oros.domain.OrderPriority;
import zw.gov.mohcc.impilo.oros.domain.OrderStatus;
import zw.gov.mohcc.impilo.oros.domain.OrderType;
import zw.gov.mohcc.impilo.oros.persistence.entity.OrderEntity;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;
import zw.gov.mohcc.impilo.shared.response.PagedResponse;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * REST controller for worklist operations.
 *
 * <p>Provides a filtered view of orders at a facility with support
 * for accepting and rejecting orders from the worklist.</p>
 */
@RestController
@RequestMapping("/v1")
public class WorklistController {

    private static final Logger log = LoggerFactory.getLogger(WorklistController.class);

    private final WorklistService worklistService;

    public WorklistController(WorklistService worklistService) {
        this.worklistService = worklistService;
    }

    /**
     * Get the worklist for a facility, filtered by optional parameters.
     */
    @GetMapping("/worklists")
    public ResponseEntity<ApiResponse<PagedResponse<OrderSummaryDto>>> getWorklist(
            @RequestParam UUID facilityId,
            @RequestParam(required = false) UUID workspaceId,
            @RequestParam(required = false) OrderType type,
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(required = false) OrderPriority priority,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        String correlationId = TrustContextHolder.require().correlationId().toString();

        Page<OrderEntity> result = worklistService.getWorklist(
                facilityId, workspaceId, type, status, priority, PageRequest.of(page, size));

        List<OrderSummaryDto> items = result.getContent().stream()
                .map(OrderSummaryDto::from)
                .collect(Collectors.toList());

        PagedResponse<OrderSummaryDto> pagedResponse = PagedResponse.of(
                items, page, size, result.getTotalElements());

        return ResponseEntity.ok(ApiResponse.ok(pagedResponse, correlationId));
    }

    /**
     * Accept an order from the worklist.
     */
    @PostMapping("/orders/{orderId}/accept")
    public ResponseEntity<ApiResponse<OrderSummaryDto>> acceptOrder(@PathVariable String orderId) {
        String correlationId = TrustContextHolder.require().correlationId().toString();

        OrderEntity order = worklistService.acceptOrder(orderId);
        return ResponseEntity.ok(ApiResponse.ok(OrderSummaryDto.from(order), correlationId));
    }

    /**
     * Reject an order from the worklist with a reason.
     */
    @PostMapping("/orders/{orderId}/reject")
    public ResponseEntity<ApiResponse<OrderSummaryDto>> rejectOrder(
            @PathVariable String orderId,
            @Valid @RequestBody RejectRequest request) {
        String correlationId = TrustContextHolder.require().correlationId().toString();

        OrderEntity order = worklistService.rejectOrder(orderId, request.reason());
        return ResponseEntity.ok(ApiResponse.ok(OrderSummaryDto.from(order), correlationId));
    }
}
