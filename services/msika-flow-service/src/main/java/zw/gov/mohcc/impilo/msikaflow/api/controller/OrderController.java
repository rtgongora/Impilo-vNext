package zw.gov.mohcc.impilo.msikaflow.api.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.msikaflow.api.TrustHeaderExtractor;
import zw.gov.mohcc.impilo.msikaflow.api.dto.*;
import zw.gov.mohcc.impilo.msikaflow.core.*;
import zw.gov.mohcc.impilo.msikaflow.domain.*;
import zw.gov.mohcc.impilo.msikaflow.persistence.entity.*;
import zw.gov.mohcc.impilo.msikaflow.persistence.repository.OrderRepository;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/orders")
public class OrderController {

    private final OrderStateMachine stateMachine;
    private final CatalogValidationService catalogValidation;
    private final PricingService pricingService;
    private final PaymentService paymentService;
    private final FulfillmentService fulfillmentService;
    private final OrderRepository orderRepository;

    public OrderController(OrderStateMachine stateMachine,
                           CatalogValidationService catalogValidation,
                           PricingService pricingService,
                           PaymentService paymentService,
                           FulfillmentService fulfillmentService,
                           OrderRepository orderRepository) {
        this.stateMachine = stateMachine;
        this.catalogValidation = catalogValidation;
        this.pricingService = pricingService;
        this.paymentService = paymentService;
        this.fulfillmentService = fulfillmentService;
        this.orderRepository = orderRepository;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Object>> listOrders(
            @RequestParam(required = false) UUID facility_id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            HttpServletRequest httpReq) {
        UUID tenantId = TrustHeaderExtractor.tenantId(httpReq);
        String correlationId = TrustHeaderExtractor.correlationId(httpReq);
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100));
        Page<OrderEntity> orders = facility_id != null
                ? orderRepository.findByTenantIdAndFacilityIdOrderByCreatedAtDesc(tenantId, facility_id, pageable)
                : orderRepository.findByTenantIdOrderByCreatedAtDesc(tenantId, pageable);
        List<OrderView> items = orders.getContent().stream()
                .map(order -> OrderView.from(order, stateMachine.getOrderLines(order.getOrderId())))
                .toList();
        return ResponseEntity.ok(ApiResponse.ok(new Object() {
            public final List<OrderView> items = items;
            public final int page = orders.getNumber();
            public final int size = orders.getSize();
            public final long total_elements = orders.getTotalElements();
        }, correlationId));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<OrderView>> createOrder(
            @Valid @RequestBody CreateOrderRequest req, HttpServletRequest httpReq) {
        UUID tenantId = TrustHeaderExtractor.tenantId(httpReq);
        String actorId = TrustHeaderExtractor.actorId(httpReq);
        String actorTypeStr = TrustHeaderExtractor.actorType(httpReq);
        String correlationId = TrustHeaderExtractor.correlationId(httpReq);
        ActorType actorType = ActorType.valueOf(actorTypeStr);
        OrderType orderType = OrderType.valueOf(req.orderType());

        OrderEntity order = stateMachine.createOrder(tenantId, actorId, actorType,
                req.patientCpid(), orderType,
                req.facilityId(), req.facilityRef(),
                req.vendorId(), req.vendorRef(),
                req.providerRef(),
                req.idempotencyKey());

        // Add lines if provided
        if (req.lines() != null) {
            for (LineItemRequest lineReq : req.lines()) {
                LineItemKind kind = lineReq.kind() != null ? LineItemKind.valueOf(lineReq.kind()) : LineItemKind.PRODUCT;
                FulfillmentMode mode = lineReq.fulfillmentMode() != null ? FulfillmentMode.valueOf(lineReq.fulfillmentMode()) : null;
                stateMachine.addLine(order.getOrderId(), lineReq.msikaCoreCode(), kind,
                        lineReq.qty(), lineReq.unitPrice(), mode,
                        null, lineReq.substitutionPolicy(), lineReq.metadata());
            }
        }

        List<OrderLineEntity> lines = stateMachine.getOrderLines(order.getOrderId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(OrderView.from(order, lines), correlationId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<OrderView>> getOrder(@PathVariable String id, HttpServletRequest httpReq) {
        String correlationId = TrustHeaderExtractor.correlationId(httpReq);
        OrderEntity order = stateMachine.getOrder(id);
        List<OrderLineEntity> lines = stateMachine.getOrderLines(id);
        return ResponseEntity.ok(ApiResponse.ok(OrderView.from(order, lines), correlationId));
    }

    @GetMapping("/{id}/status-summary")
    public ResponseEntity<ApiResponse<OrderStatusSummary>> getOrderStatusSummary(@PathVariable String id, HttpServletRequest httpReq) {
        String correlationId = TrustHeaderExtractor.correlationId(httpReq);
        OrderEntity order = stateMachine.getOrder(id);
        return ResponseEntity.ok(ApiResponse.ok(
                new OrderStatusSummary(
                        order.getOrderId(),
                        order.getTenantId(),
                        order.getStatus().name(),
                        order.getOrderType().name(),
                        order.getAmountTotal(),
                        order.getCurrency(),
                        order.getFacilityRef(),
                        order.getVendorRef(),
                        order.getProviderRef()
                ),
                correlationId
        ));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<ApiResponse<OrderView>> cancelOrder(@PathVariable String id, HttpServletRequest httpReq) {
        String actorId = TrustHeaderExtractor.actorId(httpReq);
        String actorType = TrustHeaderExtractor.actorType(httpReq);
        String correlationId = TrustHeaderExtractor.correlationId(httpReq);
        OrderEntity order = stateMachine.transition(id, OrderStatus.CANCELLED, actorId, actorType, "USER_CANCELLED", null);
        return ResponseEntity.ok(ApiResponse.ok(OrderView.from(order, null), correlationId));
    }

    @PostMapping("/{id}/validate")
    public ResponseEntity<ApiResponse<OrderView>> validateOrder(@PathVariable String id, HttpServletRequest httpReq) {
        String actorId = TrustHeaderExtractor.actorId(httpReq);
        String actorType = TrustHeaderExtractor.actorType(httpReq);
        String correlationId = TrustHeaderExtractor.correlationId(httpReq);
        OrderEntity order = stateMachine.transition(id, OrderStatus.VALIDATED, actorId, actorType, "ORDER_VALIDATED", null);
        return ResponseEntity.ok(ApiResponse.ok(OrderView.from(order, null), correlationId));
    }

    @PostMapping("/{id}/price")
    public ResponseEntity<ApiResponse<OrderView>> priceOrder(@PathVariable String id, HttpServletRequest httpReq) {
        String actorId = TrustHeaderExtractor.actorId(httpReq);
        String actorType = TrustHeaderExtractor.actorType(httpReq);
        String correlationId = TrustHeaderExtractor.correlationId(httpReq);
        pricingService.priceOrder(id, actorId, actorType);
        OrderEntity order = stateMachine.getOrder(id);
        List<OrderLineEntity> lines = stateMachine.getOrderLines(id);
        return ResponseEntity.ok(ApiResponse.ok(OrderView.from(order, lines), correlationId));
    }

    @PostMapping("/{id}/pay")
    public ResponseEntity<ApiResponse<Object>> payOrder(@PathVariable String id, HttpServletRequest httpReq) {
        String actorId = TrustHeaderExtractor.actorId(httpReq);
        String actorType = TrustHeaderExtractor.actorType(httpReq);
        String correlationId = TrustHeaderExtractor.correlationId(httpReq);
        SettlementEntity settlement = paymentService.createPaymentIntent(id, actorId, actorType);
        return ResponseEntity.ok(ApiResponse.ok(new Object() {
            public final String orderId = id;
            public final String mushexPaymentIntentId = settlement.getMushexPaymentIntentId();
            public final String status = settlement.getStatus().name();
        }, correlationId));
    }

    @PostMapping("/{id}/route")
    public ResponseEntity<ApiResponse<OrderView>> routeOrder(@PathVariable String id, HttpServletRequest httpReq) {
        String actorId = TrustHeaderExtractor.actorId(httpReq);
        String actorType = TrustHeaderExtractor.actorType(httpReq);
        String correlationId = TrustHeaderExtractor.correlationId(httpReq);
        fulfillmentService.routeOrder(id, actorId, actorType);
        OrderEntity order = stateMachine.getOrder(id);
        return ResponseEntity.ok(ApiResponse.ok(OrderView.from(order, null), correlationId));
    }

    @PostMapping("/{id}/accept")
    public ResponseEntity<ApiResponse<OrderView>> acceptOrder(@PathVariable String id, HttpServletRequest httpReq) {
        String actorId = TrustHeaderExtractor.actorId(httpReq);
        String actorType = TrustHeaderExtractor.actorType(httpReq);
        String correlationId = TrustHeaderExtractor.correlationId(httpReq);
        fulfillmentService.acceptOrder(id, actorId, actorType);
        OrderEntity order = stateMachine.getOrder(id);
        return ResponseEntity.ok(ApiResponse.ok(OrderView.from(order, null), correlationId));
    }

    @PostMapping("/{id}/mark-ready")
    public ResponseEntity<ApiResponse<OrderView>> markReady(@PathVariable String id, HttpServletRequest httpReq) {
        String actorId = TrustHeaderExtractor.actorId(httpReq);
        String actorType = TrustHeaderExtractor.actorType(httpReq);
        String correlationId = TrustHeaderExtractor.correlationId(httpReq);
        fulfillmentService.markReady(id, actorId, actorType);
        OrderEntity order = stateMachine.getOrder(id);
        return ResponseEntity.ok(ApiResponse.ok(OrderView.from(order, null), correlationId));
    }

    @PostMapping("/{id}/mark-delivered")
    public ResponseEntity<ApiResponse<OrderView>> markDelivered(@PathVariable String id, HttpServletRequest httpReq) {
        String actorId = TrustHeaderExtractor.actorId(httpReq);
        String actorType = TrustHeaderExtractor.actorType(httpReq);
        String correlationId = TrustHeaderExtractor.correlationId(httpReq);
        fulfillmentService.markDelivered(id, actorId, actorType);
        OrderEntity order = stateMachine.getOrder(id);
        return ResponseEntity.ok(ApiResponse.ok(OrderView.from(order, null), correlationId));
    }

    @GetMapping("/{id}/tracking")
    public ResponseEntity<ApiResponse<Object>> getTracking(@PathVariable String id, HttpServletRequest httpReq) {
        String correlationId = TrustHeaderExtractor.correlationId(httpReq);
        OrderEntity order = stateMachine.getOrder(id);
        var events = stateMachine.getOrderEvents(id);
        return ResponseEntity.ok(ApiResponse.ok(new Object() {
            public final String orderId = order.getOrderId();
            public final String status = order.getStatus().name();
            public final Object transitions = events;
        }, correlationId));
    }
}
