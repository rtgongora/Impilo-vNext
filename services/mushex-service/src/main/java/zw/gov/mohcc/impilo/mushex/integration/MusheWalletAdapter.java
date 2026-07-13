package zw.gov.mohcc.impilo.mushex.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Adapter for calling the Mushe Wallet service from MUSHEX.
 *
 * <p>Supports three wallet-based payment flows:</p>
 * <ol>
 *   <li>{@link #payFromWallet} — debit a patient's INDIVIDUAL wallet</li>
 *   <li>{@link #payToMerchant} — pay a registered merchant (debit payer, credit merchant)</li>
 *   <li>{@link #disburseToBatch} — credit merchant wallets for settlement payouts</li>
 * </ol>
 */
@Component
public class MusheWalletAdapter {

    private static final Logger log = LoggerFactory.getLogger(MusheWalletAdapter.class);

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public MusheWalletAdapter(
            RestClient.Builder restClientBuilder,
            @Value("${impilo.services.mushe-wallet-base-url:http://localhost:8126}") String baseUrl,
            ObjectMapper objectMapper) {
        this.restClient = restClientBuilder.baseUrl(baseUrl).build();
        this.objectMapper = objectMapper;
    }

    // ── Pay From Wallet ────────────────────────────────────────────────

    /**
     * Debit a patient's INDIVIDUAL wallet.
     *
     * <p>Step 1: look up the wallet by owner (INDIVIDUAL + CPID).<br/>
     * Step 2: POST a debit against that wallet.</p>
     *
     * @return the wallet transaction reference returned by the wallet service
     */
    public WalletPaymentResult payFromWallet(UUID tenantId,
                                              String patientCpid,
                                              BigDecimal amount,
                                              String reference,
                                              String correlationId) {
        log.info("payFromWallet: tenantId={} cpid={} amount={} ref={}", tenantId, patientCpid, amount, reference);

        // Step 1 — find wallet by owner
        UUID walletId = lookupWalletByOwner(tenantId, "INDIVIDUAL", patientCpid);

        // Step 2 — debit wallet
        return debitWallet(tenantId, walletId, amount, reference, correlationId,
                "Wallet payment for " + reference);
    }

    // ── Pay To Merchant ────────────────────────────────────────────────

    /**
     * Pay a merchant using the payer's wallet. The wallet service handles the
     * debit-from-payer + credit-to-merchant atomically.
     */
    public WalletPaymentResult payToMerchant(UUID tenantId,
                                              String fromCpid,
                                              String merchantProviderNumber,
                                              BigDecimal amount,
                                              String reference) {
        log.info("payToMerchant: tenantId={} from={} merchant={} amount={} ref={}",
                tenantId, fromCpid, merchantProviderNumber, amount, reference);

        UUID fromWalletId = lookupWalletByOwner(tenantId, "INDIVIDUAL", fromCpid);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("fromWalletId", fromWalletId.toString());
        body.put("merchantProviderNumber", merchantProviderNumber);
        body.put("amount", amount);
        body.put("reference", reference);
        body.put("channel", "MUSHEX");

        try {
            String raw = restClient.post()
                    .uri("/internal/v1/merchants/pay")
                    .header("X-Tenant-ID", tenantId.toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);

            return parseTransactionResult(raw, "payToMerchant");
        } catch (Exception e) {
            log.error("payToMerchant failed: tenantId={} from={} merchant={}",
                    tenantId, fromCpid, merchantProviderNumber, e);
            throw new WalletAdapterException("Merchant payment failed: " + e.getMessage(), e);
        }
    }

    // ── Disburse To Batch ──────────────────────────────────────────────

    /**
     * Credit merchant wallets for settlement / payout batch items.
     *
     * <p>Per-item failures do not abort the batch, but they are never silently
     * dropped either: every failure is returned with its reason so the caller can
     * mark the batch PARTIAL, surface an ops event, and retry/compensate the
     * missed payouts. A bare success-count return let a batch under-disburse
     * invisibly.</p>
     *
     * @param payoutItems list of maps containing: merchantProviderNumber, amount, reference
     * @return per-item outcome: credited count + the failed items with reasons
     */
    public DisbursementResult disburseToBatch(UUID tenantId, List<Map<String, Object>> payoutItems) {
        log.info("disburseToBatch: tenantId={} items={}", tenantId, payoutItems.size());

        int credited = 0;
        List<FailedDisbursement> failures = new java.util.ArrayList<>();
        for (Map<String, Object> item : payoutItems) {
            String merchantProviderNumber = String.valueOf(item.get("merchantProviderNumber"));
            BigDecimal amount = new BigDecimal(String.valueOf(item.get("amount")));
            String reference = String.valueOf(item.getOrDefault("reference", "SETTLEMENT"));

            try {
                UUID merchantWalletId = lookupMerchantWallet(tenantId, merchantProviderNumber);

                Map<String, Object> body = new LinkedHashMap<>();
                body.put("amount", amount);
                body.put("txnType", "SETTLEMENT");
                body.put("channel", "MUSHEX");
                body.put("reference", reference);
                body.put("description", "Settlement disbursement for " + merchantProviderNumber);
                body.put("counterpartyRef", "MUSHEX_SETTLEMENT");
                body.put("counterpartyName", "MUSheX Settlement Engine");

                restClient.post()
                        .uri("/internal/v1/wallets/{walletId}/credit", merchantWalletId)
                        .header("X-Tenant-ID", tenantId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(body)
                        .retrieve()
                        .body(String.class);

                credited++;
                log.info("Credited merchant wallet: provider={} walletId={} amount={}",
                        merchantProviderNumber, merchantWalletId, amount);
            } catch (Exception e) {
                log.error("Failed to credit merchant wallet: provider={} amount={}",
                        merchantProviderNumber, amount, e);
                failures.add(new FailedDisbursement(merchantProviderNumber, amount, reference,
                        e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()));
            }
        }

        log.info("disburseToBatch complete: tenantId={} credited={}/{} failed={}",
                tenantId, credited, payoutItems.size(), failures.size());
        return new DisbursementResult(credited, List.copyOf(failures));
    }

    // ── Internal Helpers ───────────────────────────────────────────────

    private UUID lookupWalletByOwner(UUID tenantId, String ownerType, String ownerRef) {
        try {
            String raw = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/internal/v1/wallets/by-owner")
                            .queryParam("owner_type", ownerType)
                            .queryParam("owner_ref", ownerRef)
                            .build())
                    .header("X-Tenant-ID", tenantId.toString())
                    .retrieve()
                    .body(String.class);

            if (raw == null || raw.isBlank()) {
                throw new WalletAdapterException("Empty response from wallet lookup: " + ownerType + "/" + ownerRef);
            }

            JsonNode node = objectMapper.readTree(raw);
            JsonNode data = node.has("data") ? node.get("data") : node;
            String walletIdStr = data.path("walletId").asText("");
            if (walletIdStr.isBlank()) {
                throw new WalletAdapterException("No walletId in wallet lookup response for " + ownerRef);
            }

            return UUID.fromString(walletIdStr);
        } catch (WalletAdapterException e) {
            throw e;
        } catch (Exception e) {
            throw new WalletAdapterException(
                    "Wallet lookup failed for " + ownerType + "/" + ownerRef + ": " + e.getMessage(), e);
        }
    }

    private UUID lookupMerchantWallet(UUID tenantId, String providerNumber) {
        try {
            String raw = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/internal/v1/merchants/by-provider")
                            .queryParam("provider_number", providerNumber)
                            .build())
                    .header("X-Tenant-ID", tenantId.toString())
                    .retrieve()
                    .body(String.class);

            if (raw == null || raw.isBlank()) {
                throw new WalletAdapterException("Empty merchant lookup response for provider=" + providerNumber);
            }

            JsonNode node = objectMapper.readTree(raw);
            JsonNode data = node.has("data") ? node.get("data") : node;
            String walletIdStr = data.path("walletId").asText("");
            if (walletIdStr.isBlank()) {
                throw new WalletAdapterException("No walletId for merchant provider=" + providerNumber);
            }

            return UUID.fromString(walletIdStr);
        } catch (WalletAdapterException e) {
            throw e;
        } catch (Exception e) {
            throw new WalletAdapterException(
                    "Merchant wallet lookup failed for provider=" + providerNumber + ": " + e.getMessage(), e);
        }
    }

    private WalletPaymentResult debitWallet(UUID tenantId, UUID walletId, BigDecimal amount,
                                             String reference, String correlationId, String description) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("amount", amount);
        body.put("txnType", "PAYMENT");
        body.put("channel", "MUSHEX");
        body.put("reference", reference);
        body.put("description", description);
        body.put("counterpartyRef", "MUSHEX");
        body.put("counterpartyName", "MUSheX Payment Engine");

        try {
            String raw = restClient.post()
                    .uri("/internal/v1/wallets/{walletId}/debit", walletId)
                    .header("X-Tenant-ID", tenantId.toString())
                    .header("X-Correlation-ID", correlationId != null ? correlationId : "")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);

            return parseTransactionResult(raw, "debitWallet");
        } catch (Exception e) {
            log.error("debitWallet failed: walletId={} amount={}", walletId, amount, e);
            throw new WalletAdapterException("Wallet debit failed: " + e.getMessage(), e);
        }
    }

    private WalletPaymentResult parseTransactionResult(String raw, String operation) {
        try {
            if (raw == null || raw.isBlank()) {
                throw new WalletAdapterException("Empty response from " + operation);
            }
            JsonNode node = objectMapper.readTree(raw);
            JsonNode data = node.has("data") ? node.get("data") : node;
            String txnId = data.path("transactionId").asText(data.path("id").asText(""));
            String status = data.path("status").asText("COMPLETED");
            BigDecimal settledAmount = data.has("amount")
                    ? new BigDecimal(data.path("amount").asText("0"))
                    : BigDecimal.ZERO;

            return new WalletPaymentResult(txnId, status, settledAmount);
        } catch (WalletAdapterException e) {
            throw e;
        } catch (Exception e) {
            throw new WalletAdapterException("Failed to parse " + operation + " response: " + e.getMessage(), e);
        }
    }

    // ── Result / Exception types ───────────────────────────────────────

    public record WalletPaymentResult(String transactionId, String status, BigDecimal amount) {}

    /** Outcome of a payout-batch disbursement: how many credited, and exactly what failed. */
    public record DisbursementResult(int credited, List<FailedDisbursement> failures) {
        public boolean complete() {
            return failures.isEmpty();
        }
    }

    /** A single payout item that could not be credited, with the reason. */
    public record FailedDisbursement(String merchantProviderNumber, BigDecimal amount,
                                     String reference, String reason) {}

    public static class WalletAdapterException extends RuntimeException {
        public WalletAdapterException(String message) {
            super(message);
        }

        public WalletAdapterException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
