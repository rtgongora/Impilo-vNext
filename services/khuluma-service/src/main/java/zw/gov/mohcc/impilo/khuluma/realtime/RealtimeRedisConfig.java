package zw.gov.mohcc.impilo.khuluma.realtime;

import zw.gov.mohcc.impilo.realtime.RealtimeEvent;
import zw.gov.mohcc.impilo.realtime.RealtimeHub;
import zw.gov.mohcc.impilo.realtime.RealtimeInstance;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.adapter.MessageListenerAdapter;

import java.nio.charset.StandardCharsets;

/**
 * Wires the Redis pub/sub side of the realtime gateway for multi-instance fan-out. Subscribes to
 * the realtime topic and forwards remote events into the local {@link RealtimeHub}, skipping
 * events this instance published (already delivered locally). Gated by
 * {@code impilo.khuluma.realtime.redis-enabled} (default true) so tests run without Redis.
 */
@Configuration
@ConditionalOnProperty(name = "impilo.khuluma.realtime.redis-enabled", havingValue = "true", matchIfMissing = true)
public class RealtimeRedisConfig {

    private static final Logger log = LoggerFactory.getLogger(RealtimeRedisConfig.class);

    private final RealtimeHub hub;
    private final RealtimeInstance instance;
    private final ObjectMapper objectMapper;
    private final String topic;
    private final String emergencyTopic;

    public RealtimeRedisConfig(RealtimeHub hub, RealtimeInstance instance, ObjectMapper objectMapper,
                               @Value("${impilo.khuluma.realtime.redis-topic:khuluma:realtime}") String topic,
                               @Value("${impilo.khuluma.realtime.emergency-topic:pct:emergency:realtime}") String emergencyTopic) {
        this.hub = hub;
        this.instance = instance;
        this.objectMapper = objectMapper;
        this.topic = topic;
        this.emergencyTopic = emergencyTopic;
    }

    /** Listener that deserializes a fanned-out event and re-dispatches it locally. */
    public void onMessage(String body) {
        try {
            RealtimeEvent event = objectMapper.readValue(body, RealtimeEvent.class);
            if (instance.id().equals(event.originInstanceId())) {
                return; // this instance already delivered it locally
            }
            hub.dispatchLocal(event);
        } catch (Exception ex) {
            log.debug("Failed to handle fanned-out realtime event: {}", ex.toString());
        }
    }

    @Bean
    public MessageListenerAdapter khulumaRealtimeListenerAdapter() {
        MessageListenerAdapter adapter = new MessageListenerAdapter(this, "onMessage");
        adapter.setSerializer(new org.springframework.data.redis.serializer.RedisSerializer<Object>() {
            @Override public byte[] serialize(Object o) { return o == null ? new byte[0] : o.toString().getBytes(StandardCharsets.UTF_8); }
            @Override public Object deserialize(byte[] bytes) { return bytes == null ? null : new String(bytes, StandardCharsets.UTF_8); }
        });
        return adapter;
    }

    @Bean
    public RedisMessageListenerContainer khulumaRealtimeListenerContainer(
            RedisConnectionFactory connectionFactory, MessageListenerAdapter khulumaRealtimeListenerAdapter) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(khulumaRealtimeListenerAdapter, new ChannelTopic(topic));
        // W19 bridge: PCT publishes episode invalidation hints on a separate topic; Khuluma hosts
        // the only browser WS/SSE, so it must listen here or the UI never sees them.
        if (emergencyTopic != null && !emergencyTopic.isBlank() && !emergencyTopic.equals(topic)) {
            container.addMessageListener(khulumaRealtimeListenerAdapter, new ChannelTopic(emergencyTopic));
            log.info("Khuluma realtime Redis fan-out enabled on topics '{}' and '{}'", topic, emergencyTopic);
        } else {
            log.info("Khuluma realtime Redis fan-out enabled on topic '{}'", topic);
        }
        return container;
    }
}
