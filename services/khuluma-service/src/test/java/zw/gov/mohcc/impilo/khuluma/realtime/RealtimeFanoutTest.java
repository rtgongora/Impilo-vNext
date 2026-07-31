package zw.gov.mohcc.impilo.khuluma.realtime;

import zw.gov.mohcc.impilo.realtime.RealtimeEvent;
import zw.gov.mohcc.impilo.realtime.RealtimeHub;
import zw.gov.mohcc.impilo.realtime.RealtimeInstance;
import zw.gov.mohcc.impilo.realtime.RealtimeSubscription;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Multi-instance fan-out logic without a live Redis: the dispatcher always delivers locally and
 * stamps the origin instance; the Redis listener re-dispatches REMOTE events but skips this
 * instance's own (already delivered) events to prevent double delivery.
 */
class RealtimeFanoutTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @SuppressWarnings("unchecked")
    @Test
    void dispatcher_deliversLocally_andStampsOrigin() {
        RealtimeHub hub = new RealtimeHub();
        RealtimeInstance instance = new RealtimeInstance();
        ObjectProvider<StringRedisTemplate> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(null);
        RedisRealtimeDispatcher dispatcher =
                new RedisRealtimeDispatcher(hub, instance, mapper, provider, "khuluma:realtime", false);

        UUID tenant = UUID.randomUUID();
        List<RealtimeEvent> received = new ArrayList<>();
        hub.register(new RealtimeSubscription("s1", tenant, "A", Set.of("conversation:x"), received::add));

        dispatcher.publish(tenant, "conversation:x", "message.created", Map.of("body", "hi"));

        assertThat(received).hasSize(1);
        assertThat(received.get(0).originInstanceId()).isEqualTo(instance.id());
    }

    @Test
    void listener_skipsOwnEvents_deliversRemote() throws Exception {
        RealtimeHub hub = new RealtimeHub();
        RealtimeInstance instance = new RealtimeInstance();
        RealtimeRedisConfig config = new RealtimeRedisConfig(
                hub, instance, mapper, "khuluma:realtime", "pct:emergency:realtime");

        UUID tenant = UUID.randomUUID();
        List<RealtimeEvent> received = new ArrayList<>();
        hub.register(new RealtimeSubscription("s1", tenant, "A", Set.of("conversation:x"), received::add));

        // Event originating from THIS instance — already delivered locally, must be ignored.
        RealtimeEvent own = new RealtimeEvent(tenant, "conversation:x", "message.created", Map.of(), instance.id());
        config.onMessage(mapper.writeValueAsString(own));
        assertThat(received).isEmpty();

        // Event from a DIFFERENT instance — must be delivered.
        RealtimeEvent remote = new RealtimeEvent(tenant, "conversation:x", "message.created",
                Map.of("body", "remote"), "other-instance");
        config.onMessage(mapper.writeValueAsString(remote));
        assertThat(received).hasSize(1);
        assertThat(received.get(0).payload()).containsEntry("body", "remote");
    }
}
