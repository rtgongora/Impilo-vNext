package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.MsikaFlowServiceClient;
import zw.gov.mohcc.impilo.experience.client.MsikaServiceClient;

import java.util.*;

/**
 * Marketplace endpoints. Delegates to MsikaFlowServiceClient.
 * GET  /internal/v1/marketplace/orders — list marketplace orders with facility_id filter, pagination.
 * GET  /internal/v1/marketplace/orders/{id} — get single order.
 * POST /internal/v1/marketplace/orders — create order.
 */
@RestController
@RequestMapping("/internal/v1/marketplace")
public class MarketplaceController {

    private static final Logger log = LoggerFactory.getLogger(MarketplaceController.class);

    private final MsikaFlowServiceClient msikaFlowClient;
    private final MsikaServiceClient msikaServiceClient;

    public MarketplaceController(MsikaFlowServiceClient msikaFlowClient, MsikaServiceClient msikaServiceClient) {
        this.msikaFlowClient = msikaFlowClient;
        this.msikaServiceClient = msikaServiceClient;
    }

    public record CreateOrderRequest(
            @NotBlank String facility_id,
            @NotBlank String order_number,
            JsonNode items,
            @NotBlank String ordered_by,
            String total_amount
    ) {}

    @GetMapping("/orders")
    public ResponseEntity<Map<String, Object>> listOrders(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false, name = "facility_id") String facilityId) {
        return unavailable501(
                "MARKETPLACE_ROUTE_UNAVAILABLE",
                "Marketplace order list is not yet exposed on msika-flow-service (no tenant-scoped GET /v1/orders); use order id or vendor-scoped flows.",
                requestId, correlationId);
    }

    @GetMapping("/catalog")
    public ResponseEntity<Map<String, Object>> listCatalog(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String search) {

        LinkedMultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        if (search != null && !search.isBlank()) {
            params.add("q", search);
        }
        if (category != null && !category.isBlank()) {
            params.add("kind", category);
        }
        params.add("page", "0");
        params.add("size", "50");

        try {
            ResponseEntity<String> result = msikaServiceClient.search(params);
            return envelope(result, requestId, correlationId);
        } catch (HttpStatusCodeException e) {
            return envelope(e, requestId, correlationId);
        } catch (Exception e) {
            return unavailable502(
                    "MSIKA_SERVICE_UNAVAILABLE",
                    "Unable to load marketplace catalog while msika-service is unavailable",
                    requestId, correlationId);
        }
    }

    @GetMapping("/vendors")
    public ResponseEntity<Map<String, Object>> listPartners(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {

        try {
            ResponseEntity<String> result = msikaFlowClient.listVendors(null);
            return envelope(result, requestId, correlationId);
        } catch (HttpStatusCodeException e) {
            return envelope(e, requestId, correlationId);
        } catch (Exception e) {
            return unavailable502(
                    "MSIKA_FLOW_UNAVAILABLE",
                    "Unable to load marketplace vendors while msika-flow-service is unavailable",
                    requestId, correlationId);
        }
    }

    @GetMapping("/bookings")
    public ResponseEntity<Map<String, Object>> listBookings(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return unavailable501(
                "MARKETPLACE_ROUTE_UNAVAILABLE",
                "Marketplace bookings list is not exposed on msika-flow-service (bookings are POST-only today).",
                requestId, correlationId);
    }

    @GetMapping("/orders/{id}")
    public ResponseEntity<Map<String, Object>> getOrder(
            @PathVariable UUID id,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {

        try {
            ResponseEntity<String> result = msikaFlowClient.getOrder(id.toString());
            return envelope(result, requestId, correlationId);
        } catch (HttpStatusCodeException e) {
            return envelope(e, requestId, correlationId);
        } catch (Exception e) {
            return unavailable502(
                    "MSIKA_FLOW_UNAVAILABLE",
                    "Unable to fetch marketplace order while msika-flow-service is unavailable",
                    requestId, correlationId);
        }
    }

    @PostMapping("/orders")
    public ResponseEntity<Map<String, Object>> createOrder(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.POD_ID) String podId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @Valid @RequestBody CreateOrderRequest request) {

        String itemsJson = request.items() == null || request.items().isNull()
                ? "[]" : request.items().toString();
        String orderBody = String.format(
                "{\"facility_id\":\"%s\",\"order_number\":\"%s\",\"items\":%s,\"ordered_by\":\"%s\",\"total_amount\":\"%s\"}",
                request.facility_id(), request.order_number(),
                itemsJson,
                request.ordered_by(),
                request.total_amount() != null ? request.total_amount() : "0");

        try {
            ResponseEntity<String> result = msikaFlowClient.createOrder(orderBody);
            return envelope(result, requestId, correlationId);
        } catch (HttpStatusCodeException e) {
            return envelope(e, requestId, correlationId);
        } catch (Exception e) {
            log.warn("MSIKA Flow unavailable for order create: {}", e.getMessage());
            return unavailable502(
                    "MSIKA_FLOW_UNAVAILABLE",
                    "Unable to create marketplace order while msika-flow-service is unavailable",
                    requestId, correlationId);
        }
    }

    private ResponseEntity<Map<String, Object>> envelope(ResponseEntity<String> upstream, String requestId, String correlationId) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", upstream.getBody());
        response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
        return ResponseEntity.status(upstream.getStatusCode()).body(response);
    }

    private ResponseEntity<Map<String, Object>> envelope(HttpStatusCodeException upstream, String requestId, String correlationId) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", upstream.getResponseBodyAsString());
        response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
        return ResponseEntity.status(upstream.getStatusCode()).body(response);
    }

    private ResponseEntity<Map<String, Object>> unavailable502(String code, String message, String requestId, String correlationId) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                "error", Map.of("code", code, "message", message),
                "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
    }

    private ResponseEntity<Map<String, Object>> unavailable501(String code, String message, String requestId, String correlationId) {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).body(Map.of(
                "error", Map.of("code", code, "message", message),
                "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
    }
}
