package zw.gov.mohcc.impilo.pharmacy.api.controller;

import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.pharmacy.api.dto.BackorderRequest;
import zw.gov.mohcc.impilo.pharmacy.api.dto.DispenseItemDto;
import zw.gov.mohcc.impilo.pharmacy.api.dto.DispenseOrderSummaryDto;
import zw.gov.mohcc.impilo.pharmacy.api.dto.PickRequest;
import zw.gov.mohcc.impilo.pharmacy.core.DispenseEngine;
import zw.gov.mohcc.impilo.pharmacy.persistence.entity.DispenseOrderEntity;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * REST controller for dispense order management.
 *
 * <p>Provides endpoints for the full dispense lifecycle: retrieving orders,
 * accepting, picking items, partial/full dispense, and backorder.</p>
 */
@RestController
@RequestMapping("/v1/dispense-orders")
public class DispenseController {

    private static final Logger log = LoggerFactory.getLogger(DispenseController.class);

    private final DispenseEngine dispenseEngine;

    public DispenseController(DispenseEngine dispenseEngine) {
        this.dispenseEngine = dispenseEngine;
    }

    /**
     * Get a single dispense order by its ID.
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<DispenseOrderSummaryDto>> getOrder(@PathVariable UUID id) {
        String correlationId = TrustContextHolder.require().correlationId().toString();

        DispenseOrderEntity order = dispenseEngine.getOrder(id);
        return ResponseEntity.ok(ApiResponse.ok(DispenseOrderSummaryDto.from(order), correlationId));
    }

    /**
     * Get all dispense orders for a patient by CPID.
     */
    @GetMapping("/patient/{cpid}")
    public ResponseEntity<ApiResponse<List<DispenseOrderSummaryDto>>> getPatientOrders(
            @PathVariable String cpid) {
        String correlationId = TrustContextHolder.require().correlationId().toString();

        List<DispenseOrderSummaryDto> orders = dispenseEngine.getPatientOrders(cpid).stream()
                .map(DispenseOrderSummaryDto::from)
                .collect(Collectors.toList());

        return ResponseEntity.ok(ApiResponse.ok(orders, correlationId));
    }

    /**
     * Accept a pending dispense order, assigning it to the current pharmacist.
     */
    @PostMapping("/{id}/accept")
    public ResponseEntity<ApiResponse<DispenseOrderSummaryDto>> acceptOrder(@PathVariable UUID id) {
        String correlationId = TrustContextHolder.require().correlationId().toString();

        DispenseOrderEntity order = dispenseEngine.acceptOrder(id);
        log.info("Dispense order accepted: orderId={}, acceptedBy={}",
                order.getOrderId(), order.getAcceptedBy());

        return ResponseEntity.ok(ApiResponse.ok(DispenseOrderSummaryDto.from(order), correlationId));
    }

    /**
     * Pick items from shelves for a dispense order.
     */
    @PostMapping("/{id}/pick")
    public ResponseEntity<ApiResponse<DispenseOrderSummaryDto>> pickItems(
            @PathVariable UUID id,
            @Valid @RequestBody PickRequest request) {
        String correlationId = TrustContextHolder.require().correlationId().toString();

        DispenseOrderEntity order = dispenseEngine.pickItems(id, request.items());
        log.info("Items picked for dispense order: orderId={}, itemCount={}",
                order.getOrderId(), request.items().size());

        return ResponseEntity.ok(ApiResponse.ok(DispenseOrderSummaryDto.from(order), correlationId));
    }

    /**
     * Complete a partial dispense (some items dispensed, others outstanding).
     */
    @PostMapping("/{id}/partial")
    public ResponseEntity<ApiResponse<DispenseOrderSummaryDto>> partialDispense(@PathVariable UUID id) {
        String correlationId = TrustContextHolder.require().correlationId().toString();

        DispenseOrderEntity order = dispenseEngine.partialDispense(id);
        log.info("Partial dispense completed: orderId={}", order.getOrderId());

        return ResponseEntity.ok(ApiResponse.ok(DispenseOrderSummaryDto.from(order), correlationId));
    }

    /**
     * Complete the full dispense, recording all stock movements.
     */
    @PostMapping("/{id}/complete")
    public ResponseEntity<ApiResponse<DispenseOrderSummaryDto>> completeDispense(@PathVariable UUID id) {
        String correlationId = TrustContextHolder.require().correlationId().toString();

        DispenseOrderEntity order = dispenseEngine.completeDispense(id);
        log.info("Dispense completed: orderId={}, completedBy={}",
                order.getOrderId(), order.getCompletedBy());

        return ResponseEntity.ok(ApiResponse.ok(DispenseOrderSummaryDto.from(order), correlationId));
    }

    /**
     * Place the order on backorder with operational notes.
     */
    @PostMapping("/{id}/backorder")
    public ResponseEntity<ApiResponse<DispenseOrderSummaryDto>> backorder(
            @PathVariable UUID id,
            @Valid @RequestBody BackorderRequest request) {
        String correlationId = TrustContextHolder.require().correlationId().toString();

        DispenseOrderEntity order = dispenseEngine.backorder(id, request.notes());
        log.info("Dispense order placed on backorder: orderId={}", order.getOrderId());

        return ResponseEntity.ok(ApiResponse.ok(DispenseOrderSummaryDto.from(order), correlationId));
    }
}
