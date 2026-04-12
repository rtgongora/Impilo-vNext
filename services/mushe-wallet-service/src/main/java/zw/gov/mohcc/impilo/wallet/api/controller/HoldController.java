package zw.gov.mohcc.impilo.wallet.api.controller;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;
import zw.gov.mohcc.impilo.wallet.api.dto.PlaceHoldRequest;
import zw.gov.mohcc.impilo.wallet.core.HoldService;
import zw.gov.mohcc.impilo.wallet.persistence.entity.HoldEntity;

import java.util.UUID;

/**
 * Hold lifecycle endpoints: place, capture, release.
 */
@RestController
@RequestMapping("/internal/v1/holds")
public class HoldController {

    private final HoldService holdService;

    public HoldController(HoldService holdService) {
        this.holdService = holdService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<HoldEntity>> placeHold(
            @RequestHeader(value = CompanionHeaders.CORRELATION_ID, required = false) String correlationId,
            @Valid @RequestBody PlaceHoldRequest request) {

        HoldEntity hold = holdService.placeHold(
                request.walletId(),
                request.amount(),
                request.reason(),
                request.reference(),
                request.expiresAt());

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(hold, correlationId));
    }

    @PostMapping("/{holdId}/capture")
    public ResponseEntity<ApiResponse<HoldEntity>> captureHold(
            @RequestHeader(value = CompanionHeaders.CORRELATION_ID, required = false) String correlationId,
            @PathVariable UUID holdId) {

        HoldEntity hold = holdService.captureHold(holdId);

        return ResponseEntity.ok(ApiResponse.ok(hold, correlationId));
    }

    @PostMapping("/{holdId}/release")
    public ResponseEntity<ApiResponse<HoldEntity>> releaseHold(
            @RequestHeader(value = CompanionHeaders.CORRELATION_ID, required = false) String correlationId,
            @PathVariable UUID holdId) {

        HoldEntity hold = holdService.releaseHold(holdId);

        return ResponseEntity.ok(ApiResponse.ok(hold, correlationId));
    }
}
