package zw.gov.mohcc.impilo.wallet.api.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;
import zw.gov.mohcc.impilo.wallet.card.BillContributionService;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Mushe social funding — shareable "help pay my bill" links (unified card, Phase 3 feature).
 * Create a request, view it by its share token (the link target), and contribute toward it.
 */
@RestController
@RequestMapping("/internal/v1/bill-contributions")
public class BillContributionController {

    private final BillContributionService service;

    public BillContributionController(BillContributionService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Object>> create(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(value = CompanionHeaders.CORRELATION_ID, required = false) String correlationId,
            @RequestBody Map<String, Object> body) {
        try {
            var request = service.createRequest(
                    UUID.fromString(tenantId),
                    uuid(body, "beneficiaryWalletId"),
                    str(body, "title"),
                    str(body, "billRef"),
                    body.get("targetAmount") != null ? new BigDecimal(str(body, "targetAmount")) : null,
                    str(body, "currency"),
                    str(body, "createdBy"),
                    str(body, "origin"),
                    uuid(body, "campaignRef"),
                    uuid(body, "beneficiaryTargetWalletId"));
            return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(request, correlationId));
        } catch (BillContributionService.ContributionRejected e) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                    .body(ApiResponse.error("CONTRIBUTION_REQUEST_REJECTED", e.getMessage(), 422, correlationId));
        }
    }

    /** The share-link target — anyone with the token can view the request + progress. */
    @GetMapping("/{shareToken}")
    public ResponseEntity<ApiResponse<Object>> view(
            @PathVariable String shareToken,
            @RequestHeader(value = CompanionHeaders.CORRELATION_ID, required = false) String correlationId) {
        try {
            var request = service.getByToken(shareToken);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("request", request);
            data.put("contributions", service.contributions(request.getId()));
            return ResponseEntity.ok(ApiResponse.ok(data, correlationId));
        } catch (BillContributionService.ContributionRejected e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("CONTRIBUTION_LINK_UNKNOWN", e.getMessage(), 404, correlationId));
        }
    }

    @PostMapping("/{shareToken}/contribute")
    public ResponseEntity<ApiResponse<Object>> contribute(
            @PathVariable String shareToken,
            @RequestHeader(value = CompanionHeaders.CORRELATION_ID, required = false) String correlationId,
            @RequestHeader(value = CompanionHeaders.IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @RequestBody Map<String, Object> body) {
        try {
            var contribution = service.contribute(shareToken,
                    new BigDecimal(str(body, "amount")),
                    str(body, "contributorRef"), str(body, "contributorName"), str(body, "message"),
                    idempotencyKey,
                    uuid(body, "contributorWalletId"),
                    str(body, "origin"),
                    Boolean.parseBoolean(str(body, "isAnonymous")));
            return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(contribution, correlationId));
        } catch (BillContributionService.ContributionRejected e) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                    .body(ApiResponse.error("CONTRIBUTION_REJECTED", e.getMessage(), 422, correlationId));
        }
    }

    /**
     * Release escrowed campaign funds to the real beneficiary. Body: {@code {amount?}} — omit
     * amount to release the full escrow balance. The case layer (Daidzai) gates on campaign
     * governance before calling; mushe enforces the mechanical invariants (no over-release).
     */
    @PostMapping("/{requestId}/release")
    public ResponseEntity<ApiResponse<Object>> release(
            @PathVariable String requestId,
            @RequestHeader(value = CompanionHeaders.CORRELATION_ID, required = false) String correlationId,
            @RequestHeader(value = CompanionHeaders.IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @RequestBody(required = false) Map<String, Object> body) {
        try {
            var txn = service.release(UUID.fromString(requestId),
                    body != null && body.get("amount") != null ? new BigDecimal(str(body, "amount")) : null,
                    idempotencyKey, actorId);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("requestId", requestId);
            data.put("amount", txn.getAmount());
            data.put("txnId", txn.getTxnId());
            return ResponseEntity.ok(ApiResponse.ok(data, correlationId));
        } catch (BillContributionService.ContributionRejected e) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                    .body(ApiResponse.error("RELEASE_REJECTED", e.getMessage(), 422, correlationId));
        }
    }

    @PostMapping("/{requestId}/close")
    public ResponseEntity<ApiResponse<Object>> close(
            @PathVariable String requestId,
            @RequestHeader(value = CompanionHeaders.CORRELATION_ID, required = false) String correlationId) {
        return ResponseEntity.ok(ApiResponse.ok(service.close(UUID.fromString(requestId)), correlationId));
    }

    private static String str(Map<String, Object> body, String key) {
        Object v = body == null ? null : body.get(key);
        return v == null ? null : v.toString();
    }

    private static UUID uuid(Map<String, Object> body, String key) {
        String v = str(body, key);
        return v == null || v.isBlank() ? null : UUID.fromString(v);
    }
}
