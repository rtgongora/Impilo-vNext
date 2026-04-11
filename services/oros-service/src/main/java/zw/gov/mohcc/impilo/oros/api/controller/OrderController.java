package zw.gov.mohcc.impilo.oros.api.controller;

import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.oros.api.dto.CancelRequest;
import zw.gov.mohcc.impilo.oros.api.dto.OrderItemDto;
import zw.gov.mohcc.impilo.oros.api.dto.OrderSummaryDto;
import zw.gov.mohcc.impilo.oros.api.dto.PlaceOrderRequest;
import zw.gov.mohcc.impilo.oros.core.*;
import zw.gov.mohcc.impilo.oros.persistence.entity.OrderEntity;
import zw.gov.mohcc.impilo.oros.persistence.entity.RoutingEntity;
import zw.gov.mohcc.impilo.oros.persistence.entity.SlaTimerEntity;
import zw.gov.mohcc.impilo.oros.persistence.entity.WorkstepEntity;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;

import java.util.List;
import java.util.stream.Collectors;

/**
 * REST controller for clinical order management.
 *
 * <p>Provides endpoints for placing, retrieving, and cancelling orders.
 * Order placement orchestrates the full initial workflow: order creation,
 * routing, workstep generation, and SLA timer start.</p>
 */
@RestController
@RequestMapping("/v1/orders")
public class OrderController {

    private static final Logger log = LoggerFactory.getLogger(OrderController.class);

    private final OrderStateMachine stateMachine;
    private final RoutingEngine routingEngine;
    private final WorkstepEngine workstepEngine;
    private final SlaService slaService;

    public OrderController(OrderStateMachine stateMachine,
                           RoutingEngine routingEngine,
                           WorkstepEngine workstepEngine,
                           SlaService slaService) {
        this.stateMachine = stateMachine;
        this.routingEngine = routingEngine;
        this.workstepEngine = workstepEngine;
        this.slaService = slaService;
    }

    /**
     * Place a new clinical order.
     *
     * <p>Orchestrates the full placement flow:</p>
     * <ol>
     *   <li>Create the order via the state machine</li>
     *   <li>Route the order based on facility capabilities</li>
     *   <li>Create worksteps from the order type template</li>
     *   <li>Start the SLA timer</li>
     * </ol>
     */
    @PostMapping
    public ResponseEntity<ApiResponse<OrderSummaryDto>> placeOrder(
            @Valid @RequestBody PlaceOrderRequest request) {
        TrustContext ctx = TrustContextHolder.require();
        String correlationId = ctx.correlationId().toString();

        List<OrderStateMachine.OrderItemData> items = null;
        if (request.items() != null) {
            items = request.items().stream()
                    .map(dto -> new OrderStateMachine.OrderItemData(
                            dto.codingSystem(), dto.code(), dto.displayName(),
                            dto.quantity(), dto.instructions(),
                            dto.specimenType(), dto.bodySite()))
                    .collect(Collectors.toList());
        }

        // Step 1: Place the order
        OrderEntity order = stateMachine.placeOrder(
                ctx.facilityId(),
                request.patientCpid(),
                request.orderType(),
                request.priority(),
                request.ziboOrderCode(),
                request.encounterRef(),
                request.clinicalNotes(),
                items);

        // Step 2: Route the order
        RoutingEntity route = routingEngine.routeOrder(order);

        // Step 3: Create worksteps
        List<WorkstepEntity> worksteps = workstepEngine.createWorkstepsForOrder(order);

        // Step 4: Start SLA timer (OVERALL stage, 0 = use default TAT from config)
        SlaTimerEntity timer = slaService.startTimer(order.getOrderId(), "OVERALL", 0);

        log.info("Order placed and routed: orderId={}, route={}, worksteps={}, slaTarget={}",
                order.getOrderId(), route.getRouteTarget(), worksteps.size(), timer.getTargetAt());

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(OrderSummaryDto.from(order), correlationId));
    }

    /**
     * Get a single order by its ID.
     */
    @GetMapping("/{orderId}")
    public ResponseEntity<ApiResponse<OrderSummaryDto>> getOrder(@PathVariable String orderId) {
        String correlationId = TrustContextHolder.require().correlationId().toString();

        OrderEntity order = stateMachine.getOrder(orderId);
        return ResponseEntity.ok(ApiResponse.ok(OrderSummaryDto.from(order), correlationId));
    }

    /**
     * Get all orders for a patient by CPID.
     */
    @GetMapping("/patient/{cpid}")
    public ResponseEntity<ApiResponse<List<OrderSummaryDto>>> getPatientOrders(
            @PathVariable String cpid) {
        String correlationId = TrustContextHolder.require().correlationId().toString();

        List<OrderSummaryDto> orders = stateMachine.getPatientOrders(cpid).stream()
                .map(OrderSummaryDto::from)
                .collect(Collectors.toList());

        return ResponseEntity.ok(ApiResponse.ok(orders, correlationId));
    }

    /**
     * Cancel an existing order.
     */
    @PostMapping("/{orderId}/cancel")
    public ResponseEntity<ApiResponse<OrderSummaryDto>> cancelOrder(
            @PathVariable String orderId,
            @Valid @RequestBody CancelRequest request) {
        String correlationId = TrustContextHolder.require().correlationId().toString();

        OrderEntity order = stateMachine.cancelOrder(orderId, request.reason());
        return ResponseEntity.ok(ApiResponse.ok(OrderSummaryDto.from(order), correlationId));
    }
}
