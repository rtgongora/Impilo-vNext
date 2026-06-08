package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.NdrQueryServiceClient;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * BFF proxy for ndr-service bronze/gold query and gold build surfaces.
 */
@RestController
@RequestMapping("/internal/v1/ndr")
public class NdrController {

    private static final Logger log = LoggerFactory.getLogger(NdrController.class);

    private final NdrQueryServiceClient ndrClient;

    public NdrController(NdrQueryServiceClient ndrClient) {
        this.ndrClient = ndrClient;
    }

    @GetMapping("/query/bronze")
    public ResponseEntity<Map<String, Object>> queryBronze(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(value = "event_type", required = false) String eventType,
            @RequestParam(value = "subject_id", required = false) String subjectId,
            @RequestParam(value = "cursor", defaultValue = "0") int cursor,
            @RequestParam(value = "limit", defaultValue = "25") int limit) {
        try {
            JsonNode data = ndrClient.queryBronze(eventType, subjectId, cursor, limit);
            return ResponseEntity.ok(wrap(data, requestId, correlationId));
        } catch (Exception e) {
            log.warn("NDR bronze query failed: {}", e.getMessage());
            return upstreamFailure("NDR_UNAVAILABLE", e.getMessage(), requestId, correlationId);
        }
    }

    @GetMapping("/query/gold/encounters")
    public ResponseEntity<Map<String, Object>> queryGoldEncounters(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(value = "patient_id", required = false) String patientId,
            @RequestParam(value = "cursor", defaultValue = "0") int cursor,
            @RequestParam(value = "limit", defaultValue = "25") int limit) {
        try {
            JsonNode data = ndrClient.queryGoldEncounters(patientId, cursor, limit);
            return ResponseEntity.ok(wrap(data, requestId, correlationId));
        } catch (Exception e) {
            log.warn("NDR gold encounters query failed: {}", e.getMessage());
            return upstreamFailure("NDR_UNAVAILABLE", e.getMessage(), requestId, correlationId);
        }
    }

    @PostMapping("/build/gold/encounters")
    public ResponseEntity<Map<String, Object>> buildGoldEncounters(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            JsonNode data = ndrClient.buildGoldEncounters();
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(wrap(data, requestId, correlationId));
        } catch (Exception e) {
            log.warn("NDR gold build failed: {}", e.getMessage());
            return upstreamFailure("NDR_UNAVAILABLE", e.getMessage(), requestId, correlationId);
        }
    }

    private static Map<String, Object> wrap(JsonNode data, String requestId, String correlationId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("data", data != null ? data : Map.of());
        body.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
        return body;
    }

    private static ResponseEntity<Map<String, Object>> upstreamFailure(
            String code, String message, String requestId, String correlationId) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                "data", Map.of(),
                "error", Map.of("code", code, "message", message),
                "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
    }
}
