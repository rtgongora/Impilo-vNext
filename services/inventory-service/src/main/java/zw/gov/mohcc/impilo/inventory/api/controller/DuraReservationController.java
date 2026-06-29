package zw.gov.mohcc.impilo.inventory.api.controller;

import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.inventory.api.dto.CreateReservationRequest;
import zw.gov.mohcc.impilo.inventory.api.dto.ReservationDto;
import zw.gov.mohcc.impilo.inventory.core.ReservationService;
import zw.gov.mohcc.impilo.inventory.persistence.entity.StockReservationEntity;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Dura stock reservation REST API ({@code /v1/dura/reservations}).
 */
@RestController
@RequestMapping("/v1/dura/reservations")
public class DuraReservationController {

    private static final Logger log = LoggerFactory.getLogger(DuraReservationController.class);

    private final ReservationService reservationService;

    public DuraReservationController(ReservationService reservationService) {
        this.reservationService = reservationService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ReservationDto>> reserve(
            @Valid @RequestBody CreateReservationRequest request) {
        String correlationId = TrustContextHolder.require().correlationId().toString();
        StockReservationEntity reservation = reservationService.reserve(
                request.facilityId(), request.storeId(), request.itemCode(), request.batchLotId(),
                request.qty(), request.uom(), request.refType(), request.refId(),
                request.reason(), request.expiresAt());
        log.info("Reservation created via API: itemCode={}, qty={}", request.itemCode(), request.qty());
        return ResponseEntity.ok(ApiResponse.ok(ReservationDto.from(reservation), correlationId));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<ReservationDto>>> listActive(
            @RequestParam("facilityId") UUID facilityId,
            @RequestParam("storeId") UUID storeId,
            @RequestParam("itemCode") String itemCode) {
        String correlationId = TrustContextHolder.require().correlationId().toString();
        List<ReservationDto> dtos = reservationService.listActive(facilityId, storeId, itemCode).stream()
                .map(ReservationDto::from)
                .collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.ok(dtos, correlationId));
    }

    @GetMapping("/availability")
    public ResponseEntity<ApiResponse<Map<String, Integer>>> availability(
            @RequestParam("facilityId") UUID facilityId,
            @RequestParam("storeId") UUID storeId,
            @RequestParam("itemCode") String itemCode) {
        String correlationId = TrustContextHolder.require().correlationId().toString();
        int reserved = reservationService.reservedQuantity(facilityId, storeId, itemCode);
        int available = reservationService.availableQuantity(facilityId, storeId, itemCode);
        Map<String, Integer> body = Map.of(
                "available", available,
                "reserved", reserved,
                "onHand", available + reserved);
        return ResponseEntity.ok(ApiResponse.ok(body, correlationId));
    }

    @PostMapping("/{reservationId}/release")
    public ResponseEntity<ApiResponse<ReservationDto>> release(@PathVariable UUID reservationId) {
        String correlationId = TrustContextHolder.require().correlationId().toString();
        StockReservationEntity reservation = reservationService.release(reservationId);
        return ResponseEntity.ok(ApiResponse.ok(ReservationDto.from(reservation), correlationId));
    }

    @PostMapping("/{reservationId}/consume")
    public ResponseEntity<ApiResponse<ReservationDto>> consume(@PathVariable UUID reservationId) {
        String correlationId = TrustContextHolder.require().correlationId().toString();
        StockReservationEntity reservation = reservationService.consume(reservationId);
        return ResponseEntity.ok(ApiResponse.ok(ReservationDto.from(reservation), correlationId));
    }
}
