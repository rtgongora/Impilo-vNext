package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.PctServiceClient;

import java.util.Map;

/**
 * Maternity summary — pure proxy to PCT. All aggregation and clinical logic lives in pct-service.
 */
@RestController
@RequestMapping("/internal/v1/maternity")
public class MaternitySummaryController {

    private static final Logger log = LoggerFactory.getLogger(MaternitySummaryController.class);

    private final PctServiceClient pctClient;

    public MaternitySummaryController(PctServiceClient pctClient) {
        this.pctClient = pctClient;
    }

    /**
     * GET /internal/v1/maternity/summary — delegated to PCT {@code /v1/maternity/summary}.
     */
    @GetMapping("/summary")
    public ResponseEntity<Map<String, Object>> getSummary(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam String patientId,
            @RequestParam(required = false) String encounterId) {
        try {
            JsonNode data = pctClient.getMaternitySummary(patientId, encounterId);
            return ResponseEntity.ok(Map.of(
                    "data", data != null ? data : Map.of(),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            // An empty 200 here reads as "there is no maternity record for this woman", which is
            // an affirmative finding and would be a fabricated one. Fail loudly instead.
            log.error("PCT getMaternitySummary failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                    "error", "maternity_summary_unavailable",
                    "message", "The maternity summary could not be retrieved. Do not treat this "
                               + "as an absence of a maternity record.",
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        }
    }
}
