package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.PctServiceClient;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Experience-BFF facade for the PCT sorting desk (R7/G12) — the pre-triage step that assigns a
 * journey a visit type (context + visit type + arrival reason) between ARRIVED and TRIAGE/QUEUE, so
 * the Cadre Engine can route it. Composition/orchestration only; PCT owns the sort record. The visit-
 * type catalogue carries no PHI. Trust headers are forwarded by the RestTemplate interceptor.
 */
@RestController
@RequestMapping("/internal/v1/sorting-desk")
public class SortingDeskController {

    private static final Logger log = LoggerFactory.getLogger(SortingDeskController.class);

    private final PctServiceClient pct;

    public SortingDeskController(PctServiceClient pct) {
        this.pct = pct;
    }

    @GetMapping("/visit-types")
    public ResponseEntity<Map<String, Object>> visitTypes(
            @RequestParam(required = false) String context,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            return ok(pct.getSortingVisitTypes(context), requestId, correlationId, HttpStatus.OK);
        } catch (Exception e) {
            return fail(requestId, correlationId, e.getMessage());
        }
    }

    @PostMapping("/journeys/{journeyId}/sort")
    public ResponseEntity<Map<String, Object>> sort(
            @PathVariable String journeyId,
            @RequestBody Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            return ok(pct.sortJourney(journeyId, body), requestId, correlationId, HttpStatus.CREATED);
        } catch (org.springframework.web.client.HttpStatusCodeException e) {
            log.warn("Sorting-desk sort {} rejected: {}", journeyId, e.getResponseBodyAsString());
            return ResponseEntity.status(e.getStatusCode()).body(Map.of(
                    "error", Map.of("code", "SORT_REJECTED", "message", e.getResponseBodyAsString()),
                    "meta", meta(requestId, correlationId)));
        } catch (Exception e) {
            return fail(requestId, correlationId, e.getMessage());
        }
    }

    @GetMapping("/journeys/{journeyId}")
    public ResponseEntity<Map<String, Object>> latest(
            @PathVariable String journeyId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            return ok(pct.getLatestSort(journeyId), requestId, correlationId, HttpStatus.OK);
        } catch (Exception e) {
            return fail(requestId, correlationId, e.getMessage());
        }
    }

    private static ResponseEntity<Map<String, Object>> ok(JsonNode data, String requestId, String correlationId, HttpStatus status) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("data", data != null ? data : List.of());
        out.put("meta", meta(requestId, correlationId));
        return ResponseEntity.status(status).body(out);
    }

    private static Map<String, Object> meta(String requestId, String correlationId) {
        return Map.of("request_id", requestId, "correlation_id", correlationId);
    }

    private static ResponseEntity<Map<String, Object>> fail(String requestId, String correlationId, String message) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                "error", Map.of("code", "PCT_UNAVAILABLE", "message", message != null ? message : "sorting desk unavailable"),
                "meta", meta(requestId, correlationId)));
    }
}
