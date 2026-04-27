package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        financePlaneAuthorizationService.assertServiceAccessDecisionAccess("POST");
        try {
            JsonNode data = costaClient.postServiceAccessDecision(body);
            return ResponseEntity.ok(wrap(data, correlationId));
        } catch (Exception e) {
            log.error("COSTA service access decision create failed: {}", e.getMessage());
            return ResponseEntity.badRequest().body(error(e.getMessage(), correlationId));
        }
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> list(
            @RequestParam(value = "encounter_id", required = false) String encounterId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        financePlaneAuthorizationService.assertServiceAccessDecisionAccess("GET");
        try {
            JsonNode data = costaClient.getServiceAccessDecisions(encounterId);
            return ResponseEntity.ok(wrap(data, correlationId));
        } catch (Exception e) {
            log.error("COSTA service access decision list failed: {}", e.getMessage());
            return ResponseEntity.internalServerError().body(error(e.getMessage(), correlationId));
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> get(
            @PathVariable("id") String id,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        financePlaneAuthorizationService.assertServiceAccessDecisionAccess("GET");
        try {
            JsonNode data = costaClient.getServiceAccessDecision(id);
            return ResponseEntity.ok(wrap(data, correlationId));
        } catch (Exception e) {
            log.error("COSTA service access decision get failed: {}", e.getMessage());
            return ResponseEntity.internalServerError().body(error(e.getMessage(), correlationId));
        }
    }

    private static Map<String, Object> wrap(JsonNode data, String correlationId) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("data", data);
        out.put("meta", Map.of("correlation_id", correlationId));
        return out;
    }

    private static Map<String, Object> error(String message, String correlationId) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("data", null);
        out.put("error", Map.of("message", message, "correlation_id", correlationId));
        return out;
    }
}
