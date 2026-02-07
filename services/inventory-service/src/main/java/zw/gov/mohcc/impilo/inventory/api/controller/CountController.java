package zw.gov.mohcc.impilo.inventory.api.controller;

import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.inventory.api.dto.CountLineDto;
import zw.gov.mohcc.impilo.inventory.api.dto.CountSessionDto;
import zw.gov.mohcc.impilo.inventory.api.dto.CreateCountRequest;
import zw.gov.mohcc.impilo.inventory.api.dto.UpdateCountLineRequest;
import zw.gov.mohcc.impilo.inventory.core.StockCountService;
import zw.gov.mohcc.impilo.inventory.persistence.entity.CountLineEntity;
import zw.gov.mohcc.impilo.inventory.persistence.entity.CountSessionEntity;
import zw.gov.mohcc.impilo.inventory.persistence.repository.CountLineRepository;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * REST controller for physical stock count session management.
 *
 * <p>Manages the full stock count lifecycle: create session, start counting,
 * update individual lines, submit for review, approve (posting variance
 * adjustments), or reject (requiring re-count).</p>
 */
@RestController
@RequestMapping("/v1/counts")
public class CountController {

    private static final Logger log = LoggerFactory.getLogger(CountController.class);

    private final StockCountService stockCountService;
    private final CountLineRepository countLineRepository;

    public CountController(StockCountService stockCountService,
                           CountLineRepository countLineRepository) {
        this.stockCountService = stockCountService;
        this.countLineRepository = countLineRepository;
    }

    /**
     * Create a new count session for a store/bin.
     */
    @PostMapping
    public ResponseEntity<ApiResponse<CountSessionDto>> createSession(
            @Valid @RequestBody CreateCountRequest request) {
        String correlationId = TrustContextHolder.require().correlationId().toString();

        CountSessionEntity session = stockCountService.createSession(request.storeId(), request.binId());
        List<CountLineDto> lines = loadLines(session.getSessionId());

        log.info("Count session created: sessionId={}, storeId={}", session.getSessionId(), request.storeId());

        return ResponseEntity.ok(ApiResponse.ok(CountSessionDto.from(session, lines), correlationId));
    }

    /**
     * Start counting (transition DRAFT to IN_PROGRESS).
     */
    @PostMapping("/{id}/start")
    public ResponseEntity<ApiResponse<CountSessionDto>> startCounting(@PathVariable UUID id) {
        String correlationId = TrustContextHolder.require().correlationId().toString();

        CountSessionEntity session = stockCountService.startCounting(id);
        List<CountLineDto> lines = loadLines(id);

        log.info("Count session started: sessionId={}", id);

        return ResponseEntity.ok(ApiResponse.ok(CountSessionDto.from(session, lines), correlationId));
    }

    /**
     * Update a count line with the physically counted quantity.
     */
    @PostMapping("/{id}/lines/{lineId}")
    public ResponseEntity<ApiResponse<CountLineDto>> updateLine(
            @PathVariable UUID id,
            @PathVariable UUID lineId,
            @Valid @RequestBody UpdateCountLineRequest request) {
        String correlationId = TrustContextHolder.require().correlationId().toString();

        CountLineEntity line = stockCountService.updateLine(
                id, lineId, request.qtyCounted(), request.barcode());

        log.info("Count line updated: sessionId={}, lineId={}, qtyCounted={}",
                id, lineId, request.qtyCounted());

        return ResponseEntity.ok(ApiResponse.ok(CountLineDto.from(line), correlationId));
    }

    /**
     * Submit the count for supervisory review.
     */
    @PostMapping("/{id}/submit")
    public ResponseEntity<ApiResponse<CountSessionDto>> submitCount(@PathVariable UUID id) {
        String correlationId = TrustContextHolder.require().correlationId().toString();

        CountSessionEntity session = stockCountService.submitCount(id);
        List<CountLineDto> lines = loadLines(id);

        log.info("Count session submitted: sessionId={}", id);

        return ResponseEntity.ok(ApiResponse.ok(CountSessionDto.from(session, lines), correlationId));
    }

    /**
     * Approve the count, posting variance adjustments to the ledger.
     */
    @PostMapping("/{id}/approve")
    public ResponseEntity<ApiResponse<CountSessionDto>> approveCount(
            @PathVariable UUID id,
            @RequestBody(required = false) Map<String, String> body) {
        String correlationId = TrustContextHolder.require().correlationId().toString();
        String notes = (body != null) ? body.getOrDefault("notes", "") : "";

        CountSessionEntity session = stockCountService.approveCount(id, notes);
        List<CountLineDto> lines = loadLines(id);

        log.info("Count session approved: sessionId={}", id);

        return ResponseEntity.ok(ApiResponse.ok(CountSessionDto.from(session, lines), correlationId));
    }

    /**
     * Reject the count, requiring a re-count.
     */
    @PostMapping("/{id}/reject")
    public ResponseEntity<ApiResponse<CountSessionDto>> rejectCount(
            @PathVariable UUID id,
            @RequestBody(required = false) Map<String, String> body) {
        String correlationId = TrustContextHolder.require().correlationId().toString();
        String notes = (body != null) ? body.getOrDefault("notes", "") : "";

        CountSessionEntity session = stockCountService.rejectCount(id, notes);
        List<CountLineDto> lines = loadLines(id);

        log.info("Count session rejected: sessionId={}", id);

        return ResponseEntity.ok(ApiResponse.ok(CountSessionDto.from(session, lines), correlationId));
    }

    /**
     * Get a count session with all its lines.
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<CountSessionDto>> getSession(@PathVariable UUID id) {
        String correlationId = TrustContextHolder.require().correlationId().toString();

        CountSessionEntity session = stockCountService.getSession(id);
        List<CountLineDto> lines = loadLines(id);

        return ResponseEntity.ok(ApiResponse.ok(CountSessionDto.from(session, lines), correlationId));
    }

    /**
     * Load count lines for a session from the repository and map to DTOs.
     */
    private List<CountLineDto> loadLines(UUID sessionId) {
        List<CountLineEntity> lines = countLineRepository.findBySessionId(sessionId);
        if (lines == null || lines.isEmpty()) {
            return Collections.emptyList();
        }
        return lines.stream()
                .map(CountLineDto::from)
                .collect(Collectors.toList());
    }
}
