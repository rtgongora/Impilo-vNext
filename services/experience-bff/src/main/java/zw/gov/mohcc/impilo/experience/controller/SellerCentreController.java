package zw.gov.mohcc.impilo.experience.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.MsikaFlowServiceClient;
import zw.gov.mohcc.impilo.experience.client.MsikaServiceClient;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Msika Seller Centre — create/edit/submit/publish/unpublish listings, manage the
 * storefront, view own orders, and (for operators) the moderation queue + decisions.
 * Composition only; persistence lives in msika-service. Policy (impilo.msika) is
 * enforced at the Tshepo gate; msika-service applies role gates as defence in depth.
 */
@RestController
@RequestMapping("/internal/v1/marketplace/seller")
public class SellerCentreController {

    private static final Logger log = LoggerFactory.getLogger(SellerCentreController.class);

    private final MsikaServiceClient msika;
    private final MsikaFlowServiceClient msikaFlow;

    public SellerCentreController(MsikaServiceClient msika, MsikaFlowServiceClient msikaFlow) {
        this.msika = msika;
        this.msikaFlow = msikaFlow;
    }

    // ── Storefront ────────────────────────────────────────────────────────────────────
    @PostMapping("/storefront")
    public ResponseEntity<Map<String, Object>> createStorefront(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody String body) {
        return passthrough(() -> msika.createStorefront(body).getBody(), "storefront", requestId, correlationId);
    }

    @PostMapping("/storefront/{storefrontId}/verify")
    public ResponseEntity<Map<String, Object>> verifyStorefront(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @PathVariable String storefrontId,
            @RequestBody String body) {
        return passthrough(() -> msika.verifyStorefront(storefrontId, body).getBody(), "storefront", requestId, correlationId);
    }

    // ── Listing authoring ──────────────────────────────────────────────────────────────
    @PostMapping("/listings")
    public ResponseEntity<Map<String, Object>> createListing(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody String body) {
        return passthrough(() -> msika.createListing(body).getBody(), "listing", requestId, correlationId);
    }

    @PatchMapping("/listings/{listingId}")
    public ResponseEntity<Map<String, Object>> updateListing(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @PathVariable String listingId,
            @RequestBody String body) {
        return passthrough(() -> msika.updateListing(listingId, body).getBody(), "listing", requestId, correlationId);
    }

    @PostMapping("/listings/{listingId}/submit")
    public ResponseEntity<Map<String, Object>> submit(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @PathVariable String listingId) {
        return passthrough(() -> msika.submitListing(listingId).getBody(), "listing", requestId, correlationId);
    }

    @PostMapping("/listings/{listingId}/publish")
    public ResponseEntity<Map<String, Object>> publish(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @PathVariable String listingId) {
        return passthrough(() -> msika.publishListing(listingId).getBody(), "listing", requestId, correlationId);
    }

    @PostMapping("/listings/{listingId}/unpublish")
    public ResponseEntity<Map<String, Object>> unpublish(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @PathVariable String listingId) {
        return passthrough(() -> msika.unpublishListing(listingId).getBody(), "listing", requestId, correlationId);
    }

    @GetMapping("/listings/{sellerType}/{sellerId}")
    public ResponseEntity<Map<String, Object>> sellerListings(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @PathVariable String sellerType,
            @PathVariable String sellerId) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("listings", safe(() -> msika.listingsForSeller(sellerType, sellerId).getBody(), "listings"));
        data.put("orders", safe(() -> msikaFlow.listOrders(0, 20, null).getBody(), "orders"));
        return envelope(data, requestId, correlationId);
    }

    // ── Moderation (operator/governance) ───────────────────────────────────────────────
    @GetMapping("/moderation/queue")
    public ResponseEntity<Map<String, Object>> moderationQueue(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        LinkedMultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("page", String.valueOf(page));
        params.add("size", String.valueOf(size));
        return passthrough(() -> msika.moderationQueue(params).getBody(), "queue", requestId, correlationId);
    }

    @PostMapping("/moderation/{listingId}/approve")
    public ResponseEntity<Map<String, Object>> approve(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @PathVariable String listingId,
            @RequestBody(required = false) String body) {
        return passthrough(() -> msika.approveListing(listingId, body != null ? body : "{}").getBody(), "listing", requestId, correlationId);
    }

    @PostMapping("/moderation/{listingId}/reject")
    public ResponseEntity<Map<String, Object>> reject(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @PathVariable String listingId,
            @RequestBody(required = false) String body) {
        return passthrough(() -> msika.rejectListing(listingId, body != null ? body : "{}").getBody(), "listing", requestId, correlationId);
    }

    @PostMapping("/moderation/{listingId}/suspend")
    public ResponseEntity<Map<String, Object>> suspend(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @PathVariable String listingId,
            @RequestBody(required = false) String body) {
        return passthrough(() -> msika.suspendListing(listingId, body != null ? body : "{}").getBody(), "listing", requestId, correlationId);
    }

    // ── helpers ─────────────────────────────────────────────────────────────────────────

    private ResponseEntity<Map<String, Object>> passthrough(Supplier<Object> supplier, String key, String requestId, String correlationId) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put(key, safe(supplier, key));
        return envelope(data, requestId, correlationId);
    }

    private Object safe(Supplier<Object> supplier, String section) {
        try {
            return supplier.get();
        } catch (Exception e) {
            log.warn("seller-centre section '{}' degraded: {}", section, e.getMessage());
            return Map.of("status", "UNAVAILABLE", "section", section);
        }
    }

    private ResponseEntity<Map<String, Object>> envelope(Map<String, Object> data, String requestId, String correlationId) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", data);
        response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
        return ResponseEntity.ok(response);
    }
}
