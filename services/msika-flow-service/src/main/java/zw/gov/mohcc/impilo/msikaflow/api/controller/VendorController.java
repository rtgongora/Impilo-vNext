package zw.gov.mohcc.impilo.msikaflow.api.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.msikaflow.api.TrustHeaderExtractor;
import zw.gov.mohcc.impilo.msikaflow.api.dto.*;
import zw.gov.mohcc.impilo.msikaflow.core.FulfillmentService;
import zw.gov.mohcc.impilo.msikaflow.core.OrderStateMachine;
import zw.gov.mohcc.impilo.msikaflow.core.VendorService;
import zw.gov.mohcc.impilo.msikaflow.domain.*;
import zw.gov.mohcc.impilo.msikaflow.persistence.entity.*;
import zw.gov.mohcc.impilo.msikaflow.persistence.repository.OrderRepository;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/vendors")
public class VendorController {

    private final VendorService vendorService;
    private final OrderRepository orderRepository;
    private final OrderStateMachine stateMachine;
    private final FulfillmentService fulfillmentService;

    public VendorController(VendorService vendorService, OrderRepository orderRepository,
                            OrderStateMachine stateMachine, FulfillmentService fulfillmentService) {
        this.vendorService = vendorService;
        this.orderRepository = orderRepository;
        this.stateMachine = stateMachine;
        this.fulfillmentService = fulfillmentService;
    }

    @PostMapping("/apply")
    public ResponseEntity<ApiResponse<VendorProfileEntity>> apply(
            @Valid @RequestBody VendorApplyRequest req, HttpServletRequest httpReq) {
        UUID tenantId = TrustHeaderExtractor.tenantId(httpReq);
        String correlationId = TrustHeaderExtractor.correlationId(httpReq);

        VendorType type = VendorType.valueOf(req.type());
        VendorProfileEntity vendor = vendorService.applyVendor(tenantId, req.name(), type, req.coverage());

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(vendor, correlationId));
    }

    @PostMapping("/{id}/documents/upload")
    public ResponseEntity<ApiResponse<VendorDocumentEntity>> uploadDoc(
            @PathVariable String id,
            @Valid @RequestBody VendorDocUploadRequest req,
            HttpServletRequest httpReq) {
        String correlationId = TrustHeaderExtractor.correlationId(httpReq);

        VendorDocumentEntity doc = vendorService.uploadDocument(id, req.docType(), req.landelaDocId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(doc, correlationId));
    }

    @GetMapping("/{id}/orders")
    public ResponseEntity<ApiResponse<Page<OrderEntity>>> vendorOrders(
            @PathVariable String id, Pageable pageable, HttpServletRequest httpReq) {
        String correlationId = TrustHeaderExtractor.correlationId(httpReq);

        UUID vendorId = UUID.fromString(id);
        List<OrderStatus> activeStatuses = List.of(
                OrderStatus.ROUTED, OrderStatus.ACCEPTED, OrderStatus.IN_PROGRESS,
                OrderStatus.READY_FOR_PICKUP, OrderStatus.OUT_FOR_DELIVERY);
        Page<OrderEntity> orders = orderRepository.findByVendorIdAndStatusIn(vendorId, activeStatuses, pageable);

        return ResponseEntity.ok(ApiResponse.ok(orders, correlationId));
    }

    @PostMapping("/{id}/orders/{orderId}/accept")
    public ResponseEntity<ApiResponse<OrderView>> acceptVendorOrder(
            @PathVariable String id, @PathVariable String orderId, HttpServletRequest httpReq) {
        String actorId = TrustHeaderExtractor.actorId(httpReq);
        String actorType = TrustHeaderExtractor.actorType(httpReq);
        String correlationId = TrustHeaderExtractor.correlationId(httpReq);

        fulfillmentService.acceptOrder(orderId, actorId, actorType);
        OrderEntity order = stateMachine.getOrder(orderId);
        return ResponseEntity.ok(ApiResponse.ok(OrderView.from(order, null), correlationId));
    }
}
