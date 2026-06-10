package zw.gov.mohcc.impilo.experience.bootstrap;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
public class BootstrapStateRepository {

    private static final String STATE_PREFIX = "experience:bff:bootstrap:state:";
    private static final String TOKEN_USED_PREFIX = "experience:bff:bootstrap:token-used:";

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final Duration ttl = Duration.ofDays(365);

    public BootstrapStateRepository(StringRedisTemplate redis, ObjectMapper objectMapper) {
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    public BootstrapDtos.BootstrapStateRecord getOrInit(String tenantId) {
        return find(tenantId).orElseGet(() -> init(tenantId));
    }

    public Optional<BootstrapDtos.BootstrapStateRecord> find(String tenantId) {
        try {
            String json = redis.opsForValue().get(key(tenantId));
            if (json == null || json.isBlank()) return Optional.empty();
            return Optional.of(objectMapper.readValue(json, BootstrapDtos.BootstrapStateRecord.class));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public BootstrapDtos.BootstrapStateRecord save(String tenantId, BootstrapDtos.BootstrapStateRecord record) {
        try {
            redis.opsForValue().set(key(tenantId), objectMapper.writeValueAsString(record), ttl);
            return record;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to persist bootstrap state", e);
        }
    }

    public boolean isTokenUsed(String tenantId, String tokenFingerprint) {
        return Boolean.TRUE.equals(redis.hasKey(tokenKey(tenantId, tokenFingerprint)));
    }

    public void markTokenUsed(String tenantId, String tokenFingerprint) {
        redis.opsForValue().set(tokenKey(tenantId, tokenFingerprint), OffsetDateTime.now().toString(), Duration.ofDays(365));
    }

    private BootstrapDtos.BootstrapStateRecord init(String tenantId) {
        BootstrapDtos.BootstrapStateRecord record = new BootstrapDtos.BootstrapStateRecord(
                UUID.randomUUID().toString(),
                tenantId,
                true,
                false,
                null,
                false,
                null,
                null,
                null,
                null,
                null,
                null,
                OffsetDateTime.now().toString(),
                OffsetDateTime.now().toString(),
                Map.of()
        );
        return save(tenantId, record);
    }

    private static String key(String tenantId) {
        return STATE_PREFIX + tenantId;
    }

    private static String tokenKey(String tenantId, String fingerprint) {
        return TOKEN_USED_PREFIX + tenantId + ":" + fingerprint;
    }
}
