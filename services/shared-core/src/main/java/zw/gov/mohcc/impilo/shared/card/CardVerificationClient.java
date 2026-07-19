package zw.gov.mohcc.impilo.shared.card;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
// Registered via CardAutoConfiguration — shared-core beans are not
// component-scanned by consumer apps, so this ships as an auto-configured bean
// available in every service that depends on shared-core.

/**
 * The ONE shared card-resolve seam. Any service that acts on a presented SMART
 * card — identity sign-in, a card payment, a card check-in, a break-glass PHR
 * read — injects THIS instead of hand-rolling a VITO call, so a card presentation
 * (card number, scanned vito QR, or printed-card assertion) resolves the same
 * governed way through VITO's {@code POST /v1/cards/resolve}.
 *
 * <p>Fail-closed: when card resolution is disabled, unconfigured, or VITO is
 * unreachable, {@link #resolve} returns {@link Resolution#unresolved()} — a
 * presentation is never fabricated into an identity. Trust headers
 * (tenant/actor/correlation) on the current request are forwarded to VITO.</p>
 */
public class CardVerificationClient {

    private static final Logger log = LoggerFactory.getLogger(CardVerificationClient.class);

    private static final List<String> FORWARDED_HEADERS = List.of(
            "X-Tenant-ID", "X-Pod-ID", "X-Request-ID", "X-Correlation-ID",
            "X-Actor-Id", "X-Actor-Type", "X-Purpose-Of-Use", "Authorization");

    private final RestTemplate restTemplate = new RestTemplate();
    private final boolean enabled;
    private final String vitoBaseUrl;

    public CardVerificationClient(
            @Value("${impilo.card.enabled:true}") boolean enabled,
            @Value("${VITO_BASE_URL:${impilo.card.vito-base-url:http://localhost:8082}}") String vitoBaseUrl) {
        this.enabled = enabled;
        this.vitoBaseUrl = trimSlash(vitoBaseUrl);
    }

    /** Resolve a card number. */
    public Resolution resolveByCardNumber(String cardNumber) {
        return resolve(cardNumber, null, null);
    }

    /** Resolve a scanned vito QR (compact JWS). */
    public Resolution resolveByQrToken(String qrToken) {
        return resolve(null, qrToken, null);
    }

    /** Resolve a scanned printed-card assertion (Ed25519-signed JSON). */
    public Resolution resolveByAssertion(String cardAssertion) {
        return resolve(null, null, cardAssertion);
    }

    /**
     * Resolve a card presentation — exactly one of the three is expected.
     * @return the resolved identity + card status, or {@link Resolution#unresolved()}.
     */
    public Resolution resolve(String cardNumber, String qrToken, String cardAssertion) {
        if (!enabled) {
            return Resolution.unresolved();
        }
        boolean any = (cardNumber != null && !cardNumber.isBlank())
                || (qrToken != null && !qrToken.isBlank())
                || (cardAssertion != null && !cardAssertion.isBlank());
        if (!any) {
            return Resolution.unresolved();
        }
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            if (cardNumber != null) body.put("cardNumber", cardNumber);
            if (qrToken != null) body.put("qrToken", qrToken);
            if (cardAssertion != null) body.put("cardAssertion", cardAssertion);

            @SuppressWarnings("unchecked")
            Map<String, Object> resp = restTemplate.postForObject(
                    vitoBaseUrl + "/v1/cards/resolve", new HttpEntity<>(body, forwardHeaders()), Map.class);
            if (resp == null) {
                return Resolution.unresolved();
            }
            // VITO wraps in ApiResponse { data: {...} }; accept flat too.
            Object dataObj = resp.get("data");
            @SuppressWarnings("unchecked")
            Map<String, Object> data = dataObj instanceof Map ? (Map<String, Object>) dataObj : resp;
            String healthId = str(data.get("healthId"));
            if (healthId == null) {
                return Resolution.unresolved();
            }
            return new Resolution(true, healthId, str(data.get("cpid")), str(data.get("cardNumber")),
                    str(data.get("cardStatus")), str(data.get("cardForm")), str(data.get("resolvedVia")));
        } catch (RuntimeException e) {
            log.warn("Card resolve failed (fail-closed): {}", e.getMessage());
            return Resolution.unresolved();
        }
    }

    private HttpHeaders forwardHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            HttpServletRequest inbound = attrs.getRequest();
            for (String h : FORWARDED_HEADERS) {
                String v = inbound.getHeader(h);
                if (v != null && !v.isBlank()) {
                    headers.set(h, v);
                }
            }
        }
        return headers;
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private static String trimSlash(String s) {
        return (s != null && s.endsWith("/")) ? s.substring(0, s.length() - 1) : s;
    }

    /**
     * A resolved card presentation. cpid == healthId (CPID/health_id collapse).
     * {@code resolvedVia} ∈ {CARD_NUMBER, VITO_QR, PRINTED_ASSERTION}.
     */
    public record Resolution(
            boolean resolved,
            String healthId,
            String cpid,
            String cardNumber,
            String cardStatus,
            String cardForm,
            String resolvedVia) {

        public boolean isActive() {
            return resolved && "ACTIVE".equals(cardStatus);
        }

        public static Resolution unresolved() {
            return new Resolution(false, null, null, null, null, null, null);
        }
    }
}
