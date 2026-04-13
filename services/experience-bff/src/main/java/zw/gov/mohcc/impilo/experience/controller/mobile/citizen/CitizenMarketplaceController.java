package zw.gov.mohcc.impilo.experience.controller.mobile.citizen;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.MsikaFlowServiceClient;
import zw.gov.mohcc.impilo.experience.controller.ResourceNotFoundException;

import java.util.*;

/**
 * Citizen marketplace endpoints.
 * GET  /internal/v1/mobile/citizen/marketplace/services
 * GET  /internal/v1/mobile/citizen/marketplace/services/{id}
 * POST /internal/v1/mobile/citizen/marketplace/requests
 * GET  /internal/v1/mobile/citizen/marketplace/requests
 * GET  /internal/v1/mobile/citizen/marketplace/requests/{id}
 * POST /internal/v1/mobile/citizen/marketplace/requests/{id}/cancel
 */
@RestController
@RequestMapping("/internal/v1/mobile/citizen/marketplace")
public class CitizenMarketplaceController {

    private final MsikaFlowServiceClient msikaFlowClient;

        this.msikaFlowClient = msikaFlowClient;
    }

    public record ServiceRequestBody(
            @NotBlank String serviceId,
            String preferredDate,
            String notes
    ) {}

    @GetMapping("/services")
    public ResponseEntity<Map<String, Object>> listServices(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        // STRANGLER: migrated — delegate to MsikaFlowServiceClient
        org.springframework.util.LinkedMultiValueMap<String, String> params = new org.springframework.util.LinkedMultiValueMap<>();
        if (category != null && !category.isBlank()) params.add("category", category);
        if (search != null && !search.isBlank()) params.add("search", search);
        params.add("page", String.valueOf(page));
        params.add("size", String.valueOf(Math.min(size, 100)));

        ResponseEntity<String> result = msikaFlowClient.listVendors(params);

        // STRANGLER: migrated — was direct JdbcTemplate SELECT from marketplace_services table
        // List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql.toString(), params.toArray());

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", result.getBody());
        response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
        return ResponseEntity.ok(response);
    }

    @GetMapping("/services/{id}")
    public ResponseEntity<Map<String, Object>> getService(
            @PathVariable UUID id,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {

        // STRANGLER: migrated — delegate to MsikaFlowServiceClient
        ResponseEntity<String> result = msikaFlowClient.getOrder(id.toString());

        // STRANGLER: migrated — was direct JdbcTemplate SELECT from marketplace_services
        // List<Map<String, Object>> rows = jdbcTemplate.queryForList(..., id, tenantId);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", result.getBody());
        response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
        return ResponseEntity.ok(response);
    }

    @PostMapping("/requests")
    @Transactional
    public ResponseEntity<Map<String, Object>> createRequest(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.POD_ID) String podId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader("X-Actor-ID") String actorId,
            @Valid @RequestBody ServiceRequestBody body) {

        // STRANGLER: migrated — delegate to MsikaFlowServiceClient
        String orderBody = String.format(
                "{\"serviceId\":\"%s\",\"citizenCpid\":\"%s\",\"notes\":\"%s\"}",
                body.serviceId(), actorId, body.notes() != null ? body.notes() : "");

        ResponseEntity<String> result = msikaFlowClient.createOrder(orderBody);

        // STRANGLER: migrated — was direct JdbcTemplate INSERT into service_requests table
        // jdbcTemplate.update("""
        //     INSERT INTO service_requests (...) VALUES (...)
        //     """, ...);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", result.getBody());
        response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/requests")
    public ResponseEntity<Map<String, Object>> listRequests(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader("X-Actor-ID") String actorId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        // STRANGLER: migrated — delegate to MsikaFlowServiceClient
        org.springframework.util.LinkedMultiValueMap<String, String> params = new org.springframework.util.LinkedMultiValueMap<>();
        params.add("citizenCpid", actorId);
        if (status != null) params.add("status", status);
        params.add("page", String.valueOf(page));
        params.add("size", String.valueOf(Math.min(size, 100)));

        ResponseEntity<String> result = msikaFlowClient.listVendors(params);

        // STRANGLER: migrated — was direct JdbcTemplate SELECT from service_requests
        // List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql.toString(), params.toArray());

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", result.getBody());
        response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
        return ResponseEntity.ok(response);
    }

    @GetMapping("/requests/{id}")
    public ResponseEntity<Map<String, Object>> getRequest(
            @PathVariable UUID id,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {

        // STRANGLER: migrated — delegate to MsikaFlowServiceClient
        ResponseEntity<String> result = msikaFlowClient.getOrder(id.toString());

        // STRANGLER: migrated — was direct JdbcTemplate SELECT from service_requests
        // List<Map<String, Object>> rows = jdbcTemplate.queryForList(..., id, tenantId);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", result.getBody());
        response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
        return ResponseEntity.ok(response);
    }

    @PostMapping("/requests/{id}/cancel")
    @Transactional
    public ResponseEntity<Map<String, Object>> cancelRequest(
            @PathVariable UUID id,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.POD_ID) String podId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody(required = false) Map<String, Object> body) {

        // STRANGLER: migrated — delegate to MsikaFlowServiceClient
        msikaFlowClient.cancelOrder(id.toString());

        // STRANGLER: migrated — was direct JdbcTemplate UPDATE on service_requests
        // jdbcTemplate.update("""
        //     UPDATE service_requests SET status = 'CANCELLED', updated_at = ?
        //     WHERE id = ? AND tenant_id = ? AND status IN ('PENDING', 'CONFIRMED')
        //     """, ...);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", Map.of("id", id.toString(), "status", "CANCELLED"));
        response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
        return ResponseEntity.ok(response);
    }
}
