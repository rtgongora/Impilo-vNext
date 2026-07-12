package zw.gov.mohcc.impilo.experience.controller.mobile.citizen;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpStatusCodeException;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.MsikaFlowServiceClient;
import zw.gov.mohcc.impilo.experience.client.MsikaServiceClient;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Citizen marketplace endpoints (mobile buyer lane).
 *
 * <p>Honest composition over the real marketplace services:
 * <ul>
 *   <li>{@code GET /services}      → msika-service {@code /v1/listings/search} (published listings, real category).</li>
 *   <li>{@code GET /services/{id}} → msika-service {@code /v1/listings/{id}} (listing detail).</li>
 *   <li>{@code POST /requests}     → msika-flow {@code /v1/orders} (creates an order for the citizen buyer).</li>
 *   <li>{@code GET /requests}      → msika-flow {@code /v1/orders} filtered to the caller's actor (buyer scope).</li>
 *   <li>{@code GET /requests/{id}} → msika-flow {@code /v1/orders/{id}}.</li>
 *   <li>{@code POST /requests/{id}/cancel} → msika-flow {@code /v1/orders/{id}/cancel} (truthful passthrough).</li>
 * </ul>
 *
 * <p>Response shapes are held stable for {@code apps/mobile/citizen-app}
 * ({@code marketplaceService.ts}): paged endpoints return
 * {@code {data:[...], meta:{page:{...}}}}; single/detail return {@code {data:{...}}}.
 *
 * <p>Buyer scoping note: msika-flow does not expose a buyer-filtered order query today,
 * so {@code GET /requests} fetches the tenant order page and filters defensively on
 * {@code actorId == X-Actor-ID}. When msika-flow adds a buyer filter, swap the client call.
 */
@RestController
@RequestMapping("/internal/v1/mobile/citizen/marketplace")
public class CitizenMarketplaceController {

    private static final Logger log = LoggerFactory.getLogger(CitizenMarketplaceController.class);

    private final MsikaServiceClient msikaClient;
    private final MsikaFlowServiceClient msikaFlowClient;
    private final ObjectMapper objectMapper;

    public CitizenMarketplaceController(MsikaServiceClient msikaClient,
                                        MsikaFlowServiceClient msikaFlowClient,
                                        ObjectMapper objectMapper) {
        this.msikaClient = msikaClient;
        this.msikaFlowClient = msikaFlowClient;
        this.objectMapper = objectMapper;
    }

    public record ServiceRequestBody(
            @NotBlank String serviceId,
            String preferredDate,
            String notes
    ) {}

    // ── Discovery ────────────────────────────────────────────────────────────

