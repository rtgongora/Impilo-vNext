package zw.gov.mohcc.impilo.khuluma.realtime;

import zw.gov.mohcc.impilo.realtime.RealtimeEvent;
import zw.gov.mohcc.impilo.realtime.RealtimeHub;
import zw.gov.mohcc.impilo.realtime.RealtimeInstance;
import zw.gov.mohcc.impilo.realtime.RealtimeSubscription;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The subscription/dispatch core: channel + tenant matching, unregistration, and broken-sink
 * eviction. This is the shared heart of both the SSE and WebSocket transports.
 */
class RealtimeHubTest {

    @Test
    void dispatch_matchesChannelAndTenant_only() {
        RealtimeHub hub = new RealtimeHub();
        UUID tenant = UUID.randomUUID();
        List<RealtimeEvent> received = new ArrayList<>();
        hub.register(new RealtimeSubscription("s1", tenant, "A", Set.of("conversation:x"), received::add));

        hub.dispatchLocal(tenant, "conversation:x", "message.created", Map.of("body", "hi"), "inst");
        assertThat(received).hasSize(1);

        hub.dispatchLocal(tenant, "conversation:y", "message.created", Map.of(), "inst"); // other channel
        hub.dispatchLocal(UUID.randomUUID(), "conversation:x", "message.created", Map.of(), "inst"); // other tenant
        assertThat(received).hasSize(1);
    }

    @Test
    void unregister_stopsDelivery() {
        RealtimeHub hub = new RealtimeHub();
        UUID tenant = UUID.randomUUID();
        List<RealtimeEvent> received = new ArrayList<>();
        hub.register(new RealtimeSubscription("s1", tenant, "A", Set.of("c"), received::add));
        hub.unregister("s1");
        hub.dispatchLocal(tenant, "c", "e", Map.of(), "inst");
        assertThat(received).isEmpty();
        assertThat(hub.subscriberCount()).isZero();
    }

    @Test
    void brokenSink_isEvicted() {
        RealtimeHub hub = new RealtimeHub();
        UUID tenant = UUID.randomUUID();
        hub.register(new RealtimeSubscription("bad", tenant, "A", Set.of("c"), e -> { throw new RuntimeException("boom"); }));
        hub.dispatchLocal(tenant, "c", "e", Map.of(), "inst");
        assertThat(hub.subscriberCount()).isZero();
    }
}
