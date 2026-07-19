package zw.gov.mohcc.impilo.shared.biometric;

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
// Registered via BiometricAutoConfiguration — shared-core beans are not
// component-scanned by consumer apps, so this ships as an auto-configured bean
// available in every service that depends on shared-core.

/**
 * The ONE shared biometric-verification seam. Any service that authorizes a
 * payment, a check-in, a pickup or an identity step with a biometric injects
 * THIS instead of hand-rolling an ABIS HTTP call — so real matching reaches
 * every consumer the same governed way (ABIS SoR → core-infra matcher-engine).
 *
 * <p>Care-first / fail-closed: when biometrics are disabled, unconfigured, or the
 * ABIS/engine chain is unreachable, {@link #verify} returns
 * {@link Decision#unavailable()} — a biometric outage never fabricates a MATCH
 * and callers can fall back to their existing factor (OTP, token) without
 * blocking care. Trust headers (tenant/actor/correlation) on the current request
 * are forwarded to ABIS.</p>
 */
public class BiometricVerificationClient {

    private static final Logger log = LoggerFactory.getLogger(BiometricVerificationClient.class);

    private static final List<String> FORWARDED_HEADERS = List.of(
            "X-Tenant-ID", "X-Pod-ID", "X-Request-ID", "X-Correlation-ID",
            "X-Actor-Id", "X-Actor-Type", "X-Purpose-Of-Use", "Authorization");

    private final RestTemplate restTemplate = new RestTemplate();
    private final boolean enabled;
    private final String abisBaseUrl;

    public BiometricVerificationClient(
            @Value("${impilo.biometric.enabled:true}") boolean enabled,
            @Value("${ABIS_BASE_URL:${impilo.biometric.abis-base-url:http://localhost:8186}}") String abisBaseUrl) {
        this.enabled = enabled;
        this.abisBaseUrl = trimSlash(abisBaseUrl);
    }

    /**
     * 1:1 verify a probe template against a subject's enrolled biometric.
     *
     * @param subjectRef opaque ABIS subject reference (NEVER a Health ID)
     * @param modality   FINGERPRINT | FACE | IRIS
     * @param probeTemplateBase64 the probe template (already extracted client/device side)
     */
    public Decision verify(String subjectRef, String modality, String probeTemplateBase64) {
        if (!enabled) {
            return Decision.unavailable();
        }
        if (subjectRef == null || subjectRef.isBlank() || probeTemplateBase64 == null || probeTemplateBase64.isBlank()) {
            return Decision.unavailable();
        }
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("subjectRef", subjectRef);
            body.put("modality", modality);
            body.put("probeBase64", probeTemplateBase64);

            @SuppressWarnings("unchecked")
            Map<String, Object> resp = restTemplate.postForObject(
                    abisBaseUrl + "/v1/abis/verify", new HttpEntity<>(body, forwardHeaders()), Map.class);
            if (resp == null) {
                return Decision.unavailable();
            }
            String result = String.valueOf(resp.getOrDefault("result", "UNAVAILABLE"));
            double confidence = resp.get("confidence") instanceof Number n ? n.doubleValue() : 0.0;
            return new Decision(result, confidence);
        } catch (RuntimeException e) {
            log.warn("Biometric verify failed (fail-closed): {}", e.getMessage());
            return Decision.unavailable();
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

    private static String trimSlash(String s) {
        return (s != null && s.endsWith("/")) ? s.substring(0, s.length() - 1) : s;
    }

    /** result ∈ {MATCH, NO_MATCH, UNAVAILABLE, NO_REFERENCE}; confidence 0..1. */
    public record Decision(String result, double confidence) {
        public boolean isMatch() {
            return "MATCH".equals(result);
        }

        public static Decision unavailable() {
            return new Decision("UNAVAILABLE", 0.0);
        }
    }
}