    @GetMapping("/services")
    public ResponseEntity<Map<String, Object>> listServices(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        LinkedMultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        if (search != null && !search.isBlank()) params.add("q", search);
        if (category != null && !category.isBlank()) params.add("category", category);
        params.add("page", String.valueOf(Math.max(page, 0)));
        params.add("size", String.valueOf(Math.min(Math.max(size, 1), 100)));

        JsonNode body = parse(msikaClient.searchListings(params));
        JsonNode paged = body.path("data"); // ApiResponse<PagedResponse<ListingView>>
        ArrayNode services = objectMapper.createArrayNode();
        for (JsonNode listing : paged.path("items")) {
            services.add(toService(listing));
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", services);
        response.put("meta", Map.of(
                "page", pageMeta(paged, page, size),
                "request_id", requestId,
                "correlation_id", correlationId));
        return ResponseEntity.ok(response);
    }

    @GetMapping("/services/{id}")
    public ResponseEntity<Map<String, Object>> getService(
            @PathVariable String id,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {

        JsonNode body = parse(msikaClient.getListing(id));
        JsonNode listing = body.path("data");

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", toService(listing));
        response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
        return ResponseEntity.ok(response);
    }

    // ── Requests (orders) ────────────────────────────────────────────────────

    @PostMapping("/requests")
    public ResponseEntity<Map<String, Object>> createRequest(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(CompanionHeaders.ACTOR_ID) String actorId,
            @Valid @RequestBody ServiceRequestBody body) {

        ObjectNode orderBody = objectMapper.createObjectNode();
        orderBody.put("orderType", "SERVICE_BOOKING");
        orderBody.put("patientCpid", actorId);
        ArrayNode lines = orderBody.putArray("lines");
        ObjectNode line = lines.addObject();
        line.put("msikaCoreCode", body.serviceId());
        line.put("kind", "SERVICE");
        line.put("qty", 1);
        line.put("unitPrice", 0);
        boolean hasNotes = body.notes() != null && !body.notes().isBlank();
        boolean hasDate = body.preferredDate() != null && !body.preferredDate().isBlank();
        if (hasNotes || hasDate) {
            ObjectNode metadata = orderBody.putObject("metadata");
            if (hasNotes) metadata.put("notes", body.notes());
            if (hasDate) metadata.put("preferredDate", body.preferredDate());
        }

        JsonNode created = parse(msikaFlowClient.createOrder(write(orderBody))).path("data");

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", toRequest(created));
        response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/requests")
    public ResponseEntity<Map<String, Object>> listRequests(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader("X-Actor-ID") String actorId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        // msika-flow has no buyer-filtered query; fetch the page and filter defensively on actor.
        JsonNode paged = parse(msikaFlowClient.listOrders(Math.max(page, 0), Math.min(Math.max(size, 1), 100), null))
                .path("data");
        ArrayNode requests = objectMapper.createArrayNode();
        for (JsonNode order : paged.path("items")) {
            if (!actorId.equals(order.path("actorId").asText(null))
                    && !actorId.equals(order.path("patientCpid").asText(null))) {
                continue;
            }
            if (status != null && !status.isBlank()
                    && !status.equalsIgnoreCase(order.path("status").asText(""))) {
                continue;
            }
            requests.add(toRequest(order));
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", requests);
        response.put("meta", Map.of(
                "page", pageMeta(paged, page, size),
                "request_id", requestId,
                "correlation_id", correlationId));
        return ResponseEntity.ok(response);
    }

    @GetMapping("/requests/{id}")
    public ResponseEntity<Map<String, Object>> getRequest(
            @PathVariable String id,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {

        JsonNode order = parse(msikaFlowClient.getOrder(id)).path("data");

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", toRequest(order));
        response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
        return ResponseEntity.ok(response);
    }

    @PostMapping("/requests/{id}/cancel")
    public ResponseEntity<Map<String, Object>> cancelRequest(
            @PathVariable String id,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody(required = false) Map<String, Object> ignoredBody) {

        // Truthful passthrough: report the real upstream status, never a hardcoded CANCELLED.
        JsonNode order = parse(msikaFlowClient.cancelOrder(id)).path("data");

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", toRequest(order));
        response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
        return ResponseEntity.ok(response);
    }

    // ── Mapping (msika/msika-flow → stable mobile shapes) ─────────────────────

    private ObjectNode toService(JsonNode listing) {
        ObjectNode out = objectMapper.createObjectNode();
        out.put("id", text(listing, "listingId", "id"));
        out.put("name", text(listing, "title", "name"));
        out.put("description", text(listing, "summary", "description"));
        out.put("category", text(listing, "riskClassification", "category"));
        out.put("facilityId", text(listing, "sellerId", "storefrontId"));
        out.put("facilityName", text(listing, "fulfilmentOwner", "sellerType"));
        JsonNode price = firstOf(listing, "priceAmount", "price");
        if (price != null && !price.isNull()) out.put("price", price.asDouble());
        out.put("currency", textOr(listing, "USD", "priceCurrency", "currency"));
        out.put("available", "PUBLISHED".equalsIgnoreCase(listing.path("status").asText("")));
        JsonNode media = listing.path("media");
        if (media.isArray() && media.size() > 0) {
            out.put("imageUrl", media.get(0).path("mediaRef").asText(null));
        }
        return out;
    }

    private ObjectNode toRequest(JsonNode order) {
        ObjectNode out = objectMapper.createObjectNode();
        String orderId = text(order, "orderId", "id");
        out.put("id", orderId);
        JsonNode lines = order.path("lines");
        String serviceId = lines.isArray() && lines.size() > 0
                ? lines.get(0).path("msikaCoreCode").asText(null) : null;
        out.put("serviceId", serviceId);
        out.put("serviceName", textOr(order, serviceId != null ? serviceId : orderId, "orderType"));
        out.put("facilityName", text(order, "facilityRef", "vendorRef"));
        out.put("status", order.path("status").asText("PENDING"));
        out.put("requestedAt", text(order, "createdAt"));
        out.put("scheduledAt", text(order, "updatedAt"));
        JsonNode metadata = order.path("metadata");
        if (metadata.isObject() && metadata.hasNonNull("notes")) {
            out.put("notes", metadata.path("notes").asText());
        }
        return out;
    }

    private Map<String, Object> pageMeta(JsonNode paged, int fallbackPage, int fallbackSize) {
        int number = paged.path("page").asInt(fallbackPage);
        int size = paged.path("size").asInt(fallbackSize);
        long totalElements = paged.path("totalElements").asLong(paged.path("items").size());
        int totalPages = paged.has("totalPages")
                ? paged.path("totalPages").asInt()
                : (size > 0 ? (int) Math.ceil((double) totalElements / size) : 0);
        Map<String, Object> pageMeta = new LinkedHashMap<>();
        pageMeta.put("number", number);
        pageMeta.put("size", size);
        pageMeta.put("total_elements", totalElements);
        pageMeta.put("total_pages", totalPages);
        return pageMeta;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private JsonNode parse(ResponseEntity<String> upstream) {
        try {
            String body = upstream.getBody();
            return body == null || body.isBlank() ? objectMapper.createObjectNode() : objectMapper.readTree(body);
        } catch (Exception e) {
            log.warn("Citizen marketplace: could not parse upstream body: {}", e.getMessage());
            return objectMapper.createObjectNode();
        }
    }

    private String write(JsonNode node) {
        try {
            return objectMapper.writeValueAsString(node);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialise order body", e);
        }
    }

    private static String text(JsonNode node, String... keys) {
        for (String key : keys) {
            JsonNode value = node.path(key);
            if (value.isTextual() && !value.asText().isBlank()) return value.asText();
        }
        return null;
    }

    private static String textOr(JsonNode node, String fallback, String... keys) {
        String value = text(node, keys);
        return value != null ? value : fallback;
    }

    private static JsonNode firstOf(JsonNode node, String... keys) {
        for (String key : keys) {
            JsonNode value = node.get(key);
            if (value != null && !value.isNull()) return value;
        }
        return null;
    }

    @ExceptionHandler(HttpStatusCodeException.class)
    public ResponseEntity<Map<String, Object>> handleUpstream(HttpStatusCodeException ex) {
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("error", Map.of("code", "UPSTREAM_ERROR", "message", ex.getStatusText()));
        return ResponseEntity.status(ex.getStatusCode()).body(error);
    }
}
