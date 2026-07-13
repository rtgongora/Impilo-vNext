package zw.gov.mohcc.impilo.mushex.integration.paynow;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Real Paynow (Webdev, Zimbabwe) wire-protocol client — the aggregator surface
 * that reaches EcoCash, OneMoney, InnBucks, ZimSwitch and Visa/Mastercard
 * behind ONE integration.
 *
 * <p>Protocol (Paynow's published API):
 * <ul>
 *   <li>Initiate: form-encoded POST to {@code /interface/initiatetransaction}
 *       (browser redirect flow) or {@code /interface/remotetransaction}
 *       (express/mobile push with {@code method} + {@code phone}).</li>
 *   <li>Integrity: every message carries a {@code hash} — uppercase SHA-512 hex
 *       of the concatenated field VALUES (in field order) + the integration key.
 *       Responses and result posts are verified with the same scheme.</li>
 *   <li>Status: GET the returned {@code pollurl}; Paynow also POSTs the same
 *       fields to our {@code resulturl}.</li>
 * </ul>
 *
 * <p>This client holds real protocol logic and is exercised in the money-rails
 * rig against a protocol simulator; against the real endpoint it only needs
 * credentials (integration id + key) — no code change.</p>
 */
public class PaynowClient {

    private static final Logger log = LoggerFactory.getLogger(PaynowClient.class);

    private final RestClient restClient;
    private final String integrationId;
    private final String integrationKey;
    private final String resultUrl;
    private final String returnUrl;

    public PaynowClient(RestClient.Builder restClientBuilder,
                        String baseUrl,
                        String integrationId,
                        String integrationKey,
                        String resultUrl,
                        String returnUrl) {
        this.restClient = restClientBuilder.baseUrl(baseUrl).build();
        this.integrationId = integrationId;
        this.integrationKey = integrationKey;
        this.resultUrl = resultUrl;
        this.returnUrl = returnUrl;
    }

    /** Outcome of an initiate call. */
    public record InitiateResult(boolean ok, String browserUrl, String pollUrl, String error) {}

    /** Outcome of a status poll / result post. */
    public record StatusResult(String status, String reference, String paynowReference,
                               BigDecimal amount, boolean hashValid) {
        public boolean paid() {
            return hashValid && ("Paid".equalsIgnoreCase(status) || "Awaiting Delivery".equalsIgnoreCase(status));
        }
        public boolean cancelled() {
            return "Cancelled".equalsIgnoreCase(status);
        }
    }

    /** Standard (browser-redirect) transaction — cards, bank, wallet-agnostic checkout. */
    public InitiateResult initiateTransaction(String reference, BigDecimal amount, String additionalInfo) {
        Map<String, String> fields = baseFields(reference, amount, additionalInfo);
        fields.put("hash", hash(fields.values()));
        return postInitiate("/interface/initiatetransaction", fields);
    }

    /**
     * Express checkout — mobile-money USSD push straight to the customer's
     * handset ({@code method}: ecocash | onemoney | innbucks; {@code phone}:
     * the customer's MSISDN).
     */
    public InitiateResult initiateExpressCheckout(String reference, BigDecimal amount,
                                                  String additionalInfo, String method, String phone) {
        Map<String, String> fields = baseFields(reference, amount, additionalInfo);
        fields.put("method", method.toLowerCase(Locale.ROOT));
        fields.put("phone", phone);
        fields.put("hash", hash(fields.values()));
        return postInitiate("/interface/remotetransaction", fields);
    }

    /** Poll the transaction's pollurl for its current status. */
    public StatusResult pollStatus(String pollUrl) {
        String raw = restClient.get().uri(pollUrl).retrieve().body(String.class);
        return parseStatus(raw != null ? raw : "");
    }

    /** Parse + hash-verify a status message (poll response or resulturl post body). */
    public StatusResult parseStatus(String rawUrlEncoded) {
        Map<String, String> fields = parseForm(rawUrlEncoded);
        boolean hashValid = verifyHash(fields);
        BigDecimal amount = null;
        try {
            if (fields.get("amount") != null) {
                amount = new BigDecimal(fields.get("amount"));
            }
        } catch (NumberFormatException ignored) {
            // leave null; caller treats unparseable amounts as absent
        }
        return new StatusResult(
                fields.getOrDefault("status", "Unknown"),
                fields.get("reference"),
                fields.get("paynowreference"),
                amount,
                hashValid);
    }

    /**
     * Verify the {@code hash} field of a Paynow message: SHA-512 over every
     * field VALUE (in order, excluding the hash itself) + integration key.
     */
    public boolean verifyHash(Map<String, String> fields) {
        String presented = fields.get("hash");
        if (presented == null || presented.isBlank()) {
            return false;
        }
        Map<String, String> withoutHash = new LinkedHashMap<>(fields);
        withoutHash.remove("hash");
        String expected = hash(withoutHash.values());
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                presented.toUpperCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8));
    }

    /** Uppercase SHA-512 hex of the concatenated values + integration key. */
    public String hash(Iterable<String> values) {
        StringBuilder sb = new StringBuilder();
        for (String v : values) {
            sb.append(v != null ? v : "");
        }
        sb.append(integrationKey);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-512");
            byte[] bytes = digest.digest(sb.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                hex.append(String.format("%02X", b));
            }
            return hex.toString();
        } catch (Exception e) {
            throw new IllegalStateException("SHA-512 unavailable", e);
        }
    }

    // ── internals ────────────────────────────────────────────────────────────

    private Map<String, String> baseFields(String reference, BigDecimal amount, String additionalInfo) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("id", integrationId);
        fields.put("reference", reference);
        fields.put("amount", amount.toPlainString());
        fields.put("additionalinfo", additionalInfo != null ? additionalInfo : "");
        fields.put("returnurl", returnUrl != null ? returnUrl : "");
        fields.put("resulturl", resultUrl != null ? resultUrl : "");
        fields.put("status", "Message");
        return fields;
    }

    private InitiateResult postInitiate(String path, Map<String, String> fields) {
        String body = encodeForm(fields);
        String raw = restClient.post()
                .uri(path)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(body)
                .retrieve()
                .body(String.class);
        Map<String, String> resp = parseForm(raw != null ? raw : "");
        String status = resp.getOrDefault("status", "Error");
        if (!"Ok".equalsIgnoreCase(status)) {
            String error = resp.getOrDefault("error", "Paynow returned status " + status);
            log.warn("Paynow initiate failed: {}", error);
            return new InitiateResult(false, null, null, error);
        }
        if (!verifyHash(resp)) {
            log.error("Paynow initiate response FAILED hash verification — rejecting");
            return new InitiateResult(false, null, null, "response hash verification failed");
        }
        return new InitiateResult(true, resp.get("browserurl"), resp.get("pollurl"), null);
    }

    static String encodeForm(Map<String, String> fields) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : fields.entrySet()) {
            if (sb.length() > 0) sb.append('&');
            sb.append(URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8))
                    .append('=')
                    .append(URLEncoder.encode(e.getValue() != null ? e.getValue() : "", StandardCharsets.UTF_8));
        }
        return sb.toString();
    }

    public static Map<String, String> parseForm(String raw) {
        Map<String, String> out = new LinkedHashMap<>();
        for (String pair : raw.split("&")) {
            if (pair.isBlank()) continue;
            int eq = pair.indexOf('=');
            String k = eq >= 0 ? pair.substring(0, eq) : pair;
            String v = eq >= 0 ? pair.substring(eq + 1) : "";
            out.put(URLDecoder.decode(k, StandardCharsets.UTF_8).toLowerCase(Locale.ROOT),
                    URLDecoder.decode(v, StandardCharsets.UTF_8));
        }
        return out;
    }
}
