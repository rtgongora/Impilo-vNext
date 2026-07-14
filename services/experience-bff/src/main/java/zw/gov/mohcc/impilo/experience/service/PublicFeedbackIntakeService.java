package zw.gov.mohcc.impilo.experience.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import zw.gov.mohcc.impilo.experience.client.KeycloakAdminClient;
import zw.gov.mohcc.impilo.experience.client.RitoServiceClient;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Anonymous public-lane feedback/safety-concern intake (gateway ADR W4
 * {@code gateway-feedback-claim}).
 *
 * <p>Clones the {@link PublicSosIntakeService} abuse controls — per-IP and global fixed
 * rate windows plus body caps — with one deliberate inversion: this lane fails
 * <b>CLOSED</b> when the rate-limit store is unavailable. Feedback is not life-safety;
 * an anonymous write lane without working abuse controls stays shut.</p>
 *
 * <p>The claim code returned on creation is the ONLY key to the case status — it is
 * generated and hashed downstream in rito and never persisted in plaintext anywhere.</p>
 */
@Service
public class PublicFeedbackIntakeService {

    private static final Logger log = LoggerFactory.getLogger(PublicFeedbackIntakeService.class);

    // ── Abuse thresholds (journalled in the ADR public contract registry) ──
    public static final int MAX_REQUESTS_PER_IP = 3;
    public static final int IP_WINDOW_SECONDS = 600;      // 10 minutes
    public static final int MAX_REQUESTS_GLOBAL = 30;
    public static final int GLOBAL_WINDOW_SECONDS = 60;   // 1 minute
    public static final int MAX_TITLE_CHARS = 200;
    public static final int MAX_DESCRIPTION_CHARS = 4000;
    public static final int MAX_CONTACT_CHARS = 255;

    /** Allow-listed public case types (mirrors rito's PublicCaseIntakeService). */
    public static final Set<String> PUBLIC_CASE_TYPES = Set.of("COMPLAINT", "SAFETY_CONCERN");

    private static final String IP_RATE_PREFIX = "gateway:feedback:rl:ip:";
    private static final String GLOBAL_RATE_KEY = "gateway:feedback:rl:global";

    private final StringRedisTemplate redis;
    private final RitoServiceClient rito;
    private final KeycloakAdminClient keycloakAdminClient;

    public PublicFeedbackIntakeService(StringRedisTemplate redis,
                                       RitoServiceClient rito,
                                       KeycloakAdminClient keycloakAdminClient) {
        this.redis = redis;
        this.rito = rito;
        this.keycloakAdminClient = keycloakAdminClient;
    }

    public record FeedbackReceipt(String caseReference, String claimCode, String status) {}

    public record FeedbackStatus(JsonNode status) {}

    public static class FeedbackValidationException extends RuntimeException {
        public FeedbackValidationException(String message) { super(message); }
    }

    public static class FeedbackRateLimitedException extends RuntimeException {
        private final long retryAfterSeconds;
        public FeedbackRateLimitedException(String message, long retryAfterSeconds) {
            super(message);
            this.retryAfterSeconds = retryAfterSeconds;
        }
        public long retryAfterSeconds() { return retryAfterSeconds; }
    }

    public FeedbackReceipt submit(Map<String, Object> body, String clientIp) {
        String caseType = str(body, "caseType");
        if (!PUBLIC_CASE_TYPES.contains(caseType)) {
            throw new FeedbackValidationException("caseType must be one of " + PUBLIC_CASE_TYPES);
        }
        String title = str(body, "title");
        String description = str(body, "description");
        if (title.isBlank() || description.isBlank()) {
            throw new FeedbackValidationException("A short title and a description are required.");
        }
        if (title.length() > MAX_TITLE_CHARS || description.length() > MAX_DESCRIPTION_CHARS
                || str(body, "contact").length() > MAX_CONTACT_CHARS) {
            throw new FeedbackValidationException("One of the fields is too long.");
        }

        // Fail-closed abuse controls: no working limiter -> no anonymous write.
        enforceWindowFailClosed(IP_RATE_PREFIX + ContactOtpService.sha256Hex(clientIp == null ? "unknown" : clientIp),
                MAX_REQUESTS_PER_IP, IP_WINDOW_SECONDS,
                "Too many reports from this connection. Please try again later.");
        enforceWindowFailClosed(GLOBAL_RATE_KEY, MAX_REQUESTS_GLOBAL, GLOBAL_WINDOW_SECONDS,
                "The service is busy right now. Please try again in a few minutes.");

        Map<String, Object> ritoBody = new LinkedHashMap<>();
        ritoBody.put("caseType", caseType);
        ritoBody.put("title", title);
        ritoBody.put("description", description);
        putIfPresent(ritoBody, "facilityId", str(body, "facilityId"));
        putIfPresent(ritoBody, "contact", str(body, "contact"));

        JsonNode created = rito.createPublicCase(ritoBody, resolveBearer());
        if (created == null || !created.hasNonNull("claimCode")) {
            throw new IllegalStateException("public case creation returned no claim code");
        }
        return new FeedbackReceipt(
                created.path("caseReference").asText(null),
                created.path("claimCode").asText(),
                created.path("status").asText(null));
    }

    /** Disclosure-limited status by claim code; null when unknown. */
    public JsonNode statusByClaimCode(String claimCode, String clientIp) {
        if (claimCode == null || claimCode.isBlank() || claimCode.length() > 64
                || !claimCode.matches("[A-Za-z0-9]+")) {
            throw new FeedbackValidationException("Enter the claim code you were given.");
        }
        // Status reads share the same per-IP window: the claim code is a secret and this
        // throttles brute-force probing.
        enforceWindowFailClosed(IP_RATE_PREFIX + ContactOtpService.sha256Hex(clientIp == null ? "unknown" : clientIp),
                MAX_REQUESTS_PER_IP * 2, IP_WINDOW_SECONDS,
                "Too many status checks from this connection. Please try again later.");
        return rito.getPublicCaseStatus(claimCode.trim(), resolveBearer());
    }

    private String resolveBearer() {
        try {
            return keycloakAdminClient.serviceAccountBearer();
        } catch (Exception e) {
            return null;
        }
    }

    private void enforceWindowFailClosed(String key, int max, int windowSeconds, String message) {
        try {
            Long count = redis.opsForValue().increment(key);
            if (count != null && count == 1) {
                redis.expire(key, Duration.ofSeconds(windowSeconds));
            }
            if (count != null && count > max) {
                Long ttl = redis.getExpire(key);
                throw new FeedbackRateLimitedException(message, ttl != null && ttl > 0 ? ttl : windowSeconds);
            }
        } catch (FeedbackRateLimitedException e) {
            throw e;
        } catch (Exception e) {
            // Unlike the SOS lane, feedback fails CLOSED: an anonymous write lane without a
            // working limiter is an open abuse channel, and nothing here is life-safety.
            log.warn("Feedback rate-limit store unavailable, refusing anonymous write (fail-closed): {}",
                    e.getMessage());
            throw new FeedbackRateLimitedException(
                    "We cannot accept anonymous reports right now. Please try again later or sign in.", 300);
        }
    }

    private static void putIfPresent(Map<String, Object> map, String key, String value) {
        if (value != null && !value.isBlank()) {
            map.put(key, value.trim());
        }
    }

    private static String str(Map<String, Object> body, String key) {
        Object v = body.get(key);
        return v == null ? "" : v.toString().trim();
    }
}
