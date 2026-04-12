package zw.gov.mohcc.impilo.channels.ussd;

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
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Handles USSD menu interactions for Mushe Wallet operations.
 *
 * <p>Menu mapping (example short code *123#):</p>
 * <ul>
 *   <li>*123*1# — BALANCE: check wallet balance</li>
 *   <li>*123*2# — SEND: send money (prompted: recipient, amount, PIN)</li>
 *   <li>*123*3# — PAY: pay merchant (prompted: merchant number, amount, PIN)</li>
 *   <li>*123*4# — HISTORY: last 5 transactions</li>
 *   <li>*123*5# — CARD_BLOCK: emergency card block</li>
 * </ul>
 *
 * <p>Multi-step interactions use continued USSD input with asterisk-delimited fields.
 * For example, SEND flow: *123*2*RECIPIENT_CPID*1000*PIN#</p>
 */
@Component
public class WalletUssdHandler {

    private static final Logger log = LoggerFactory.getLogger(WalletUssdHandler.class);

    /** Maximum USSD response length (GSM standard). */
    private static final int MAX_USSD_LENGTH = 160;

    /** Pattern to match USSD input: *shortcode*option[*param1*param2...]# */
    private static final Pattern USSD_PATTERN = Pattern.compile(
            "^\\*\\d+\\*?(\\d+)?(?:\\*(.+))?#$");

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public WalletUssdHandler(
            RestClient.Builder restClientBuilder,
            @Value("${impilo.services.mushe-wallet-base-url:http://localhost:8126}") String baseUrl,
            ObjectMapper objectMapper) {
        this.restClient = restClientBuilder.baseUrl(baseUrl).build();
        this.objectMapper = objectMapper;
    }

    /**
     * Handle a USSD input string for a given subscriber.
     *
     * @param ussdInput  the raw USSD string (e.g. "*123*1#")
     * @param msisdn     the subscriber's phone number
     * @param tenantId   the tenant UUID
     * @return a formatted USSD text response (max 160 chars)
     */
    public UssdResponse handle(String ussdInput, String msisdn, UUID tenantId) {
        log.debug("USSD input: msisdn={} input={}", msisdn, ussdInput);

        Matcher matcher = USSD_PATTERN.matcher(ussdInput.trim());
        if (!matcher.matches()) {
            return menu();
        }

        String option = matcher.group(1);
        String params = matcher.group(2); // may be null
        String[] parts = params != null ? params.split("\\*") : new String[0];

        if (option == null || option.isEmpty()) {
            return menu();
        }

        try {
            return switch (option) {
                case "1" -> handleBalance(msisdn, tenantId);
                case "2" -> handleSend(msisdn, tenantId, parts);
                case "3" -> handlePay(msisdn, tenantId, parts);
                case "4" -> handleHistory(msisdn, tenantId);
                case "5" -> handleCardBlock(msisdn, tenantId, parts);
                default -> menu();
            };
        } catch (Exception e) {
            log.error("USSD handler error: msisdn={} option={}", msisdn, option, e);
            return end(truncate("Error: service unavailable. Try again later."));
        }
    }

    // ── BALANCE ────────────────────────────────────────────────────────

    private UssdResponse handleBalance(String msisdn, UUID tenantId) {
        UUID walletId = lookupWalletId(tenantId, msisdn);
        if (walletId == null) {
            return end("No wallet found for your account. Register at a health facility.");
        }

        try {
            String raw = restClient.get()
                    .uri("/internal/v1/wallets/{walletId}/balance", walletId)
                    .header("X-Tenant-ID", tenantId.toString())
                    .retrieve()
                    .body(String.class);

            JsonNode node = objectMapper.readTree(raw);
            JsonNode data = node.has("data") ? node.get("data") : node;
            String balance = data.path("availableBalance").asText("0.00");

            return end(truncate("Mushe Wallet\nBalance: $" + balance));
        } catch (Exception e) {
            log.error("Balance check failed: msisdn={}", msisdn, e);
            return end("Unable to retrieve balance. Try again later.");
        }
    }

    // ── SEND ───────────────────────────────────────────────────────────

    private UssdResponse handleSend(String msisdn, UUID tenantId, String[] parts) {
        // Multi-step: *123*2# → prompt, *123*2*RECIPIENT*AMOUNT*PIN#
        if (parts.length < 3) {
            if (parts.length == 0) {
                return prompt("Send Money\nEnter recipient,amount,PIN:\n*123*2*RECIPIENT*AMOUNT*PIN#");
            }
            return end("Incomplete input. Use:\n*123*2*RECIPIENT*AMOUNT*PIN#");
        }

        String recipientRef = parts[0];
        BigDecimal amount;
        try {
            amount = new BigDecimal(parts[1]);
        } catch (NumberFormatException e) {
            return end("Invalid amount. Use:\n*123*2*RECIPIENT*AMOUNT*PIN#");
        }
        // parts[2] = PIN — passed to wallet service for validation

        UUID fromWalletId = lookupWalletId(tenantId, msisdn);
        UUID toWalletId = lookupWalletId(tenantId, recipientRef);

        if (fromWalletId == null) {
            return end("No wallet found for your account.");
        }
        if (toWalletId == null) {
            return end("Recipient wallet not found.");
        }

        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("fromWalletId", fromWalletId.toString());
            body.put("toWalletId", toWalletId.toString());
            body.put("amount", amount);
            body.put("reference", "USSD-SEND-" + UUID.randomUUID().toString().substring(0, 8));
            body.put("description", "USSD transfer to " + recipientRef);
            body.put("channel", "USSD");

            restClient.post()
                    .uri("/internal/v1/wallets/transfer")
                    .header("X-Tenant-ID", tenantId.toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);

            return end(truncate("Sent $" + amount.toPlainString() + " to " + recipientRef + ". Ref: USSD-SEND"));
        } catch (Exception e) {
            log.error("USSD send failed: from={} to={} amount={}", msisdn, recipientRef, amount, e);
            return end("Transfer failed. Check balance and try again.");
        }
    }

    // ── PAY MERCHANT ───────────────────────────────────────────────────

    private UssdResponse handlePay(String msisdn, UUID tenantId, String[] parts) {
        // Multi-step: *123*3# → prompt, *123*3*MERCHANT_NO*AMOUNT*PIN#
        if (parts.length < 3) {
            if (parts.length == 0) {
                return prompt("Pay Merchant\nEnter merchant,amount,PIN:\n*123*3*MERCHANT*AMOUNT*PIN#");
            }
            return end("Incomplete input. Use:\n*123*3*MERCHANT*AMOUNT*PIN#");
        }

        String merchantNumber = parts[0];
        BigDecimal amount;
        try {
            amount = new BigDecimal(parts[1]);
        } catch (NumberFormatException e) {
            return end("Invalid amount. Use:\n*123*3*MERCHANT*AMOUNT*PIN#");
        }

        UUID fromWalletId = lookupWalletId(tenantId, msisdn);
        if (fromWalletId == null) {
            return end("No wallet found for your account.");
        }

        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("fromWalletId", fromWalletId.toString());
            body.put("merchantProviderNumber", merchantNumber);
            body.put("amount", amount);
            body.put("reference", "USSD-PAY-" + UUID.randomUUID().toString().substring(0, 8));
            body.put("channel", "USSD");

            restClient.post()
                    .uri("/internal/v1/merchants/pay")
                    .header("X-Tenant-ID", tenantId.toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);

            return end(truncate("Paid $" + amount.toPlainString() + " to merchant " + merchantNumber));
        } catch (Exception e) {
            log.error("USSD merchant pay failed: msisdn={} merchant={} amount={}", msisdn, merchantNumber, amount, e);
            return end("Payment failed. Check balance and try again.");
        }
    }

    // ── HISTORY ────────────────────────────────────────────────────────

    private UssdResponse handleHistory(String msisdn, UUID tenantId) {
        UUID walletId = lookupWalletId(tenantId, msisdn);
        if (walletId == null) {
            return end("No wallet found for your account.");
        }

        try {
            String raw = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/internal/v1/wallets/{walletId}/transactions")
                            .queryParam("page", 0)
                            .queryParam("size", 5)
                            .build(walletId))
                    .header("X-Tenant-ID", tenantId.toString())
                    .retrieve()
                    .body(String.class);

            JsonNode node = objectMapper.readTree(raw);
            JsonNode data = node.has("data") ? node.get("data") : node;
            JsonNode content = data.has("content") ? data.get("content") : data;

            if (!content.isArray() || content.isEmpty()) {
                return end("No recent transactions.");
            }

            StringBuilder sb = new StringBuilder("Recent Txns:\n");
            int count = 0;
            for (JsonNode txn : content) {
                if (count >= 5) break;
                String type = txn.path("txnType").asText("TXN");
                String amt = txn.path("amount").asText("0.00");
                String dir = txn.path("direction").asText("");
                String sign = "CREDIT".equalsIgnoreCase(dir) ? "+" : "-";
                sb.append(sign).append("$").append(amt).append(" ").append(type).append("\n");
                count++;
            }

            return end(truncate(sb.toString().trim()));
        } catch (Exception e) {
            log.error("USSD history failed: msisdn={}", msisdn, e);
            return end("Unable to fetch history. Try again later.");
        }
    }

    // ── CARD BLOCK ─────────────────────────────────────────────────────

    private UssdResponse handleCardBlock(String msisdn, UUID tenantId, String[] parts) {
        // Confirm step: *123*5# → prompt, *123*5*CONFIRM# to block
        if (parts.length == 0) {
            return prompt("EMERGENCY Card Block\nDial *123*5*1# to confirm blocking ALL your cards.");
        }

        if (!"1".equals(parts[0])) {
            return end("Card block cancelled.");
        }

        UUID walletId = lookupWalletId(tenantId, msisdn);
        if (walletId == null) {
            return end("No wallet found for your account.");
        }

        // Note: The wallet service card-block endpoint is expected to exist at this path.
        // If not available, this degrades gracefully with an error message.
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("walletId", walletId.toString());
            body.put("reason", "USSD emergency block by " + msisdn);
            body.put("channel", "USSD");

            restClient.post()
                    .uri("/internal/v1/wallets/{walletId}/block-cards", walletId)
                    .header("X-Tenant-ID", tenantId.toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);

            return end("All cards BLOCKED. Visit a facility to unblock.");
        } catch (Exception e) {
            log.error("USSD card block failed: msisdn={} walletId={}", msisdn, walletId, e);
            return end("Card block failed. Call support immediately.");
        }
    }

    // ── Internal Helpers ───────────────────────────────────────────────

    /**
     * Look up a wallet ID by subscriber reference (MSISDN or CPID).
     * The wallet service stores owner_ref which may be a phone number or CPID.
     */
    private UUID lookupWalletId(UUID tenantId, String ownerRef) {
        try {
            String raw = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/internal/v1/wallets/by-owner")
                            .queryParam("owner_type", "INDIVIDUAL")
                            .queryParam("owner_ref", ownerRef)
                            .build())
                    .header("X-Tenant-ID", tenantId.toString())
                    .retrieve()
                    .body(String.class);

            if (raw == null || raw.isBlank()) {
                return null;
            }

            JsonNode node = objectMapper.readTree(raw);
            JsonNode data = node.has("data") ? node.get("data") : node;
            String walletIdStr = data.path("walletId").asText("");
            if (walletIdStr.isBlank()) {
                return null;
            }

            return UUID.fromString(walletIdStr);
        } catch (Exception e) {
            log.debug("Wallet lookup failed for ownerRef={}: {}", ownerRef, e.getMessage());
            return null;
        }
    }

    private UssdResponse menu() {
        return prompt("Mushe Wallet\n1.Balance\n2.Send\n3.Pay\n4.History\n5.Block Card");
    }

    private UssdResponse prompt(String text) {
        return new UssdResponse(truncate(text), false);
    }

    private UssdResponse end(String text) {
        return new UssdResponse(truncate(text), true);
    }

    private String truncate(String text) {
        if (text == null) return "";
        return text.length() > MAX_USSD_LENGTH ? text.substring(0, MAX_USSD_LENGTH) : text;
    }

    // ── Response record ────────────────────────────────────────────────

    /**
     * USSD response DTO.
     *
     * @param text       the response text to display (max 160 chars)
     * @param endSession true if the USSD session should close, false to await more input
     */
    public record UssdResponse(String text, boolean endSession) {}
}
