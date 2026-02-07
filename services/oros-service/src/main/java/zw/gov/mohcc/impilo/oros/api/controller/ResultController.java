package zw.gov.mohcc.impilo.oros.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.oros.api.dto.PostResultRequest;
import zw.gov.mohcc.impilo.oros.api.dto.ResultSummaryDto;
import zw.gov.mohcc.impilo.oros.core.ResultService;
import zw.gov.mohcc.impilo.oros.persistence.entity.ResultEntity;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * REST controller for order result management.
 *
 * <p>Provides endpoints for posting results, retrieving results,
 * and marking results as critical.</p>
 */
@RestController
@RequestMapping("/v1/orders/{orderId}/results")
public class ResultController {

    private static final Logger log = LoggerFactory.getLogger(ResultController.class);

    private final ResultService resultService;
    private final ObjectMapper objectMapper;

    public ResultController(ResultService resultService, ObjectMapper objectMapper) {
        this.resultService = resultService;
        this.objectMapper = objectMapper;
    }

    /**
     * Post a result for an order.
     */
    @PostMapping
    public ResponseEntity<ApiResponse<ResultSummaryDto>> postResult(
            @PathVariable String orderId,
            @Valid @RequestBody PostResultRequest request) {
        String correlationId = TrustContextHolder.require().correlationId().toString();

        String summaryJson = serializeToJson(request.summary());
        String docIdsJson = serializeToJson(request.docIds());

        ResultEntity result = resultService.postResult(
                orderId,
                request.kind(),
                summaryJson,
                request.ziboResultCodes(),
                docIdsJson,
                request.isCritical());

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(ResultSummaryDto.from(result), correlationId));
    }

    /**
     * Get all results for an order.
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<ResultSummaryDto>>> getResults(
            @PathVariable String orderId) {
        String correlationId = TrustContextHolder.require().correlationId().toString();

        List<ResultSummaryDto> results = resultService.getResults(orderId).stream()
                .map(ResultSummaryDto::from)
                .collect(Collectors.toList());

        return ResponseEntity.ok(ApiResponse.ok(results, correlationId));
    }

    /**
     * Mark a specific result as critical.
     */
    @PostMapping("/{resultId}/mark-critical")
    public ResponseEntity<ApiResponse<ResultSummaryDto>> markCritical(
            @PathVariable String orderId,
            @PathVariable UUID resultId) {
        String correlationId = TrustContextHolder.require().correlationId().toString();

        ResultEntity result = resultService.markCritical(resultId);
        return ResponseEntity.ok(ApiResponse.ok(ResultSummaryDto.from(result), correlationId));
    }

    private String serializeToJson(Object value) {
        if (value == null) return null;
        if (value instanceof String s) return s;
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            log.warn("Failed to serialize value to JSON, using toString()", e);
            return value.toString();
        }
    }
}
