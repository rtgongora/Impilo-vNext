package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.FhirGatewayServiceClient;

import java.util.Map;

/**
 * FHIR Interoperability BFF Controller (Health OS §10: Interoperability).
 *
 * <p>Proxies FHIR R4 operations through the governed fhir-gateway-service,
 * which handles routing, validation, audit, and standards enforcement.</p>
 */
@RestController
@RequestMapping("/internal/v1/fhir")
public class FhirInteropController {

    private static final Logger log = LoggerFactory.getLogger(FhirInteropController.class);

    private final FhirGatewayServiceClient fhirClient;

    public FhirInteropController(FhirGatewayServiceClient fhirClient) {
        this.fhirClient = fhirClient;
    }

    @GetMapping("/metadata")
    public ResponseEntity<Map<String, Object>> getCapabilityStatement(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            JsonNode result = fhirClient.getCapabilityStatement();
            return ResponseEntity.ok(Map.of(
                    "data", result != null ? result : Map.of(),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            log.error("FHIR metadata fetch failed: {}", e.getMessage());
            return upstreamFailure("FHIR_GATEWAY_UNAVAILABLE", e.getMessage(), requestId, correlationId);
        }
    }

    @GetMapping("/{resourceType}")
    public ResponseEntity<Map<String, Object>> searchFhirResource(
            @PathVariable String resourceType,
            @RequestParam(defaultValue = "") String params,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            JsonNode result = fhirClient.searchResource(resourceType, params);
            return ResponseEntity.ok(Map.of(
                    "data", result != null ? result : Map.of(),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            log.error("FHIR search {}: {}", resourceType, e.getMessage());
            return upstreamFailure("FHIR_GATEWAY_UNAVAILABLE", e.getMessage(), requestId, correlationId);
        }
    }

    @GetMapping("/{resourceType}/{id}")
    public ResponseEntity<Map<String, Object>> getFhirResource(
            @PathVariable String resourceType,
            @PathVariable String id,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            JsonNode result = fhirClient.getResource(resourceType, id);
            if (result == null || result.isNull() || (result.isObject() && result.isEmpty())) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                        "error", Map.of("code", "FHIR_RESOURCE_NOT_FOUND", "message", "FHIR resource not found"),
                        "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
            }
            return ResponseEntity.ok(Map.of(
                    "data", result,
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            log.error("FHIR read {}/{}: {}", resourceType, id, e.getMessage());
            return upstreamFailure("FHIR_GATEWAY_UNAVAILABLE", e.getMessage(), requestId, correlationId);
        }
    }

    private ResponseEntity<Map<String, Object>> upstreamFailure(
            String code, String message, String requestId, String correlationId) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                "error", Map.of("code", code, "message", message != null ? message : "FHIR gateway unavailable"),
                "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
    }
}
