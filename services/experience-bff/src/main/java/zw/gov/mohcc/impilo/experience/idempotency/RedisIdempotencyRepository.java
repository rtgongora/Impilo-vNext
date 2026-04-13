package zw.gov.mohcc.impilo.experience.idempotency;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.springframework.data.redis.core.StringRedisTemplate;
import zw.gov.mohcc.impilo.companion.idempotency.IdempotencyRecord;
import zw.gov.mohcc.impilo.companion.idempotency.IdempotencyRepository;

/**
 * Redis-backed idempotency store for the stateless BFF (no PostgreSQL).
 */
public class RedisIdempotencyRepository implements IdempotencyRepository {

    private static final String KEY_PREFIX = "experience:bff:idempotency:";

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final long ttlHours;

    public RedisIdempotencyRepository(
            StringRedisTemplate redis, ObjectMapper objectMapper, long ttlHours) {
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.ttlHours = ttlHours;
    }

    private static String redisKey(String tenantId, String podId, String idempotencyKey) {
        return KEY_PREFIX + tenantId + ":" + podId + ":" + idempotencyKey;
    }

    @Override
    public Optional<IdempotencyRecord> find(String tenantId, String podId, String idempotencyKey) {
        String json = redis.opsForValue().get(redisKey(tenantId, podId, idempotencyKey));
        if (json == null || json.isBlank()) {
            return Optional.empty();
        }
        try {
            IdempotencyRecord rec = objectMapper.readValue(json, IdempotencyRecord.class);
            if (rec.expiresAt() != null && rec.expiresAt().isBefore(OffsetDateTime.now())) {
                redis.delete(redisKey(tenantId, podId, idempotencyKey));
                return Optional.empty();
            }
            return Optional.of(rec);
        } catch (JsonProcessingException e) {
            return Optional.empty();
        }
    }

    @Override
    public void store(
            String tenantId,
            String podId,
            String idempotencyKey,
            String requestHash,
            int responseStatus,
            String responseBody) {
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime expiresAt = now.plusHours(ttlHours);
        IdempotencyRecord rec =
                new IdempotencyRecord(
                        tenantId,
                        podId,
                        idempotencyKey,
                        requestHash,
                        responseStatus,
                        responseBody,
                        now,
                        expiresAt);
        String k = redisKey(tenantId, podId, idempotencyKey);
        try {
            String json = objectMapper.writeValueAsString(rec);
            redis.opsForValue().setIfAbsent(k, json, ttlHours, TimeUnit.HOURS);
        } catch (JsonProcessingException ignored) {
            // skip — cannot persist without serializable payload
        }
    }
}
