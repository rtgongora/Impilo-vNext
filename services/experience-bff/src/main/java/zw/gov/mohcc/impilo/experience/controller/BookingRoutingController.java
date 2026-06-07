package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.TusoServiceClient;
import zw.gov.mohcc.impilo.experience.client.VarapiServiceClient;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Polymorphic booking routing targets — mirrors teleconsult routing pattern.
 */
@RestController
@RequestMapping("/internal/v1/bookings/routing")
public class BookingRoutingController {

    private static final Logger log = LoggerFactory.getLogger(BookingRoutingController.class);

    private final VarapiServiceClient varapiClient;
    private final TusoServiceClient tusoClient;

    public BookingRoutingController(VarapiServiceClient varapiClient, TusoServiceClient tusoClient) {
        this.varapiClient = varapiClient;
        this.tusoClient = tusoClient;
    }

    @GetMapping("/providers")
    public ResponseEntity<Map<String, Object>> searchProviders(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(name = "q") String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        try {
            Map<String, Object> request = new LinkedHashMap<>();
            request.put("query", query);
            request.put("page", page);
            request.put("size", Math.min(Math.max(size, 1), 50));
            JsonNode providers = varapiClient.searchProviders(request);
            return ok(providers, requestId, correlationId);
        } catch (Exception e) {
            return upstreamFailure("VARAPI_UNAVAILABLE", e.getMessage(), requestId, correlationId);
        }
    }

    @GetMapping("/facilities")
    public ResponseEntity<Map<String, Object>> searchFacilities(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(name = "q") String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        try {
            Map<String, Object> request = new LinkedHashMap<>();
            request.put("query", query);
            request.put("page", page);
            request.put("size", Math.min(Math.max(size, 1), 50));
            JsonNode facilities = tusoClient.searchFacilities(request);
            return ok(facilities, requestId, correlationId);
        } catch (Exception e) {
            return upstreamFailure("TUSO_UNAVAILABLE", e.getMessage(), requestId, correlationId);
        }
    }

    @GetMapping("/workspaces")
    public ResponseEntity<Map<String, Object>> listWorkspaces(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(name = "facility_id") String facilityId) {
        try {
            long parsedFacilityId = Long.parseLong(facilityId);
            JsonNode workspaces = tusoClient.listWorkspaces(parsedFacilityId);
            return ok(workspaces, requestId, correlationId);
        } catch (NumberFormatException nfe) {
            return error(HttpStatus.BAD_REQUEST, "INVALID_FACILITY_ID",
                    "facility_id must be numeric", requestId, correlationId);
        } catch (Exception e) {
            return upstreamFailure("TUSO_UNAVAILABLE", e.getMessage(), requestId, correlationId);
        }
    }

    @GetMapping("/resources")
    public ResponseEntity<Map<String, Object>> listResources(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(name = "facility_id") String facilityId,
            @RequestParam(required = false, name = "resource_type") String resourceType) {
        try {
            long parsedFacilityId = Long.parseLong(facilityId);
            JsonNode resources = tusoClient.listFacilityResources(parsedFacilityId, resourceType);
            return ok(resources, requestId, correlationId);
        } catch (NumberFormatException nfe) {
            return error(HttpStatus.BAD_REQUEST, "INVALID_FACILITY_ID",
                    "facility_id must be numeric", requestId, correlationId);
        } catch (Exception e) {
            log.warn("Failed to fetch facility resources for booking routing: {}", e.getMessage());
            return ok(List.of(), requestId, correlationId);
        }
    }

    private static ResponseEntity<Map<String, Object>> ok(
            Object data, String requestId, String correlationId) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", data);
        response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
        return ResponseEntity.ok(response);
    }

    private static ResponseEntity<Map<String, Object>> error(
            HttpStatus status, String code, String message, String requestId, String correlationId) {
        return ResponseEntity.status(status).body(Map.of(
                "error", Map.of("code", code, "message", message),
                "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
    }

    private static ResponseEntity<Map<String, Object>> upstreamFailure(
            String code, String message, String requestId, String correlationId) {
        log.warn("Booking routing upstream failure: {}", message);
        return error(HttpStatus.BAD_GATEWAY, code,
                message != null ? message : "Booking routing upstream unavailable",
                requestId, correlationId);
    }
}
