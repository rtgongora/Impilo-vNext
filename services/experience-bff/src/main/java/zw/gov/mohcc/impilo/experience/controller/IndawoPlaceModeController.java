package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.IndawoServiceClient;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Indawo public-health PLACE mode composer (stateless). Surfaces the Indawo
 * surveillance / outbreak / case-investigation / field-team SoR for the place-mode
 * cockpit. Persists nothing. Authz via existing ext_authz; policies
 * {@code INDAWO-MODE}, {@code INSPECTION} (spec-only, track P).
 */
@RestController
@RequestMapping("/internal/v1/place-mode")
public class IndawoPlaceModeController {

    private static final Logger log = LoggerFactory.getLogger(IndawoPlaceModeController.class);

    private final IndawoServiceClient indawoClient;

    public IndawoPlaceModeController(IndawoServiceClient indawoClient) {
        this.indawoClient = indawoClient;
    }

    @GetMapping("/summary")
    public ResponseEntity<Map<String, Object>> summary(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return wrap(() -> indawoClient.placeModeSummary(), requestId, correlationId, "PLACE_MODE_SUMMARY_UNAVAILABLE");
    }

    @GetMapping("/alerts")
    public ResponseEntity<Map<String, Object>> alerts(
            @RequestParam(required = false) String status,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return wrap(() -> indawoClient.listSurveillance("alerts", "status", status),
                requestId, correlationId, "ALERTS_UNAVAILABLE");
    }

    @PostMapping("/alerts")
    public ResponseEntity<Map<String, Object>> createAlert(
            @RequestBody Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return wrapCreated(() -> indawoClient.postSurveillance("alerts", body),
                requestId, correlationId, "ALERT_CREATE_FAILED");
    }

    @GetMapping("/outbreaks")
    public ResponseEntity<Map<String, Object>> outbreaks(
            @RequestParam(required = false) String status,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return wrap(() -> indawoClient.listSurveillance("outbreaks", "status", status),
                requestId, correlationId, "OUTBREAKS_UNAVAILABLE");
    }

    @PostMapping("/outbreaks")
    public ResponseEntity<Map<String, Object>> createOutbreak(
            @RequestBody Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return wrapCreated(() -> indawoClient.postSurveillance("outbreaks", body),
                requestId, correlationId, "OUTBREAK_CREATE_FAILED");
    }

    @GetMapping("/cases")
    public ResponseEntity<Map<String, Object>> cases(
            @RequestParam(required = false) String outbreakId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return wrap(() -> indawoClient.listSurveillance("cases", "outbreakId", outbreakId),
                requestId, correlationId, "CASES_UNAVAILABLE");
    }

    @PostMapping("/cases")
    public ResponseEntity<Map<String, Object>> createCase(
            @RequestBody Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return wrapCreated(() -> indawoClient.postSurveillance("cases", body),
                requestId, correlationId, "CASE_CREATE_FAILED");
    }

    @GetMapping("/field-teams")
    public ResponseEntity<Map<String, Object>> teams(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return wrap(() -> indawoClient.listSurveillance("field-teams", null, null),
                requestId, correlationId, "TEAMS_UNAVAILABLE");
    }

    @PostMapping("/field-teams")
    public ResponseEntity<Map<String, Object>> createTeam(
            @RequestBody Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return wrapCreated(() -> indawoClient.postSurveillance("field-teams", body),
                requestId, correlationId, "TEAM_CREATE_FAILED");
    }

    @PostMapping("/field-teams/{teamId}/deploy")
    public ResponseEntity<Map<String, Object>> deploy(
            @PathVariable String teamId,
            @RequestBody Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return wrapCreated(() -> indawoClient.postSurveillance("field-teams/" + teamId + "/deploy", body),
                requestId, correlationId, "DEPLOY_FAILED");
    }

    @PostMapping("/deployments/{deploymentId}/recall")
    public ResponseEntity<Map<String, Object>> recall(
            @PathVariable String deploymentId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return wrap(() -> indawoClient.postSurveillance("deployments/" + deploymentId + "/recall", Map.of()),
                requestId, correlationId, "RECALL_FAILED");
    }

    // ── helpers ──

    private interface Call { JsonNode run(); }

    private ResponseEntity<Map<String, Object>> wrap(Call call, String requestId, String correlationId, String errorCode) {
        try {
            return ResponseEntity.ok(Map.of("data", call.run(), "meta", meta(requestId, correlationId)));
        } catch (Exception e) {
            log.warn("{}: {}", errorCode, e.getMessage());
            return badGateway(requestId, correlationId, errorCode);
        }
    }

    private ResponseEntity<Map<String, Object>> wrapCreated(Call call, String requestId, String correlationId, String errorCode) {
        try {
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(Map.of("data", call.run(), "meta", meta(requestId, correlationId)));
        } catch (Exception e) {
            log.warn("{}: {}", errorCode, e.getMessage());
            return badGateway(requestId, correlationId, errorCode);
        }
    }

    private static Map<String, Object> meta(String requestId, String correlationId) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("request_id", requestId);
        meta.put("correlation_id", correlationId);
        return meta;
    }

    private ResponseEntity<Map<String, Object>> badGateway(String requestId, String correlationId, String code) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                "error", Map.of("code", code, "message", "Indawo place-mode upstream unavailable"),
                "meta", meta(requestId, correlationId)));
    }
}
