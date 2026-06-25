package zw.gov.mohcc.impilo.oros.api.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.oros.api.dto.ObservationDto;
import zw.gov.mohcc.impilo.oros.api.dto.ObservationInput;
import zw.gov.mohcc.impilo.oros.api.dto.ResultSummaryDto;
import zw.gov.mohcc.impilo.oros.core.LabResultService;
import zw.gov.mohcc.impilo.oros.persistence.entity.ResultEntity;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Structured laboratory result entry & retrieval (spec §7). Authoring a result authors a versioned
 * report (preliminary/final) and persists per-analyte observations; per-analyte critical flags
 * raise the result critical workflow. Authorization is enforced at the Envoy/TSHEPO edge.
 */
@RestController
@RequestMapping("/v1")
public class LabResultController {

    private final LabResultService labResultService;

    public LabResultController(LabResultService labResultService) {
        this.labResultService = labResultService;
    }

    public record RecordLabResultRequest(
            boolean isFinal,
            String impression,
            String recommendations,
            String ziboResultCodes,
            List<ObservationInput> observations) {}

    /** Record a structured lab result (preliminary or final) with per-analyte observations. */
    @PostMapping("/orders/{orderId}/lab-results")
    public ResponseEntity<ApiResponse<ResultSummaryDto>> record(
            @PathVariable String orderId,
            @RequestBody RecordLabResultRequest request) {
        String cid = TrustContextHolder.require().correlationId().toString();
        ResultEntity result = labResultService.recordLabResult(
                orderId, request.isFinal(), request.impression(), request.recommendations(),
                request.ziboResultCodes(), request.observations());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(ResultSummaryDto.from(result), cid));
    }

    /** Structured observations for a result. */
    @GetMapping("/results/{resultId}/observations")
    public ResponseEntity<ApiResponse<List<ObservationDto>>> observations(@PathVariable UUID resultId) {
        String cid = TrustContextHolder.require().correlationId().toString();
        List<ObservationDto> dtos = labResultService.observationsForResult(resultId).stream()
                .map(ObservationDto::from).collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.ok(dtos, cid));
    }
}
