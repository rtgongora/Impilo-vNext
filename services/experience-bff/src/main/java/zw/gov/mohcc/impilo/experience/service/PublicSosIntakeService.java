package zw.gov.mohcc.impilo.experience.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import zw.gov.mohcc.impilo.experience.client.DaidzaiServiceClient;
import zw.gov.mohcc.impilo.experience.client.KeycloakAdminClient;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Anonymous public-lane emergency SOS intake (gateway ADR §5 anonymous-write exception, PD-3).
 *
 * <p>A guest request is captured immediately and never gated on sign-in, but this is an anonymous
 * WRITE, so it carries its own abuse controls before it reaches daidzai:</p>
 * <ul>
 *   <li><b>Callback required + normalized</b> — the callback number is mandatory (a blank/invalid
 *       number is a 400 and never reaches daidzai) and normalized to E.164 via the same
 *       {@link ContactOtpService} normalization the R1 lane uses.</li>
 *   <li><b>Rate limits</b> — per-IP fixed window ({@value #MAX_REQUESTS_PER_IP}/{@value
 *       #IP_WINDOW_SECONDS}s) and a global endpoint window ({@value #MAX_REQUESTS_GLOBAL}/{@value
 *       #GLOBAL_WINDOW_SECONDS}s), mirroring {@code ContactOtpService.enforceWindow}. A breach is a
 *       429 with a Retry-After hint. Redis unavailable fails <em>open</em> here (unlike the OTP
 *       lane): a life-safety request must not be lost because a rate-limiter is down.</li>
 *   <li><b>Body caps</b> — free-text fields are truncated to bounded lengths so an anonymous caller
 *       cannot post unbounded payloads.</li>
 * </ul>
 *
 * <p>Daidzai owns the emergency truth and applies the callback dispatch gate; this service composes
 * and never fabricates a dispatch.</p>
 */
@Service
public class PublicSosIntakeService {

    private static final Logger log = LoggerFactory.getLogger(PublicSosIntakeService.class);

    // ── Abuse thresholds (journalled in the ADR public contract registry) ──
    public static final int MAX_REQUESTS_PER_IP = 5;
    public static final int IP_WINDOW_SECONDS = 600;      // 10 minutes
    public static final int MAX_REQUESTS_GLOBAL = 60;
    public static final int GLOBAL_WINDOW_SECONDS = 60;   // 1 minute
    public static final int MAX_DESCRIPTION_CHARS = 2000;
    public static final int MAX_LOCATION_CHARS = 512;

    private static final String IP_RATE_PREFIX = "gateway:sos:rl:ip:";
    private static final String GLOBAL_RATE_KEY = "gateway:sos:rl:global";

    private final StringRedisTemplate redis;
    private final DaidzaiServiceClient daidzai;
    private final ContactOtpService contactOtpService;
    private final KeycloakAdminClient keycloakAdminClient;

    public PublicSosIntakeService(StringRedisTemplate redis,
                                  DaidzaiServiceClient daidzai,
                                  ContactOtpService contactOtpService,
                                  KeycloakAdminClient keycloakAdminClient) {
        this.redis = redis;
        this.daidzai = daidzai;
        this.contactOtpService = contactOtpService;
        this.keycloakAdminClient = keycloakAdminClient;
    }

    /** Outcome of a captured SOS request (public-safe: reference + reassurance message only). */
    public record SosReceipt(String requestReference, String message) {}

    /** Client error: missing/invalid callback or oversized field. Maps to HTTP 400. */
    public static class SosValidationException extends RuntimeException {
        public SosValidationException(String message) { super(message); }
    }

    /** Too many SOS submissions — carries a retry-after hint. Maps to HTTP 429. */
    public static class SosRateLimitedException extends RuntimeException {
        private final long retryAfterSeconds;
        public SosRateLimitedException(String message, long retryAfterSeconds) {
            super(message);
            this.retryAfterSeconds = retryAfterSeconds;
        }
        public long retryAfterSeconds() { return retryAfterSeconds; }
    }

    /**
     * Capture an anonymous SOS request. Enforces abuse controls, normalizes the callback, then
     * calls daidzai's SOS intake as a PUBLIC_ANONYMOUS request (daidzai holds it AWAITING_CALLBACK).
     */
    public SosReceipt submit(Map<String, Object> body, String clientIp) {
        String rawCallback = str(body, "callbackNumber");
        if (rawCallback.isBlank()) {
            throw new SosValidationException("A callback number is required so a responder can reach you.");
        }
        String callback;
        try {
            // Reuse the R1 lane normalization: E.164, rejects malformed numbers and email-shaped input.
            callback = contactOtpService.normalize("PHONE", rawCallback).normalizedValue();
        } catch (ContactOtpService.InvalidContactException e) {
            throw new SosValidationException("Enter a valid phone number we can call you back on.");
        }

        // Abuse controls: per-IP then global fixed windows. Fail open on Redis errors (life-safety).
        enforceWindow(IP_RATE_PREFIX + ContactOtpService.sha256Hex(clientIp == null ? "unknown" : clientIp),
                MAX_REQUESTS_PER_IP, IP_WINDOW_SECONDS,
                "Too many emergency requests from this connection. If this is a real emergency, call the numbers shown.");
        enforceWindow(GLOBAL_RATE_KEY, MAX_REQUESTS_GLOBAL, GLOBAL_WINDOW_SECONDS,
                "The service is very busy right now. If this is a real emergency, call the numbers shown.");

        Map<String, Object> daidzaiBody = new LinkedHashMap<>();
        daidzaiBody.put("requesterType", "PUBLIC_ANONYMOUS");
        daidzaiBody.put("subjectIdentityMode", "ANONYMOUS");
        daidzaiBody.put("channel", "PUBLIC_WEB");
        daidzaiBody.put("callbackNumber", callback);
        daidzaiBody.put("emergencyCategory", firstNonBlank(str(body, "emergencyCategory"), "MEDICAL"));
        putIfPresent(daidzaiBody, "severity", str(body, "severity"));
        putIfPresent(daidzaiBody, "description", cap(str(body, "description"), MAX_DESCRIPTION_CHARS));
        putIfPresent(daidzaiBody, "locationDescription", cap(str(body, "locationDescription"), MAX_LOCATION_CHARS));
        putIfPresent(daidzaiBody, "lat", str(body, "lat"));
        putIfPresent(daidzaiBody, "lng", str(body, "lng"));

        JsonNode created = daidzai.createPublicSosRequest(daidzaiBody, resolveBearer());
        String reference = created != null && created.hasNonNull("requestReference")
                ? created.get("requestReference").asText() : null;
        log.info("Public SOS captured: reference={} category={}", reference, daidzaiBody.get("emergencyCategory"));
        return new SosReceipt(reference,
                "Your request for help has been received. A responder will call you back on the number "
                        + "you provided to confirm before help is sent. If this is life-threatening, call "
                        + "the emergency numbers shown now.");
    }

    private String resolveBearer() {
        try {
            return keycloakAdminClient.serviceAccountBearer();
        } catch (Exception e) {
            return null;
        }
    }

    private void enforceWindow(String key, int max, int windowSeconds, String message) {
        try {
            Long count = redis.opsForValue().increment(key);
            if (count != null && count == 1) {
                redis.expire(key, Duration.ofSeconds(windowSeconds));
            }
            if (count != null && count > max) {
                Long ttl = redis.getExpire(key);
                throw new SosRateLimitedException(message, ttl != null && ttl > 0 ? ttl : windowSeconds);
            }
        } catch (SosRateLimitedException e) {
            throw e;
        } catch (Exception e) {
            // A life-safety request must not be lost because the rate-limiter store is unavailable.
            log.warn("SOS rate-limit store unavailable, allowing request (fail-open): {}", e.getMessage());
        }
    }

    private static void putIfPresent(Map<String, Object> map, String key, String value) {
        if (value != null && !value.isBlank()) {
            map.put(key, value.trim());
        }
    }

    private static String cap(String value, int max) {
        if (value == null) return null;
        return value.length() <= max ? value : value.substring(0, max);
    }

    private static String firstNonBlank(String a, String b) {
        return a != null && !a.isBlank() ? a.trim() : b;
    }

    private static String str(Map<String, Object> body, String key) {
        Object v = body.get(key);
        return v == null ? "" : v.toString().trim();
    }
}
