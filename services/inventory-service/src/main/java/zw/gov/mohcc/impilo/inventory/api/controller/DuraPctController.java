package zw.gov.mohcc.impilo.inventory.api.controller;

import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.inventory.api.dto.PctConsumeRequest;
import zw.gov.mohcc.impilo.inventory.api.dto.PctConsumeResponse;
import zw.gov.mohcc.impilo.inventory.api.dto.PctReserveRequest;
import zw.gov.mohcc.impilo.inventory.api.dto.ReservationDto;
import zw.gov.mohcc.impilo.inventory.core.ConsumptionPostingService;
import zw.gov.mohcc.impilo.inventory.core.ReservationService;
import zw.gov.mohcc.impilo.inventory.core.StockAvailabilityService;
import zw.gov.mohcc.impilo.inventory.persistence.entity.LedgerEventEntity;
import zw.gov.mohcc.impilo.inventory.persistence.entity.StockReservationEntity;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;

import java.util.UUID;

/**
 * Dura ↔ PCT integration API ({@code /v1/dura/pct/*}).
 *
 * <p>The contract PCT calls for stock-dependent clinical actions: check
 * availability (with usable batches and approved substitutes when stocked-out),
 * reserve stock for an encounter, and record consumption — which posts a Dura
 * ledger event and optionally fulfils the reservation.</p>
 */
@RestController
@RequestMapping("/v1/dura/pct")
public class DuraPctController {

    private static final Logger log = LoggerFactory.getLogger(DuraPctController.class);

    private final StockAvailabilityService availabilityService;
    private final ReservationService reservationService;
    private final ConsumptionPostingService consumptionPostingService;

    public DuraPctController(StockAvailabilityService availabilityService,
                             ReservationService reservationService,
                             ConsumptionPostingService consumptionPostingService) {
        this.availabilityService = availabilityService;
        this.reservationService = reservationService;
        this.consumptionPostingService = consumptionPostingService;
    }

    @GetMapping("/availability")
    public ResponseEntity<ApiResponse<StockAvailabilityService.AvailabilityResult>> availability(
            @RequestParam("facilityId") UUID facilityId,
            @RequestParam("storeId") UUID storeId,
            @RequestParam("itemCode") String itemCode) {
        String correlationId = TrustContextHolder.require().correlationId().toString();
        StockAvailabilityService.AvailabilityResult result =
                availabilityService.availability(facilityId, storeId, itemCode);
        return ResponseEntity.ok(ApiResponse.ok(result, correlationId));
    }

    @PostMapping("/reserve")
    public ResponseEntity<ApiResponse<ReservationDto>> reserve(@Valid @RequestBody PctReserveRequest request) {
        String correlationId = TrustContextHolder.require().correlationId().toString();
        StockReservationEntity reservation = reservationService.reserve(
                request.facilityId(), request.storeId(), request.itemCode(), request.batchLotId(),
                request.qty(), request.uom(), request.resolvedRefType(), request.encounterId(),
                request.reason(), null);
        log.info("PCT reservation: encounterId={}, itemCode={}, qty={}",
                request.encounterId(), request.itemCode(), request.qty());
        return ResponseEntity.ok(ApiResponse.ok(ReservationDto.from(reservation), correlationId));
    }

    @PostMapping("/consume")
    public ResponseEntity<ApiResponse<PctConsumeResponse>> consume(@Valid @RequestBody PctConsumeRequest request) {
        String correlationId = TrustContextHolder.require().correlationId().toString();

        LedgerEventEntity event = consumptionPostingService.postClinicalConsumption(
                request.facilityId(), request.storeId(), request.itemCode(),
                request.qty(), request.refType(), request.refId());

        boolean reservationConsumed = false;
        if (request.reservationId() != null) {
            reservationService.consume(request.reservationId());
            reservationConsumed = true;
        }

        log.info("PCT consumption: itemCode={}, qty={}, refType={}, refId={}, ledgerEventId={}",
                request.itemCode(), request.qty(), request.refType(), request.refId(), event.getEventId());
        return ResponseEntity.ok(ApiResponse.ok(
                new PctConsumeResponse(event.getEventId(), reservationConsumed), correlationId));
    }
}
