package zw.gov.mohcc.impilo.inventory.api.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.inventory.api.dto.OnHandDto;
import zw.gov.mohcc.impilo.inventory.core.OnHandService;
import zw.gov.mohcc.impilo.inventory.persistence.entity.OnHandEntity;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;
import zw.gov.mohcc.impilo.shared.response.PagedResponse;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * REST controller for querying on-hand stock positions.
 *
 * <p>Provides paginated views of current stock levels, near-expiry alerts,
 * and stockout detection. All data is derived from the materialised on-hand
 * projection maintained by the ledger engine.</p>
 */
@RestController
@RequestMapping("/v1/onhand")
public class OnHandController {

    private static final Logger log = LoggerFactory.getLogger(OnHandController.class);

    private final OnHandService onHandService;

    public OnHandController(OnHandService onHandService) {
        this.onHandService = onHandService;
    }

    /**
     * Query on-hand positions with optional filters (paginated).
     */
    @GetMapping
    public ResponseEntity<ApiResponse<PagedResponse<OnHandDto>>> getOnHand(
            @RequestParam UUID facilityId,
            @RequestParam(required = false) UUID storeId,
            @RequestParam(required = false) UUID binId,
            @RequestParam(required = false) String itemCode,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        String correlationId = TrustContextHolder.require().correlationId().toString();

        Page<OnHandEntity> result = onHandService.getOnHand(
                facilityId, storeId, binId, itemCode, PageRequest.of(page, size));

        List<OnHandDto> items = result.getContent().stream()
                .map(OnHandDto::from)
                .collect(Collectors.toList());

        PagedResponse<OnHandDto> pagedResponse = PagedResponse.of(
                items, page, size, result.getTotalElements());

        return ResponseEntity.ok(ApiResponse.ok(pagedResponse, correlationId));
    }

    /**
     * Find stock items expiring within the given number of days.
     */
    @GetMapping("/near-expiry")
    public ResponseEntity<ApiResponse<List<OnHandDto>>> getNearExpiry(
            @RequestParam UUID facilityId,
            @RequestParam(defaultValue = "30") int days) {
        String correlationId = TrustContextHolder.require().correlationId().toString();

        List<OnHandDto> items = onHandService.getNearExpiry(facilityId, days).stream()
                .map(OnHandDto::from)
                .collect(Collectors.toList());

        log.info("Near-expiry query: facilityId={}, days={}, found={}", facilityId, days, items.size());

        return ResponseEntity.ok(ApiResponse.ok(items, correlationId));
    }

    /**
     * Find items that have reached zero stock (stockouts).
     */
    @GetMapping("/stockouts")
    public ResponseEntity<ApiResponse<List<OnHandDto>>> getStockouts(
            @RequestParam UUID facilityId) {
        String correlationId = TrustContextHolder.require().correlationId().toString();

        List<OnHandDto> items = onHandService.getStockouts(facilityId).stream()
                .map(OnHandDto::from)
                .collect(Collectors.toList());

        log.info("Stockout query: facilityId={}, found={}", facilityId, items.size());

        return ResponseEntity.ok(ApiResponse.ok(items, correlationId));
    }
}
