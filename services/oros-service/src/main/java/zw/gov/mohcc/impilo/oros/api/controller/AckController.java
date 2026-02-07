package zw.gov.mohcc.impilo.oros.api.controller;

import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.oros.api.dto.AckRequest;
import zw.gov.mohcc.impilo.oros.core.AcknowledgementService;
import zw.gov.mohcc.impilo.oros.domain.AckType;
import zw.gov.mohcc.impilo.oros.persistence.entity.AcknowledgementEntity;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for order acknowledgement workflows.
 *
 * <p>Supports department, clinician, and critical value acknowledgements.
 * Acknowledgements are tracked per-order with the acting user and timestamp.</p>
 */
@RestController
@RequestMapping("/v1/orders/{orderId}/ack")
public class AckController {

    private static final Logger log = LoggerFactory.getLogger(AckController.class);

    private final AcknowledgementService ackService;

    public AckController(AcknowledgementService ackService) {
        this.ackService = ackService;
    }

    /**
     * Record an acknowledgement for an order.
     */
    @PostMapping
    public ResponseEntity<ApiResponse<AckResponse>> acknowledge(
            @PathVariable String orderId,
            @Valid @RequestBody AckRequest request) {
        String correlationId = TrustContextHolder.require().correlationId().toString();

        AcknowledgementEntity ack = ackService.acknowledge(
                orderId, request.ackType(), request.notes());

        AckResponse response = new AckResponse(
                ack.getAckId(), ack.getOrderId(), ack.getAckType(),
                ack.getActorId(), ack.getNotes(), ack.getAckedAt().toString());

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(response, correlationId));
    }

    /**
     * Get all acknowledgements for an order.
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<AckResponse>>> getAcks(
            @PathVariable String orderId) {
        String correlationId = TrustContextHolder.require().correlationId().toString();

        List<AckResponse> acks = ackService.getAcks(orderId).stream()
                .map(ack -> new AckResponse(
                        ack.getAckId(), ack.getOrderId(), ack.getAckType(),
                        ack.getActorId(), ack.getNotes(), ack.getAckedAt().toString()))
                .toList();

        return ResponseEntity.ok(ApiResponse.ok(acks, correlationId));
    }

    /**
     * Response DTO for acknowledgement data.
     */
    record AckResponse(UUID ackId, String orderId, AckType ackType,
                       String actorId, String notes, String ackedAt) {}
}
