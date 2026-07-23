package zw.gov.mohcc.impilo.experience.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import zw.gov.mohcc.impilo.experience.client.GuidanceServiceClient;

import java.time.Duration;

/**
 * Anonymous public Nompilo Q&A lane (gateway ADR): abuse controls in front of the
 * guidance service's public grounded-ask endpoint.
 *
 * <p>This is an anonymous lane that reaches an LLM, so unlike the SOS write it is a
 * cost/abuse surface, not a life-safety write. Controls:</p>
 * <ul>
 *   <li><b>Rate limits</b> — per-IP {@value #MAX_ASKS_PER_IP}/{@value #IP_WINDOW_SECONDS}s and
 *       global {@value #MAX_ASKS_GLOBAL}/{@value #GLOBAL_WINDOW_SECONDS}s fixed windows
 *       (same pattern as {@link PublicSosIntakeService}). Redis unavailable fails OPEN with a
 *       warning: the surrounding journey is the emergency page, and the downstream honesty
 *       gate + question cap bound the blast radius.</li>
 *   <li><b>Question bounds</b> — trimmed, control characters stripped, capped at
 *       {@value #MAX_QUESTION_CHARS} chars (the service caps again downstream).</li>
 *   <li><b>No storage</b> — the BFF keeps nothing; single-turn passthrough.</li>
 * </ul>
 */
@Service
public class PublicGuidanceAskService {

    private static final Logger log = LoggerFactory.getLogger(PublicGuidanceAskService.class);

    public static final int MAX_ASKS_PER_IP = 10;
    public static final int IP_WINDOW_SECONDS = 300;    // 5 minutes
    public static final int MAX_ASKS_GLOBAL = 120;
    public static final int GLOBAL_WINDOW_SECONDS = 60; // 1 minute
    public static final int MAX_QUESTION_CHARS = 500;

    private static final String IP_RATE_PREFIX = "gateway:guidance-ask:rl:ip:";
    private static final String GLOBAL_RATE_KEY = "gateway:guidance-ask:rl:global";

    private final StringRedisTemplate redis;
    private final GuidanceServiceClient guidance;

    public PublicGuidanceAskService(StringRedisTemplate redis, GuidanceServiceClient guidance) {
        this.redis = redis;
        this.guidance = guidance;
    }

    /** @throws AskRateLimitedException when a window is exhausted */
    public JsonNode ask(String rawQuestion, String clientIp) {
        String question = sanitize(rawQuestion);
        if (question.length() < 3) {
            throw new IllegalArgumentException("Ask a short question.");
        }
        enforceWindow(IP_RATE_PREFIX + clientIp, MAX_ASKS_PER_IP, IP_WINDOW_SECONDS,
                "Too many questions from this connection — please wait a few minutes.");
        enforceWindow(GLOBAL_RATE_KEY, MAX_ASKS_GLOBAL, GLOBAL_WINDOW_SECONDS,
                "Nompilo is very busy right now — please try again shortly.");
        return guidance.publicAsk(question);
    }

    private static String sanitize(String raw) {
        if (raw == null) return "";
        String cleaned = raw.replaceAll("[\\p{Cntrl}&&[^\n\t]]", " ").trim();
        return cleaned.length() > MAX_QUESTION_CHARS ? cleaned.substring(0, MAX_QUESTION_CHARS) : cleaned;
    }

    private void enforceWindow(String key, int max, int windowSeconds, String message) {
        try {
            Long count = redis.opsForValue().increment(key);
            if (count != null && count == 1) {
                redis.expire(key, Duration.ofSeconds(windowSeconds));
            }
            if (count != null && count > max) {
                Long ttl = redis.getExpire(key);
                throw new AskRateLimitedException(message, ttl != null && ttl > 0 ? ttl : windowSeconds);
            }
        } catch (AskRateLimitedException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Guidance-ask rate-limit store unavailable, allowing request (fail-open): {}", e.getMessage());
        }
    }

    /** Window exhausted — carries the Retry-After hint. */
    public static class AskRateLimitedException extends RuntimeException {
        private final long retryAfterSeconds;

        public AskRateLimitedException(String message, long retryAfterSeconds) {
            super(message);
            this.retryAfterSeconds = retryAfterSeconds;
        }

        public long retryAfterSeconds() {
            return retryAfterSeconds;
        }
    }
}
