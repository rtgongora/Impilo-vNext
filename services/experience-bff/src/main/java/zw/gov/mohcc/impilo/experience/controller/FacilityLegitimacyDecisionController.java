package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpStatusCodeException;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.TusoServiceClient;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The Ministry legitimacy verdict, reachable from the experience layer (FCV-W1).
 *
 * <p>Deliberately a thin passthrough. Every guard that matters — that the caller holds an ACTIVE
 * Ministry verifier appointment whose jurisdiction covers this facility, that a reason is given,
 * that the projection and the audit write happen in one transaction — lives in tuso, next to the
 * data. Re-implementing any of it here would create a second, weaker way to decide, which is the
 * shape of the defect this programme has been closing.</p>
 *
 * <p>A 4xx from tuso is passed through with its code and message rather than collapsed into a
 * generic failure: "you have no appointment covering this facility" and "the registry is down" are
 * different facts, and the verifier needs to know which one they are looking at.</p>
 */
@RestController
@RequestMapping("/api/v1/facility-legitimacy")
public class FacilityLegitimacyDecisionController {

    private static final Logger log = LoggerFactory.getLogger(FacilityLegitimacyDecisionController.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final TusoServiceClient tusoClient;

    public FacilityLegitimacyDecisionController(TusoServiceClient tusoClient) {
        this.tusoClient = tusoClient;
    }

    @PostMapping("/{facilityUuid}/decisions")
    public ResponseEntity<Map<String, Object>> issue(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @PathVariable String facilityUuid,
            @RequestBody Map<String, Object> body) {
        return relay(() -> tusoClient.issueLegitimacyDecision(facilityUuid, body),
                HttpStatus.CREATED, requestId, correlationId,
                "The legitimacy decision could not be recorded.");
    }

    @GetMapping("/{facilityUuid}/decisions")
    public ResponseEntity<Map<String, Object>> history(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @PathVariable String facilityUuid) {
        return relay(() -> tusoClient.legitimacyDecisionHistory(facilityUuid),
                HttpStatus.OK, requestId, correlationId,
                "The legitimacy decision history could not be loaded.");
    }

    private interface Upstream {
        JsonNode call();
    }

    private ResponseEntity<Map<String, Object>> relay(Upstream upstream, HttpStatus okStatus,
                                                      String requestId, String correlationId,
                                                      String failMessage) {
        try {
            JsonNode data = upstream.call();
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("data", data == null ? Map.of() : data);
            response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
            return ResponseEntity.status(okStatus).body(response);
        } catch (Exception e) {
            HttpStatusCodeException upstreamError = asUpstreamStatus(e);
            if (upstreamError != null && upstreamError.getStatusCode().is4xxClientError()) {
                return passthrough(upstreamError, requestId, correlationId, failMessage);
            }
            log.warn("Legitimacy decision upstream failed: {}", e.getMessage());
            return error(HttpStatus.BAD_GATEWAY, "TUSO_UNAVAILABLE", failMessage, requestId, correlationId);
        }
    }

    private HttpStatusCodeException asUpstreamStatus(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof HttpStatusCodeException hse) {
                return hse;
            }
        }
        return null;
    }

    private ResponseEntity<Map<String, Object>> passthrough(HttpStatusCodeException e, String requestId,
                                                            String correlationId, String fallbackMessage) {
        String code = "DECISION_REFUSED";
        String message = fallbackMessage;
        try {
            JsonNode body = MAPPER.readTree(e.getResponseBodyAsString());
            if (!body.path("error").path("message").isMissingNode()) {
                message = body.path("error").path("message").asText(message);
                code = body.path("error").path("code").asText(code);
            } else if (!body.path("message").isMissingNode()) {
                message = body.path("message").asText(message);
            }
        } catch (Exception ignored) {
            // No parseable body — the status alone still says refusal, not outage.
        }
        return error(HttpStatus.valueOf(e.getStatusCode().value()), code, message, requestId, correlationId);
    }

    private ResponseEntity<Map<String, Object>> error(HttpStatus status, String code, String message,
                                                      String requestId, String correlationId) {
        return ResponseEntity.status(status).body(Map.of(
                "error", Map.of("code", code, "message", message),
                "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
    }
}
