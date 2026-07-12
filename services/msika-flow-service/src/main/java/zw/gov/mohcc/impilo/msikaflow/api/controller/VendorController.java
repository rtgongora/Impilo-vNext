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

    /** Paged vendor directory for the ops console, optional {@code status} filter. */
    @GetMapping
    public ResponseEntity<ApiResponse<Page<VendorProfileEntity>>> listVendors(
            @RequestParam(required = false) String status,
            Pageable pageable, HttpServletRequest httpReq) {
        UUID tenantId = TrustHeaderExtractor.tenantId(httpReq);
        String correlationId = TrustHeaderExtractor.correlationId(httpReq);
        VendorStatus vendorStatus = (status != null && !status.isBlank()) ? VendorStatus.valueOf(status) : null;
        return ResponseEntity.ok(ApiResponse.ok(
                vendorService.listVendors(tenantId, vendorStatus, pageable), correlationId));
    }

    /**
     * Resolve the vendor bound to a JWT principal — the BFF's {@code vendor/me} seam.
     * Placed before {@code /{id}} is irrelevant (distinct path), but declared here for
     * clarity.
     */
    @GetMapping("/by-actor/{actorId}")
    public ResponseEntity<ApiResponse<VendorProfileEntity>> vendorByActor(
            @PathVariable String actorId, HttpServletRequest httpReq) {
        UUID tenantId = TrustHeaderExtractor.tenantId(httpReq);
        String correlationId = TrustHeaderExtractor.correlationId(httpReq);
        return ResponseEntity.ok(ApiResponse.ok(
                vendorService.getByActorBinding(tenantId, actorId), correlationId));
    }

    /** Vendor profile plus its uploaded documents. */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<VendorDetailView>> getVendor(
            @PathVariable String id, HttpServletRequest httpReq) {
        String correlationId = TrustHeaderExtractor.correlationId(httpReq);
        VendorProfileEntity vendor = vendorService.getVendor(id);
        List<VendorDocumentEntity> docs = vendorService.getVendorDocuments(id);
        return ResponseEntity.ok(ApiResponse.ok(new VendorDetailView(vendor, docs), correlationId));
    }

    /** Ops-gated: bind a JWT principal (actor id) to a vendor for the vendor-me seam. */
    @PostMapping("/{id}/bind-actor")
    public ResponseEntity<ApiResponse<VendorProfileEntity>> bindActor(
            @PathVariable String id, @Valid @RequestBody BindActorRequest req, HttpServletRequest httpReq) {
        String actorId = TrustHeaderExtractor.actorId(httpReq);
        String correlationId = TrustHeaderExtractor.correlationId(httpReq);
        VendorProfileEntity vendor = vendorService.bindActor(id, req.actorId(), actorId);
        return ResponseEntity.ok(ApiResponse.ok(vendor, correlationId));
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
