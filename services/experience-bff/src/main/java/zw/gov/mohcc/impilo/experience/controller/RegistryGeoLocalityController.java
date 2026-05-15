package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.TusoServiceClient;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * BFF proxies for Tuso Zimbabwe admin geography and locality gazetteer (registry pickers).
 */
@RestController
@RequestMapping("/internal/v1/registry")
public class RegistryGeoLocalityController {

    private static final Logger log = LoggerFactory.getLogger(RegistryGeoLocalityController.class);

    private final TusoServiceClient tusoClient;

    public RegistryGeoLocalityController(TusoServiceClient tusoClient) {
        this.tusoClient = tusoClient;
    }

    @GetMapping("/geo/zw/districts")
    public ResponseEntity<Map<String, Object>> listDistricts(
            @RequestParam String provinceCode,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            JsonNode data = tusoClient.listZwDistricts(provinceCode);
            return ok(requestId, correlationId, data != null ? data : List.of());
        } catch (Exception e) {
            log.warn("ZW districts proxy failed: {}", e.getMessage());
            return upstreamFailure(requestId, correlationId, "TUSO_UNAVAILABLE", e.getMessage());
        }
    }

    @GetMapping("/geo/zw/wards")
    public ResponseEntity<Map<String, Object>> listWards(
            @RequestParam String districtCode,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            JsonNode data = tusoClient.listZwWards(districtCode);
            return ok(requestId, correlationId, data != null ? data : List.of());
        } catch (Exception e) {
            log.warn("ZW wards proxy failed: {}", e.getMessage());
            return upstreamFailure(requestId, correlationId, "TUSO_UNAVAILABLE", e.getMessage());
        }
    }

    @PostMapping("/geo/zw/bulk")
    public ResponseEntity<Map<String, Object>> bulkUpsertGeo(
            @RequestBody Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            JsonNode data = tusoClient.bulkUpsertZwGeo(body);
            return ok(requestId, correlationId, data != null ? data : Map.of());
        } catch (Exception e) {
            log.warn("ZW geo bulk failed: {}", e.getMessage());
            return upstreamFailure(requestId, correlationId, "TUSO_UNAVAILABLE", e.getMessage());
        }
    }

    @GetMapping("/localities/search")
    public ResponseEntity<Map<String, Object>> searchLocalities(
            @RequestParam String districtCode,
            @RequestParam String q,
            @RequestParam(defaultValue = "20") int limit,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            JsonNode data = tusoClient.searchLocalities(districtCode, q, limit);
            return ok(requestId, correlationId, data != null ? data : List.of());
        } catch (Exception e) {
            log.warn("Locality search proxy failed: {}", e.getMessage());
            return upstreamFailure(requestId, correlationId, "TUSO_UNAVAILABLE", e.getMessage());
        }
    }

    @GetMapping("/localities/proposals/pending")
    public ResponseEntity<Map<String, Object>> pendingProposals(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            JsonNode data = tusoClient.listPendingLocalityProposals();
            return ok(requestId, correlationId, data != null ? data : List.of());
        } catch (Exception e) {
            return upstreamFailure(requestId, correlationId, "TUSO_UNAVAILABLE", e.getMessage());
        }
    }

    @PostMapping("/localities/proposals")
    public ResponseEntity<Map<String, Object>> proposeLocality(
            @RequestBody Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            JsonNode data = tusoClient.proposeLocality(body);
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                    "data", data != null ? data : Map.of(),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            return upstreamFailure(requestId, correlationId, "TUSO_UNAVAILABLE", e.getMessage());
        }
    }

    @PostMapping("/localities/proposals/{id}/approve")
    public ResponseEntity<Map<String, Object>> approveProposal(
            @PathVariable long id,
            @RequestBody(required = false) Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            JsonNode data = tusoClient.approveLocalityProposal(id, body != null ? body : Map.of());
            return ok(requestId, correlationId, data != null ? data : Map.of());
        } catch (Exception e) {
            return upstreamFailure(requestId, correlationId, "APPROVE_FAILED", e.getMessage());
        }
    }

    @PostMapping("/localities/proposals/{id}/reject")
    public ResponseEntity<Map<String, Object>> rejectProposal(
            @PathVariable long id,
            @RequestBody(required = false) Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            JsonNode data = tusoClient.rejectLocalityProposal(id, body != null ? body : Map.of());
            return ok(requestId, correlationId, data != null ? data : Map.of());
        } catch (Exception e) {
            return upstreamFailure(requestId, correlationId, "REJECT_FAILED", e.getMessage());
        }
    }

    private static ResponseEntity<Map<String, Object>> ok(
            String requestId, String correlationId, Object data) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("data", data);
        body.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
        return ResponseEntity.ok(body);
    }

    private static ResponseEntity<Map<String, Object>> upstreamFailure(
            String requestId, String correlationId, String code, String message) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                "error", Map.of("code", code, "message", message != null ? message : "Registry upstream unavailable"),
                "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
    }
}
