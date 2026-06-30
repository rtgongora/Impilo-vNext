package zw.gov.mohcc.impilo.inventory.api.controller;

import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.inventory.api.dto.*;
import zw.gov.mohcc.impilo.inventory.core.SupplierService;
import zw.gov.mohcc.impilo.inventory.persistence.entity.SupplierCatalogueItemEntity;
import zw.gov.mohcc.impilo.inventory.persistence.entity.SupplierOrderEntity;
import zw.gov.mohcc.impilo.inventory.persistence.entity.SupplierProfileEntity;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Dura supplier/seller mode REST API ({@code /v1/dura/suppliers}).
 */
@RestController
@RequestMapping("/v1/dura/suppliers")
public class DuraSupplierController {

    private static final Logger log = LoggerFactory.getLogger(DuraSupplierController.class);

    private final SupplierService supplierService;

    public DuraSupplierController(SupplierService supplierService) {
        this.supplierService = supplierService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<SupplierProfileDto>> register(
            @Valid @RequestBody RegisterSupplierRequest request) {
        String correlationId = TrustContextHolder.require().correlationId().toString();
        SupplierProfileEntity supplier = supplierService.registerSupplier(
                request.name(), request.type(), request.licenceNumber(), request.contact());
        log.info("Supplier registered via API: name={}", request.name());
        return ResponseEntity.ok(ApiResponse.ok(SupplierProfileDto.from(supplier), correlationId));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<SupplierProfileDto>>> list() {
        String correlationId = TrustContextHolder.require().correlationId().toString();
        List<SupplierProfileDto> dtos = supplierService.listSuppliers().stream()
                .map(SupplierProfileDto::from)
                .collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.ok(dtos, correlationId));
    }

    @GetMapping("/{supplierId}")
    public ResponseEntity<ApiResponse<SupplierProfileDto>> get(@PathVariable UUID supplierId) {
        String correlationId = TrustContextHolder.require().correlationId().toString();
        return ResponseEntity.ok(ApiResponse.ok(
                SupplierProfileDto.from(supplierService.getSupplier(supplierId)), correlationId));
    }

    // ── Catalogue ───────────────────────────────────────────────

    @PostMapping("/{supplierId}/catalogue")
    public ResponseEntity<ApiResponse<SupplierCatalogueItemDto>> upsertCatalogue(
            @PathVariable UUID supplierId,
            @Valid @RequestBody UpsertCatalogueItemRequest request) {
        String correlationId = TrustContextHolder.require().correlationId().toString();
        SupplierCatalogueItemEntity item = supplierService.upsertCatalogueItem(
                supplierId, request.itemCode(), request.itemName(), request.packSize(),
                request.unitPrice(), request.currency(), request.availableQty());
        return ResponseEntity.ok(ApiResponse.ok(SupplierCatalogueItemDto.from(item), correlationId));
    }

    @GetMapping("/{supplierId}/catalogue")
    public ResponseEntity<ApiResponse<List<SupplierCatalogueItemDto>>> listCatalogue(@PathVariable UUID supplierId) {
        String correlationId = TrustContextHolder.require().correlationId().toString();
        List<SupplierCatalogueItemDto> dtos = supplierService.listCatalogue(supplierId).stream()
                .map(SupplierCatalogueItemDto::from)
                .collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.ok(dtos, correlationId));
    }

    // ── Orders ──────────────────────────────────────────────────

    @PostMapping("/{supplierId}/orders")
    public ResponseEntity<ApiResponse<SupplierOrderDto>> placeOrder(
            @PathVariable UUID supplierId,
            @Valid @RequestBody PlaceSupplierOrderRequest request) {
        String correlationId = TrustContextHolder.require().correlationId().toString();
        List<SupplierService.OrderLineInput> lines = request.lines().stream()
                .map(l -> new SupplierService.OrderLineInput(l.itemCode(), l.itemName(), l.qty(), l.unitPrice()))
                .collect(Collectors.toList());
        SupplierOrderEntity order = supplierService.placeOrder(
                supplierId, request.buyerFacilityId(), request.currency(), lines, request.notes());
        log.info("Supplier order placed via API: supplier={}, total={}", supplierId, order.getTotalAmount());
        return ResponseEntity.ok(ApiResponse.ok(orderWithLines(order.getSupplierOrderId(), order), correlationId));
    }

    @GetMapping("/orders")
    public ResponseEntity<ApiResponse<List<SupplierOrderDto>>> listOrders(
            @RequestParam(value = "supplierId", required = false) UUID supplierId) {
        String correlationId = TrustContextHolder.require().correlationId().toString();
        List<SupplierOrderDto> dtos = supplierService.listOrders(supplierId).stream()
                .map(SupplierOrderDto::from)
                .collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.ok(dtos, correlationId));
    }

    @GetMapping("/orders/{orderId}")
    public ResponseEntity<ApiResponse<SupplierOrderDto>> getOrder(@PathVariable UUID orderId) {
        String correlationId = TrustContextHolder.require().correlationId().toString();
        SupplierOrderEntity order = supplierService.getOrder(orderId);
        return ResponseEntity.ok(ApiResponse.ok(orderWithLines(orderId, order), correlationId));
    }

    @PostMapping("/orders/{orderId}/status")
    public ResponseEntity<ApiResponse<SupplierOrderDto>> updateOrderStatus(
            @PathVariable UUID orderId,
            @Valid @RequestBody UpdateSupplierOrderStatusRequest request) {
        String correlationId = TrustContextHolder.require().correlationId().toString();
        SupplierOrderEntity order = supplierService.updateOrderStatus(orderId, request.status());
        return ResponseEntity.ok(ApiResponse.ok(orderWithLines(orderId, order), correlationId));
    }

    private SupplierOrderDto orderWithLines(UUID orderId, SupplierOrderEntity order) {
        List<SupplierOrderLineDto> lines = supplierService.orderLines(orderId).stream()
                .map(SupplierOrderLineDto::from)
                .collect(Collectors.toList());
        return SupplierOrderDto.from(order, lines);
    }
}
