package zw.gov.mohcc.impilo.experience.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.MarketplaceFlowServiceClient;

/**
 * OF-B7 — patient offer-comparison + selection proxy over the msika-flow
 * regulated request-for-offer surface ({@code /v1/marketplace-requests}).
 *
 * <p>Pure stateless composition (experience-bff holds no truth — §11.6): every
 * upstream payload is forwarded verbatim, so the msika-flow coded envelope
 * ({@code {success,data|error:{code,message,status},correlationId}}) reaches
 * the shell with its REAL status and code. Masking a governed rejection
 * (OFFER_EXPIRED, STOCK_UNAVAILABLE, PA_REQUIRED, FINANCIAL_UNVERIFIED, …) as
 * a 502 would turn honest revalidation outcomes into fake outages — the same
 * passthrough doctrine as {@code TeleconsultController#upstreamFailure}.
 * Only transport-level failures (connection refused/timeout) are converted to
 * a coded 502 {@code MSIKA_FLOW_UNAVAILABLE} envelope.</p>
 *
 * <p>PII floor: the upstream views are PII-free by construction (§11.8
 * published snapshots; prescription tokens write-only) — this controller adds
 * nothing and strips nothing.</p>
 */
@RestController
@RequestMapping("/internal/v1/marketplace")
public class MarketplaceRequestBffController {

    private static final Logger log = LoggerFactory.getLogger(MarketplaceRequestBffController.class);

    private final MarketplaceFlowServiceClient marketplaceFlowClient;

    public MarketplaceRequestBffController(MarketplaceFlowServiceClient marketplaceFlowClient) {
        this.marketplaceFlowClient = marketplaceFlowClient;
    }

    /** Request view — §11.8-minimised published lines + windows/status. */
    @GetMapping("/requests/{requestId}")
    public ResponseEntity<String> getRequest(
            @PathVariable String requestId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String reqId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return forward("request fetch", reqId, correlationId,
                () -> marketplaceFlowClient.getRequest(requestId));
    }

    /** Ranked offer comparison listing (rankedBecause + per-offer financials). */
    @GetMapping("/requests/{requestId}/offers")
    public ResponseEntity<String> listOffers(
            @PathVariable String requestId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String reqId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return forward("offer listing", reqId, correlationId,
                () -> marketplaceFlowClient.listOffers(requestId));
    }

    /**
     * RC-1 idempotent selection commit. The Idempotency-Key is mandatory here
     * (a retried tap must replay, never double-commit) and is passed through
     * explicitly so it survives any BFF-side mutation fan-out.
     */
    @PostMapping(value = "/requests/{requestId}/select", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> select(
            @PathVariable String requestId,
            @RequestBody String body,
            @RequestHeader(CompanionHeaders.IDEMPOTENCY_KEY) String idempotencyKey,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String reqId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return forward("offer selection", reqId, correlationId,
                () -> marketplaceFlowClient.select(requestId, body, idempotencyKey));
    }

    /** OF-B14 split selection — honest per-member outcomes, PARTIAL_COMMIT rollup. */
    @PostMapping(value = "/requests/{requestId}/select-split", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> selectSplit(
            @PathVariable String requestId,
            @RequestBody String body,
            @RequestHeader(CompanionHeaders.IDEMPOTENCY_KEY) String idempotencyKey,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String reqId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return forward("split selection", reqId, correlationId,
                () -> marketplaceFlowClient.selectSplit(requestId, body, idempotencyKey));
    }

    /** OF-B17 fulfilment-pathway election (PICKUP | DELIVERY + delivery details). */
    @PutMapping(value = "/requests/{requestId}/fulfilment-pathway", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> electPathway(
            @PathVariable String requestId,
            @RequestBody String body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String reqId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return forward("pathway election", reqId, correlationId,
                () -> marketplaceFlowClient.electPathway(requestId, body));
    }

    /** Patient-side cancel (optional {@code {reason}} body). */
    @PostMapping("/requests/{requestId}/cancel")
    public ResponseEntity<String> cancel(
            @PathVariable String requestId,
            @RequestBody(required = false) String body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String reqId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return forward("request cancel", reqId, correlationId,
                () -> marketplaceFlowClient.cancel(requestId, body));
    }

    // ── passthrough plumbing ─────────────────────────────────────────────

    private ResponseEntity<String> forward(
            String action, String requestId, String correlationId, UpstreamCall call) {
        try {
            return withMeta(call.run(), requestId, correlationId);
        } catch (HttpStatusCodeException upstream) {
            // Coded-envelope passthrough: msika-flow domain rejections (4xx/409/422)
            // carry {error:{code,message,status}} — forward status + body verbatim.
            log.warn("Marketplace {} rejected upstream status={}", action, upstream.getStatusCode());
            MediaType contentType = upstream.getResponseHeaders() != null
                    ? upstream.getResponseHeaders().getContentType()
                    : null;
            return ResponseEntity.status(upstream.getStatusCode())
                    .contentType(contentType != null ? contentType : MediaType.APPLICATION_JSON)
                    .header(CompanionHeaders.REQUEST_ID, requestId)
                    .header(CompanionHeaders.CORRELATION_ID, correlationId)
                    .body(upstream.getResponseBodyAsString());
        } catch (RestClientException transport) {
            // Transport failure only — never a masked domain outcome.
            log.warn("Marketplace {} transport failure: {}", action, transport.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(CompanionHeaders.REQUEST_ID, requestId)
                    .header(CompanionHeaders.CORRELATION_ID, correlationId)
                    .body("{\"success\":false,\"error\":{\"code\":\"MSIKA_FLOW_UNAVAILABLE\","
                            + "\"message\":\"The fulfilment marketplace is temporarily unavailable. Please try again shortly.\","
                            + "\"status\":502}}");
        }
    }

    private ResponseEntity<String> withMeta(
            ResponseEntity<String> upstream, String requestId, String correlationId) {
        MediaType contentType = upstream.getHeaders().getContentType();
        return ResponseEntity.status(upstream.getStatusCode())
                .contentType(contentType != null ? contentType : MediaType.APPLICATION_JSON)
                .header(CompanionHeaders.REQUEST_ID, requestId)
                .header(CompanionHeaders.CORRELATION_ID, correlationId)
                .body(upstream.getBody());
    }

    @FunctionalInterface
    private interface UpstreamCall {
        ResponseEntity<String> run();
    }
}
