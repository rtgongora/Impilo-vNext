package zw.gov.mohcc.impilo.experience.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpStatusCodeException;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.MushexServiceClient;
import java.util.List;

@RestController
@RequestMapping("/internal/v1/finance/payer-ops")
public class PayerOpsController {

    private static final Logger log = LoggerFactory.getLogger(PayerOpsController.class);

    private final MushexServiceClient mushexClient;

    public PayerOpsController(MushexServiceClient mushexClient) {
        this.mushexClient = mushexClient;
    }

    @GetMapping("/payment-intents/{intentId}")
    public ResponseEntity<String> getPaymentIntent(
            @PathVariable String intentId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId
    ) {
        return forward("get intent", requestId, correlationId, () -> mushexClient.getPaymentIntent(intentId));
    }

    @GetMapping("/payment-intents")
    public ResponseEntity<String> listPaymentIntentsBySource(
            @RequestParam(name = "sourceType") String sourceType,
            @RequestParam(name = "sourceId", required = false) String sourceId,
            @RequestParam(name = "sourceIds", required = false) List<String> sourceIds,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId
    ) {
        return forward("list intents by source", requestId, correlationId,
                () -> mushexClient.listPaymentIntentsBySource(sourceType, sourceId, sourceIds));
    }

    @PostMapping("/payment-intents/{intentId}/cancel")
    public ResponseEntity<String> cancelIntent(
            @PathVariable String intentId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId
    ) {
        return forward("cancel intent", requestId, correlationId, () -> mushexClient.cancelIntent(intentId));
    }

    /**
     * Phase 3 — attempt-time rail enforcement.
     * <p>Read-only-from-the-BFF-side passthrough: forwards body and optional
     * {@code X-Idempotency-Key} header to {@code POST /mushex/v1/payment-intents/{intentId}/attempts}.
     * The MusheX service applies the rail-enforcement decision and the safety gate that
     * prevents real-money adapters from being called unless explicitly enabled by config.
     *
     * <p>See {@code docs/design/phase-3-attempt-time-rail-enforcement-implementation.md}.
     */
    @PostMapping("/payment-intents/{intentId}/attempts")
    public ResponseEntity<String> initiatePaymentAttempt(
            @PathVariable String intentId,
            @RequestBody(required = false) String body,
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId
    ) {
        return forward("initiate payment attempt", requestId, correlationId,
                () -> mushexClient.initiatePaymentAttempt(intentId, body, idempotencyKey));
    }

    /**
     * Phase 3 follow-on — read-only chronological list of attempts for the named intent.
     * Backed by {@code GET /mushex/v1/payment-intents/{intentId}/attempts}; the BFF does
     * not paginate or trim — operators see the same envelope MusheX returns.
     */
    @GetMapping("/payment-intents/{intentId}/attempts")
    public ResponseEntity<String> listPaymentAttempts(
            @PathVariable String intentId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId
    ) {
        return forward("list payment attempts", requestId, correlationId,
                () -> mushexClient.listPaymentAttempts(intentId));
    }

    /**
     * Phase 3 follow-on — controlled rail reselection. No idempotency key: this endpoint
     * never moves money, it only re-runs {@code RailSelectionPolicy} against the persisted
     * intent and appends the previous decision to {@code metadata.rail_selection.history[]}.
     */
    @PostMapping("/payment-intents/{intentId}/attempts/reselect")
    public ResponseEntity<String> reselectRail(
            @PathVariable String intentId,
            @RequestBody(required = false) String body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId
    ) {
        return forward("reselect rail", requestId, correlationId,
                () -> mushexClient.reselectRail(intentId, body));
    }

    @GetMapping("/payment-intents/{intentId}/receipts")
    public ResponseEntity<String> getReceipts(
            @PathVariable String intentId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId
    ) {
        return forward("get receipts", requestId, correlationId, () -> mushexClient.getReceipts(intentId));
    }

    @PostMapping("/payment-intents/{intentId}/issue-remittance-slip")
    public ResponseEntity<String> issueRemittanceSlip(
            @PathVariable String intentId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId
    ) {
        return forward("issue remittance", requestId, correlationId, () -> mushexClient.issueRemittanceSlip(intentId));
    }

    @PostMapping(value = "/remittance/claim", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> claimRemittance(
            @RequestBody String body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId
    ) {
        return forward("claim remittance", requestId, correlationId, () -> mushexClient.claimRemittance(body));
    }

    @GetMapping("/adapters")
    public ResponseEntity<String> listAdapters(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId
    ) {
        return forward("list adapters", requestId, correlationId, mushexClient::listAdapters);
    }

    @GetMapping("/fraud-flags")
    public ResponseEntity<String> listFraudFlags(
            @RequestParam MultiValueMap<String, String> queryParams,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId
    ) {
        return forward("list fraud flags", requestId, correlationId,
                () -> mushexClient.listFraudFlags(new LinkedMultiValueMap<>(queryParams)));
    }

    @GetMapping("/ops-reviews")
    public ResponseEntity<String> listOpsReviews(
            @RequestParam MultiValueMap<String, String> queryParams,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId
    ) {
        return forward("list ops reviews", requestId, correlationId,
                () -> mushexClient.listOpsReviews(new LinkedMultiValueMap<>(queryParams)));
    }

    @PostMapping(value = "/ops-reviews/{reviewId}/approve", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> approveOpsReview(
            @PathVariable String reviewId,
            @RequestBody(required = false) String body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId
    ) {
        return forward("approve ops review", requestId, correlationId,
                () -> mushexClient.approveOpsReview(reviewId, normalizeBody(body)));
    }

    @PostMapping(value = "/ops-reviews/{reviewId}/reject", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> rejectOpsReview(
            @PathVariable String reviewId,
            @RequestBody(required = false) String body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId
    ) {
        return forward("reject ops review", requestId, correlationId,
                () -> mushexClient.rejectOpsReview(reviewId, normalizeBody(body)));
    }

    private static String normalizeBody(String body) {
        return body == null || body.isBlank() ? "{}" : body;
    }

    private ResponseEntity<String> forward(String action, String requestId, String correlationId, UpstreamCall call) {
        try {
            return withHeaders(call.run(), requestId, correlationId);
        } catch (HttpStatusCodeException exception) {
            log.warn("Payer ops {} failed status={}", action, exception.getStatusCode());
            return withHeaders(exception, requestId, correlationId);
        }
    }

    private ResponseEntity<String> withHeaders(ResponseEntity<String> upstream, String requestId, String correlationId) {
        MediaType contentType = upstream.getHeaders().getContentType();
        return ResponseEntity.status(upstream.getStatusCode())
                .contentType(contentType != null ? contentType : MediaType.APPLICATION_JSON)
                .header(CompanionHeaders.REQUEST_ID, requestId)
                .header(CompanionHeaders.CORRELATION_ID, correlationId)
                .body(upstream.getBody());
    }

    private ResponseEntity<String> withHeaders(HttpStatusCodeException upstream, String requestId, String correlationId) {
        MediaType contentType = upstream.getResponseHeaders() != null ? upstream.getResponseHeaders().getContentType() : null;
        return ResponseEntity.status(upstream.getStatusCode())
                .contentType(contentType != null ? contentType : MediaType.APPLICATION_JSON)
                .header(CompanionHeaders.REQUEST_ID, requestId)
                .header(CompanionHeaders.CORRELATION_ID, correlationId)
                .body(upstream.getResponseBodyAsString());
    }

    @FunctionalInterface
    private interface UpstreamCall {
        ResponseEntity<String> run();
    }
}
