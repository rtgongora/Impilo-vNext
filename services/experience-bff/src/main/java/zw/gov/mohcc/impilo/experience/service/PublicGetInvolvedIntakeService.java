package zw.gov.mohcc.impilo.experience.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import zw.gov.mohcc.impilo.experience.client.KeycloakAdminClient;
import zw.gov.mohcc.impilo.experience.client.ParticipationServiceClient;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Anonymous public-lane citizen "Get Involved" co-design intake (gateway ADR W4
 * {@code gateway-get-involved-claim}).
 *
 * <p>Clones the {@link PublicFeedbackIntakeService} abuse controls — per-IP and global fixed
 * rate windows plus body caps — and, like feedback, fails <b>CLOSED</b> when the rate-limit
 * store is unavailable: co-design is not life-safety, so an anonymous write lane without
 * working abuse controls stays shut. The three writes here are contribution submit, board
 * support, and cohort enrol. Reads (board, cohorts, claim-code status) are proxied through
 * with no write-side rate cost, though the status read shares a per-IP window to throttle
 * brute-force probing.</p>
 *
 * <p>This is the "something could be better" surface — distinct from feedback's "something
 * went wrong". The claim code returned on submit is the ONLY key to the contribution status;
 * it is generated and hashed downstream in participation and never persisted in plaintext.</p>
 */
@Service
public class PublicGetInvolvedIntakeService {

    private static final Logger log = LoggerFactory.getLogger(PublicGetInvolvedIntakeService.class);

    // ── Abuse thresholds (journalled in the ADR public contract registry) ──
    public static final int MAX_REQUESTS_PER_IP = 5;
    public static final int IP_WINDOW_SECONDS = 600;      // 10 minutes
    public static final int MAX_REQUESTS_GLOBAL = 60;
    public static final int GLOBAL_WINDOW_SECONDS = 60;   // 1 minute
    public static final int MAX_TITLE_CHARS = 512;
    public static final int MAX_TEXT_CHARS = 8000;
    public static final int MAX_SHORT_CHARS = 255;

    /** Allow-listed public contribution types (mirrors participation ContributionType.PUBLIC_TYPES). */
    public static final Set<String> PUBLIC_TYPES = Set.of("IDEA", "EXPERIENCE");

    private static final String IP_RATE_PREFIX = "gateway:getinvolved:rl:ip:";
    private static final String GLOBAL_RATE_KEY = "gateway:getinvolved:rl:global";

    private final StringRedisTemplate redis;
    private final ParticipationServiceClient participation;
    private final KeycloakAdminClient keycloakAdminClient;

    public PublicGetInvolvedIntakeService(StringRedisTemplate redis,
                                          ParticipationServiceClient participation,
                                          KeycloakAdminClient keycloakAdminClient) {
        this.redis = redis;
        this.participation = participation;
        this.keycloakAdminClient = keycloakAdminClient;
    }

    public record ContributionReceipt(String reference, String claimCode, String status) {}

    public static class ValidationException extends RuntimeException {
        public ValidationException(String message) { super(message); }
    }

    public static class RateLimitedException extends RuntimeException {
        private final long retryAfterSeconds;
        public RateLimitedException(String message, long retryAfterSeconds) {
            super(message);
            this.retryAfterSeconds = retryAfterSeconds;
        }
        public long retryAfterSeconds() { return retryAfterSeconds; }
    }

