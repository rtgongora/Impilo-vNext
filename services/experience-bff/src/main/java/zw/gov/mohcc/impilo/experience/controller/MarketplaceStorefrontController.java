package zw.gov.mohcc.impilo.experience.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.KhulumaServiceClient;
import zw.gov.mohcc.impilo.experience.client.MsikaFlowServiceClient;
import zw.gov.mohcc.impilo.experience.client.MsikaServiceClient;
import zw.gov.mohcc.impilo.experience.client.RitoServiceClient;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Buyer-facing Msika storefront lane. Composition/orchestration only — it stitches
 * msika-service listings, msika-flow transaction tracking, Rito feedback and Khuluma
 * update requests into the storefront experience. Each downstream is fetched with a
 * safe-partial wrapper so one unavailable owner does not blank the whole page.
 *
 * Owners are never duplicated: orders/fulfilment -> msika-flow, billing -> Costa,
 * payment -> MusheX, stock -> inventory, clinical -> PCT/OROS, comms -> Khuluma,
 * feedback -> Rito. This controller only references them.
 */
@RestController
@RequestMapping("/internal/v1/marketplace/store")
public class MarketplaceStorefrontController {

    private static final Logger log = LoggerFactory.getLogger(MarketplaceStorefrontController.class);

    private final MsikaServiceClient msika;
    private final MsikaFlowServiceClient msikaFlow;
    private final RitoServiceClient rito;
    private final KhulumaServiceClient khuluma;

    public MarketplaceStorefrontController(MsikaServiceClient msika,
                                           MsikaFlowServiceClient msikaFlow,
                                           RitoServiceClient rito,
                                           KhulumaServiceClient khuluma) {
        this.msika = msika;
        this.msikaFlow = msikaFlow;
        this.rito = rito;
        this.khuluma = khuluma;
    }

    // ── Buyer home (composes featured/recent published listings + my activity) ────────
    @GetMapping("/home")
    public ResponseEntity<Map<String, Object>> home(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        Map<String, Object> data = new LinkedHashMap<>();
        LinkedMultiValueMap<String, String> featured = new LinkedMultiValueMap<>();
        featured.add("page", "0");
        featured.add("size", "12");
        data.put("featured", safe(() -> msika.searchListings(featured).getBody(), "featured"));
        data.put("favourites", safe(() -> msika.myFavourites().getBody(), "favourites"));
        data.put("activity", safe(() -> msikaFlow.listOrders(0, 10, null).getBody(), "activity"));
        return envelope(data, requestId, correlationId);
    }

    // ── Search published listings (policy/eligibility filters applied upstream) ───────
    @GetMapping("/search")
    public ResponseEntity<Map<String, Object>> search(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String risk,
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        LinkedMultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        if (q != null && !q.isBlank()) params.add("q", q);
        if (risk != null && !risk.isBlank()) params.add("risk", risk);
        if (category != null && !category.isBlank()) params.add("category", category);
        params.add("page", String.valueOf(page));
        params.add("size", String.valueOf(size));
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("listings", safe(() -> msika.searchListings(params).getBody(), "listings"));
        return envelope(data, requestId, correlationId);
    }

    // ── Listing detail (regulated audit happens in msika-service) ─────────────────────
    @GetMapping("/listing/{listingId}")
    public ResponseEntity<Map<String, Object>> listing(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @PathVariable String listingId) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("listing", safe(() -> msika.getListing(listingId).getBody(), "listing"));
        return envelope(data, requestId, correlationId);
    }

    @PutMapping("/listing/{listingId}/favourite")
    public ResponseEntity<Map<String, Object>> favourite(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @PathVariable String listingId) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("result", safe(() -> msika.favourite(listingId).getBody(), "result"));
        return envelope(data, requestId, correlationId);
    }

    @DeleteMapping("/listing/{listingId}/favourite")
    public ResponseEntity<Map<String, Object>> unfavourite(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @PathVariable String listingId) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("result", safe(() -> msika.unfavourite(listingId).getBody(), "result"));
        return envelope(data, requestId, correlationId);
    }

    // ── Buyer activity / order tracking (composes msika-flow order + tracking) ────────
    @GetMapping("/activity")
    public ResponseEntity<Map<String, Object>> activity(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("orders", safe(() -> msikaFlow.listOrders(page, size, null).getBody(), "orders"));
        return envelope(data, requestId, correlationId);
    }

    /** Compose one transaction timeline: msika-flow order + status summary + tracking. */
    @GetMapping("/activity/{orderId}")
    public ResponseEntity<Map<String, Object>> activityDetail(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @PathVariable String orderId) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("order", safe(() -> msikaFlow.getOrder(orderId).getBody(), "order"));
        data.put("tracking", safe(() -> msikaFlow.getTracking(orderId).getBody(), "tracking"));
        return envelope(data, requestId, correlationId);
    }

    // ── Feedback / safety -> Rito (link, don't own) ───────────────────────────────────
    @PostMapping("/feedback")
    public ResponseEntity<Map<String, Object>> reportToRito(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @RequestBody Map<String, Object> body) {
        Map<String, Object> caseBody = new LinkedHashMap<>(body);
        caseBody.putIfAbsent("source", "MSIKA_MARKETPLACE");
        caseBody.putIfAbsent("caseType", body.getOrDefault("caseType", "COMPLAINT"));
        if (actorId != null) caseBody.putIfAbsent("reporterActorId", actorId);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("case", safe(() -> rito.createCase(caseBody), "case"));
        return envelope(data, requestId, correlationId);
    }

    // ── Request an outbound update via Khuluma (request, don't send) ──────────────────
    @PostMapping("/notify")
    public ResponseEntity<Map<String, Object>> requestKhulumaUpdate(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody Map<String, Object> body) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("dispatch", safe(() -> khuluma.dispatch(tenantId, body).getBody(), "dispatch"));
        return envelope(data, requestId, correlationId);
    }

    // ── helpers ───────────────────────────────────────────────────────────────────────

    /** Safe-partial: a downstream failure degrades just its section, not the page. */
    private Object safe(Supplier<Object> supplier, String section) {
        try {
            return supplier.get();
        } catch (Exception e) {
            log.warn("storefront section '{}' degraded: {}", section, e.getMessage());
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
