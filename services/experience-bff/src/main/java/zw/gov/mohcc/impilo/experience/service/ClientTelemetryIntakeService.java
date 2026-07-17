package zw.gov.mohcc.impilo.experience.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import zw.gov.mohcc.impilo.experience.client.ObservabilityServiceClient;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * Anonymous public-lane client-telemetry intake (gateway ADR — {@code gateway-client-telemetry}).
 *
 * <p>Clones the {@link PublicFeedbackIntakeService} fail-CLOSED abuse controls: per-IP and
 * global fixed rate windows in Redis that refuse the write when the limiter store is down
 * (telemetry is not life-safety — only the SOS lane fails open). The batch is aggressively
 * allow-listed SERVER-SIDE — client input is never trusted: only {@code route}, {@code code},
 * {@code correlationId}, {@code requestId}, {@code status}, {@code at} survive, each length- or
 * type-capped, and any event missing {@code route}+{@code code} is dropped. The sanitized batch
 * is forwarded to observability best-effort; the citizen always gets a {@code 202} so a broken
 * downstream can never trigger a client retry storm.</p>
 */
@Service
public class ClientTelemetryIntakeService {

    private static final Logger log = LoggerFactory.getLogger(ClientTelemetryIntakeService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    // ── Abuse thresholds (journalled in the ADR public contract registry) ──
    public static final int MAX_EVENTS_PER_REQUEST = 20;
    public static final int MAX_REQUESTS_PER_IP = 60;
    public static final int IP_WINDOW_SECONDS = 60;          // 1 minute
    public static final int MAX_REQUESTS_GLOBAL = 6000;
    public static final int GLOBAL_WINDOW_SECONDS = 60;      // 1 minute

    // ── Per-field allow-list caps ──
    public static final int MAX_ROUTE_CHARS = 256;
    public static final int MAX_CODE_CHARS = 64;
    public static final int MAX_CORRELATION_ID_CHARS = 64;
    public static final int MAX_REQUEST_ID_CHARS = 64;

    private static final String IP_RATE_PREFIX = "gateway:telemetry:rl:ip:";
    private static final String GLOBAL_RATE_KEY = "gateway:telemetry:rl:global";

    private final StringRedisTemplate redis;
    private final ObservabilityServiceClient observability;

    public ClientTelemetryIntakeService(StringRedisTemplate redis,
                                        ObservabilityServiceClient observability) {
        this.redis = redis;
        this.observability = observability;
    }

    public static class TelemetryValidationException extends RuntimeException {
        public TelemetryValidationException(String message) { super(message); }
    }

    public static class TelemetryRateLimitedException extends RuntimeException {
        private final long retryAfterSeconds;
        public TelemetryRateLimitedException(String message, long retryAfterSeconds) {
            super(message);
            this.retryAfterSeconds = retryAfterSeconds;
        }
        public long retryAfterSeconds() { return retryAfterSeconds; }
    }

    /**
     * Accept a client-telemetry batch. Returns the number of events accepted (after
     * server-side sanitization + allow-listing). Forwarding is best-effort.
     */
    @SuppressWarnings("unchecked")
    public int accept(Map<String, Object> body, String clientIp) {
        Object rawEvents = body == null ? null : body.get("events");
        if (!(rawEvents instanceof List<?> events)) {
            throw new TelemetryValidationException("events must be an array.");
        }
        if (events.size() > MAX_EVENTS_PER_REQUEST) {
            throw new TelemetryValidationException(
                    "Too many events in one request (max " + MAX_EVENTS_PER_REQUEST + ").");
        }

        // Fail-closed abuse controls: no working limiter -> no anonymous write.
        enforceWindowFailClosed(
                IP_RATE_PREFIX + ContactOtpService.sha256Hex(clientIp == null ? "unknown" : clientIp),
                MAX_REQUESTS_PER_IP, IP_WINDOW_SECONDS,
                "Too many telemetry events from this connection. Please slow down.");
        enforceWindowFailClosed(GLOBAL_RATE_KEY, MAX_REQUESTS_GLOBAL, GLOBAL_WINDOW_SECONDS,
                "Telemetry is busy right now. Please try again shortly.");

        ArrayNode sanitized = MAPPER.createArrayNode();
        for (Object raw : events) {
            if (!(raw instanceof Map<?, ?> map)) {
                continue;
            }
            ObjectNode clean = sanitizeEvent((Map<String, Object>) map);
            if (clean != null) {
                sanitized.add(clean);
            }
        }

        if (!sanitized.isEmpty()) {
            ObjectNode batch = MAPPER.createObjectNode();
            batch.set("events", sanitized);
            observability.postClientEvents(batch);       // best-effort; swallows failures
        }
        return sanitized.size();
    }

    /**
     * Build an allow-listed event node, or {@code null} to drop it. Nothing outside the
     * six permitted fields is ever copied through — the client payload is not trusted.
     */
    private ObjectNode sanitizeEvent(Map<String, Object> event) {
        String route = capped(str(event, "route"), MAX_ROUTE_CHARS);
        String code = capped(str(event, "code"), MAX_CODE_CHARS);
        // Drop any event missing the two mandatory identifiers.
        if (route.isBlank() || code.isBlank()) {
            return null;
        }
        ObjectNode clean = MAPPER.createObjectNode();
        clean.put("route", route);
        clean.put("code", code);

        String correlationId = capped(str(event, "correlationId"), MAX_CORRELATION_ID_CHARS);
        if (!correlationId.isBlank()) {
            clean.put("correlationId", correlationId);
        }
        String requestId = capped(str(event, "requestId"), MAX_REQUEST_ID_CHARS);
        if (!requestId.isBlank()) {
            clean.put("requestId", requestId);
        }
        Integer status = asInt(event.get("status"));
        if (status != null) {
            clean.put("status", status);
        }
        String at = isoOrNull(str(event, "at"));
        if (at != null) {
            clean.put("at", at);
        }
        return clean;
    }

    private void enforceWindowFailClosed(String key, int max, int windowSeconds, String message) {
        try {
            Long count = redis.opsForValue().increment(key);
            if (count != null && count == 1) {
                redis.expire(key, Duration.ofSeconds(windowSeconds));
            }
            if (count != null && count > max) {
                Long ttl = redis.getExpire(key);
                throw new TelemetryRateLimitedException(message, ttl != null && ttl > 0 ? ttl : windowSeconds);
            }
        } catch (TelemetryRateLimitedException e) {
            throw e;
        } catch (Exception e) {
            // Telemetry is not life-safety: an anonymous write lane without a working limiter is
            // an open abuse channel, so it fails CLOSED (mirrors feedback, unlike the SOS lane).
            log.warn("Telemetry rate-limit store unavailable, refusing anonymous write (fail-closed): {}",
                    e.getMessage());
            throw new TelemetryRateLimitedException(
                    "We cannot accept telemetry right now. Please try again later.", 60);
        }
    }

    private static String capped(String value, int max) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        return trimmed.length() > max ? trimmed.substring(0, max) : trimmed;
    }

    private static Integer asInt(Object value) {
        if (value instanceof Number n) {
            return n.intValue();
        }
        if (value instanceof String s && !s.isBlank()) {
            try {
                return Integer.parseInt(s.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    /** Returns the ISO-8601 instant string when parseable, else {@code null} (never trust client time). */
    private static String isoOrNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(value.trim()).toString();
        } catch (Exception e) {
            return null;
        }
    }

    private static String str(Map<String, Object> map, String key) {
        Object v = map.get(key);
        return v == null ? "" : v.toString();
    }
}