    // ── Writes ────────────────────────────────────────────────────────
    public ContributionReceipt submit(Map<String, Object> body, String clientIp) {
        String type = str(body, "type");
        if (type.isBlank()) {
            type = "IDEA";
        }
        if (!PUBLIC_TYPES.contains(type)) {
            throw new ValidationException("type must be one of " + PUBLIC_TYPES);
        }
        String title = str(body, "title");
        String problem = str(body, "problemOrOpportunity");
        if (title.isBlank() && problem.isBlank()) {
            throw new ValidationException("A short title or a description of what could be better is required.");
        }
        if (title.length() > MAX_TITLE_CHARS
                || problem.length() > MAX_TEXT_CHARS
                || str(body, "betterExperience").length() > MAX_TEXT_CHARS
                || str(body, "beneficiary").length() > MAX_SHORT_CHARS
                || str(body, "needArea").length() > MAX_SHORT_CHARS
                || str(body, "contact").length() > MAX_SHORT_CHARS) {
            throw new ValidationException("One of the fields is too long.");
        }

        enforceWrite(clientIp);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("type", type);
        putIfPresent(out, "needArea", str(body, "needArea"));
        putIfPresent(out, "title", title);
        putIfPresent(out, "problemOrOpportunity", problem);
        putIfPresent(out, "betterExperience", str(body, "betterExperience"));
        putIfPresent(out, "beneficiary", str(body, "beneficiary"));
        out.put("willingToHelp", Boolean.parseBoolean(str(body, "willingToHelp")));
        putIfPresent(out, "contact", str(body, "contact"));

        JsonNode created = participation.createPublicContribution(out, resolveBearer());
        if (created == null || !created.hasNonNull("claimCode")) {
            throw new IllegalStateException("public contribution creation returned no claim code");
        }
        return new ContributionReceipt(
                created.path("reference").asText(null),
                created.path("claimCode").asText(),
                created.path("status").asText(null));
    }

    public JsonNode support(String contributionId, String clientIp) {
        enforceWrite(clientIp);
        // Derive an opaque, PII-free supporter key: stable per IP + idea so a repeat support
        // from the same connection is idempotent downstream, without capturing any identity.
        String supporterKey = "anon-" + ContactOtpService.sha256Hex(
                (clientIp == null ? "unknown" : clientIp) + ":" + contributionId).substring(0, 32);
        Map<String, Object> body = Map.of("supporterKey", supporterKey);
        return participation.supportPublic(contributionId, body, resolveBearer());
    }

    public JsonNode enroll(String cohortId, Map<String, Object> body, String clientIp) {
        String contact = str(body, "contact");
        if (contact.isBlank()) {
            throw new ValidationException("A contact is required so we can reach you about testing.");
        }
        if (contact.length() > MAX_SHORT_CHARS) {
            throw new ValidationException("Contact is too long.");
        }
        enforceWrite(clientIp);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("contact", contact);
        putIfPresent(out, "segment", str(body, "segment"));
        return participation.enrollPublic(cohortId, out, resolveBearer());
    }

    // ── Reads ─────────────────────────────────────────────────────────
    public JsonNode board() {
        return participation.getPublicBoard(resolveBearer());
    }

    public JsonNode cohorts() {
        return participation.getPublicCohorts(resolveBearer());
    }

    public JsonNode statusByClaimCode(String claimCode, String clientIp) {
        if (claimCode == null || claimCode.isBlank() || claimCode.length() > 64
                || !claimCode.matches("[A-Za-z0-9]+")) {
            throw new ValidationException("Enter the claim code you were given.");
        }
        enforceWindowFailClosed(IP_RATE_PREFIX + ContactOtpService.sha256Hex(clientIp == null ? "unknown" : clientIp),
                MAX_REQUESTS_PER_IP * 2, IP_WINDOW_SECONDS,
                "Too many status checks from this connection. Please try again later.");
        return participation.getPublicContributionStatus(claimCode.trim(), resolveBearer());
    }

    // ── helpers ───────────────────────────────────────────────────────
    private void enforceWrite(String clientIp) {
        enforceWindowFailClosed(IP_RATE_PREFIX + ContactOtpService.sha256Hex(clientIp == null ? "unknown" : clientIp),
                MAX_REQUESTS_PER_IP, IP_WINDOW_SECONDS,
                "Too many submissions from this connection. Please try again later.");
        enforceWindowFailClosed(GLOBAL_RATE_KEY, MAX_REQUESTS_GLOBAL, GLOBAL_WINDOW_SECONDS,
                "The service is busy right now. Please try again in a few minutes.");
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
                throw new RateLimitedException(message, ttl != null && ttl > 0 ? ttl : windowSeconds);
            }
        } catch (RateLimitedException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Get-Involved rate-limit store unavailable, refusing anonymous write (fail-closed): {}",
                    e.getMessage());
            throw new RateLimitedException(
                    "We cannot accept anonymous submissions right now. Please try again later or sign in.", 300);
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
