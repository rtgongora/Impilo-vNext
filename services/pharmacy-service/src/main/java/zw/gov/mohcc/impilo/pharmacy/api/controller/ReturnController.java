package zw.gov.mohcc.impilo.pharmacy.api.controller;

import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.pharmacy.api.dto.DispenseOrderSummaryDto;
import zw.gov.mohcc.impilo.pharmacy.api.dto.ReturnRequest;
import zw.gov.mohcc.impilo.pharmacy.api.dto.ReversalRequest;
import zw.gov.mohcc.impilo.pharmacy.api.dto.WastageRequest;
import zw.gov.mohcc.impilo.pharmacy.core.ReversalService;
import zw.gov.mohcc.impilo.pharmacy.persistence.entity.DispenseOrderEntity;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;

import java.util.UUID;

/**
 * REST controller for return, wastage, and reversal operations on dispense orders.
 *
 * <p>Handles reverse logistics including patient returns (items back to stock),
 * wastage recording (expired/damaged), and full order reversal.</p>
 */
@RestController
@RequestMapping("/v1/dispense-orders")
public class ReturnController {

    private static final Logger log = LoggerFactory.getLogger(ReturnController.class);

    private final ReversalService reversalService;

    public ReturnController(ReversalService reversalService) {
        this.reversalService = reversalService;
    }

    /**
     * Process a patient return of dispensed items.
     */
    @PostMapping("/{id}/return")
    public ResponseEntity<ApiResponse<DispenseOrderSummaryDto>> processReturn(
            @PathVariable UUID id,
            @Valid @RequestBody ReturnRequest request) {
        String correlationId = TrustContextHolder.require().correlationId().toString();

        DispenseOrderEntity order = reversalService.processReturn(id, request.items());
        log.info("Return processed: orderId={}, itemCount={}", order.getOrderId(), request.items().size());

        return ResponseEntity.ok(ApiResponse.ok(DispenseOrderSummaryDto.from(order), correlationId));
    }

    /**
     * Process wastage of dispensed items.
     */
    @PostMapping("/{id}/wastage")
    public ResponseEntity<ApiResponse<DispenseOrderSummaryDto>> processWastage(
            @PathVariable UUID id,
            @Valid @RequestBody WastageRequest request) {
        String correlationId = TrustContextHolder.require().correlationId().toString();

        DispenseOrderEntity order = reversalService.processWastage(id, request.items());
        log.info("Wastage recorded: orderId={}, itemCount={}", order.getOrderId(), request.items().size());

        return ResponseEntity.ok(ApiResponse.ok(DispenseOrderSummaryDto.from(order), correlationId));
    }

    /**
     * Process a full reversal of a dispense order.
     */
    @PostMapping("/{id}/reversal")
    public ResponseEntity<ApiResponse<DispenseOrderSummaryDto>> processReversal(
            @PathVariable UUID id,
            @Valid @RequestBody ReversalRequest request) {
        String correlationId = TrustContextHolder.require().correlationId().toString();

        DispenseOrderEntity order = reversalService.processReversal(id, request.reason());
        log.info("Reversal processed: orderId={}, reason={}", order.getOrderId(), request.reason());

        return ResponseEntity.ok(ApiResponse.ok(DispenseOrderSummaryDto.from(order), correlationId));
    }
}
