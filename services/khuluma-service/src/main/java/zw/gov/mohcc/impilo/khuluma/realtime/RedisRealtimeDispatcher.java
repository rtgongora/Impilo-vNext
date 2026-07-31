package zw.gov.mohcc.impilo.khuluma.realtime;

import zw.gov.mohcc.impilo.realtime.RealtimeEvent;
import zw.gov.mohcc.impilo.realtime.RealtimeHub;
import zw.gov.mohcc.impilo.realtime.RealtimeInstance;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import zw.gov.mohcc.impilo.khuluma.core.RealtimeDispatcher;

import java.util.Map;
import java.util.UUID;

/**
 * Primary {@link RealtimeDispatcher} for the running gateway. Each publish (1) delivers
 * immediately to local subscribers via {@link RealtimeHub}, then (2) best-effort publishes the
 * event to Redis so subscribers on OTHER service instances also receive it. The Redis listener
 * ({@link RealtimeRedisConfig}) ignores events stamped with this instance's id to avoid double
 * local delivery. When Redis is disabled/unavailable, local delivery still works — so a single
 * instance is fully functional and tests need no Redis.
 */
@Component
@Primary
public class RedisRealtimeDispatcher implements RealtimeDispatcher {

    private static final Logger log = LoggerFactory.getLogger(RedisRealtimeDispatcher.class);

    private final RealtimeHub hub;
    private final RealtimeInstance instance;
    private final ObjectMapper objectMapper;
    private final ObjectProvider<StringRedisTemplate> redisTemplate;
    private final String topic;
    private final boolean redisEnabled;

    public RedisRealtimeDispatcher(RealtimeHub hub,
                                   RealtimeInstance instance,
                                   ObjectMapper objectMapper,
                                   ObjectProvider<StringRedisTemplate> redisTemplate,
                                   @Value("${impilo.khuluma.realtime.redis-topic:khuluma:realtime}") String topic,
                                   @Value("${impilo.khuluma.realtime.redis-enabled:true}") boolean redisEnabled) {
        this.hub = hub;
        this.instance = instance;
        this.objectMapper = objectMapper;
        this.redisTemplate = redisTemplate;
        this.topic = topic;
        this.redisEnabled = redisEnabled;
    }

    @Override
    public void publish(UUID tenantId, String channel, String eventType, Map<String, Object> payload) {
        RealtimeEvent event = new RealtimeEvent(tenantId, channel, eventType, payload, instance.id());

        // (1) Local subscribers — always, even without Redis.
        hub.dispatchLocal(event);

        // (2) Remote instances — best-effort (skipped when Redis fan-out is disabled).
        if (!redisEnabled) {
            return;
        }
        StringRedisTemplate template = redisTemplate.getIfAvailable();
        if (template == null) {
            return;
        }
        try {
            template.convertAndSend(topic, objectMapper.writeValueAsString(event));
        } catch (Exception ex) {
            log.debug("Redis fan-out publish skipped (channel={}, event={}): {}", channel, eventType, ex.toString());
        }
    }
}
