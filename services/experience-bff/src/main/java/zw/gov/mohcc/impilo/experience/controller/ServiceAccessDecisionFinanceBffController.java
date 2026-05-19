package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.CostaServiceClient;
import zw.gov.mohcc.impilo.experience.finance.FinancePlaneAuthorizationService;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * BFF proxy for COSTA {@code /costa/v1/service-access-decisions} (pre-service access / payment gate).
 */
@RestController
@RequestMapping("/internal/v1/finance/service-access-decisions")
public class ServiceAccessDecisionFinanceBffController {

    private static final Logger log = LoggerFactory.getLogger(ServiceAccessDecisionFinanceBffController.class);

    private final CostaServiceClient costaClient;
    private final FinancePlaneAuthorizationService financePlaneAuthorizationService;

    public ServiceAccessDecisionFinanceBffController(CostaServiceClient costaClient,
                                                     FinancePlaneAuthorizationService financePlaneAuthorizationService) {
        this.costaClient = costaClient;
        this.financePlaneAuthorizationService = financePlaneAuthorizationService;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> create(
            @RequestBody Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        financePlaneAuthorizationService.assertServiceAccessDecisionAccess("POST");
        try {
            JsonNode data = costaClient.postServiceAccessDecision(body);
            return ResponseEntity.ok(wrap(data, correlationId));
        } catch (Exception e) {
            log.error("COSTA service access decision create failed: {}", e.getMessage());
            return failClose("COSTA_UNAVAILABLE", "Unable to create service access decision", requestId, correlationId);
        }
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> list(
            @RequestParam(value = "encounter_id", required = false) String encounterId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        financePlaneAuthorizationService.assertServiceAccessDecisionAccess("GET");
        try {
            JsonNode data = costaClient.getServiceAccessDecisions(encounterId);
            return ResponseEntity.ok(wrap(data, correlationId));
        } catch (Exception e) {
            log.error("COSTA service access decision list failed: {}", e.getMessage());
            return failClose("COSTA_UNAVAILABLE", "Unable to list service access decisions", requestId, correlationId);
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> get(
            @PathVariable("id") String id,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        financePlaneAuthorizationService.assertServiceAccessDecisionAccess("GET");
        try {
            JsonNode data = costaClient.getServiceAccessDecision(id);
            return ResponseEntity.ok(wrap(data, correlationId));
        } catch (Exception e) {
            log.error("COSTA service access decision get failed: {}", e.getMessage());
            return failClose("COSTA_UNAVAILABLE", "Unable to fetch service access decision", requestId, correlationId);
        }
    }

    private static Map<String, Object> wrap(JsonNode data, String correlationId) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("data", data);
        out.put("meta", Map.of("correlation_id", correlationId));
        return out;
    }

    private static ResponseEntity<Map<String, Object>> failClose(String code, String message, String requestId, String correlationId) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("data", null);
        out.put("error", Map.of("code", code, "message", message));
        out.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(out);
    }
}
