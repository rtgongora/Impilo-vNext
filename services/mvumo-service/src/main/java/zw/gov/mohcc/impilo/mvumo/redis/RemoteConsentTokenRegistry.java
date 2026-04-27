package zw.gov.mohcc.impilo.mvumo.redis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Short-lived storage for raw remote consent tokens. DB holds only a hash; Redis maps raw token to session
 * metadata for open/verify flows without a global DB scan. Uses same TTL as the remote session.
 */
@Component
@ConditionalOnBean(StringRedisTemplate.class)
public class RemoteConsentTokenRegistry {

    public static final String KEY_PREFIX = MvumoRedisKeySupport.REMOTE_TOKEN;

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public RemoteConsentTokenRegistry(StringRedisTemplate redis, ObjectMapper objectMapper) {
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    public void register(
            String rawToken,
            UUID tenantId,
            UUID sessionId,
            UUID consentRequestId,
            int ttlMinutes) {
        if (rawToken == null || rawToken.isBlank()) {
            return;
        }
        String key = KEY_PREFIX + rawToken;
        try {
            String json = objectMapper.writeValueAsString(
                    Map.of(
                            "tenantId", tenantId.toString(),
                            "sessionId", sessionId.toString(),
                            "consentRequestId", consentRequestId.toString()));
            long ttl = Math.max(1, ttlMinutes);
            redis.opsForValue().set(key, json, Duration.ofMinutes(ttl));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    public Optional<Map<String, String>> resolve(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return Optional.empty();
        }
        String v = redis.opsForValue().get(KEY_PREFIX + rawToken);
        if (v == null) {
            return Optional.empty();
        }
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> m = objectMapper.readValue(v, Map.class);
            return Optional.of(Map.of(
                    "tenantId", String.valueOf(m.get("tenantId")),
                    "sessionId", String.valueOf(m.get("sessionId")),
                    "consentRequestId", String.valueOf(m.get("consentRequestId"))));
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}
