package zw.gov.mohcc.impilo.pct.realtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import zw.gov.mohcc.impilo.realtime.InvalidationHint;
import zw.gov.mohcc.impilo.realtime.RealtimeChannels;
import zw.gov.mohcc.impilo.realtime.RealtimeEvent;
import zw.gov.mohcc.impilo.realtime.RealtimeHub;
import zw.gov.mohcc.impilo.realtime.RealtimeInstance;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Env-gated publisher of emergency episode invalidation hints. Frames carry
 * {@code {event_type, channel, episode_id, revision}} only — never PHI. Off by default so the
 * existing Kafka outbox path remains the source of truth until a host opts in.
 */
@Component
@ConditionalOnProperty(name = "pct.emergency.realtime-enabled", havingValue = "true")
public class EmergencyRealtimePublisher {

    private static final Logger log = LoggerFactory.getLogger(EmergencyRealtimePublisher.class);

    private final RealtimeHub hub;
    private final RealtimeInstance instance;
    private final ObjectMapper objectMapper;
    private final ObjectProvider<StringRedisTemplate> redisTemplate;
    private final String topic;
    private final boolean redisEnabled;
    private final AtomicLong revision = new AtomicLong();

    public EmergencyRealtimePublisher(
            RealtimeHub hub,
            RealtimeInstance instance,
            ObjectMapper objectMapper,
            ObjectProvider<StringRedisTemplate> redisTemplate,
            @Value("${pct.emergency.realtime-topic:pct:emergency:realtime}") String topic,
            @Value("${pct.emergency.realtime-redis-enabled:true}") boolean redisEnabled) {
        this.hub = hub;
        this.instance = instance;
        this.objectMapper = objectMapper;
        this.redisTemplate = redisTemplate;
        this.topic = topic;
        this.redisEnabled = redisEnabled;
    }

    public void publishEpisodeHint(UUID tenantId, UUID episodeId, String eventType) {
        long rev = revision.incrementAndGet();
        Map<String, Object> payload = InvalidationHint.episodeFrame(eventType, episodeId, rev);

        // Episode channel (for hosts that admit episode:*) plus tenant broadcast so Khuluma WS
        // subscribers — which always hold tenant: — receive the invalidation without PHI.
        dispatchAndFanOut(tenantId, RealtimeChannels.episodeChannel(episodeId), eventType, payload);
        dispatchAndFanOut(tenantId, RealtimeChannels.tenantChannel(tenantId), eventType, payload);
    }

    private void dispatchAndFanOut(UUID tenantId, String channel, String eventType, Map<String, Object> payload) {
        RealtimeEvent event = new RealtimeEvent(tenantId, channel, eventType, payload, instance.id());
        hub.dispatchLocal(event);

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
            log.debug("Emergency realtime Redis fan-out skipped (channel={}, event={}): {}",
                    channel, eventType, ex.toString());
        }
    }
}
