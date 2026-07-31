package zw.gov.mohcc.impilo.realtime;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class RealtimeHubTest {

    @Test
    void fansOutOnlyToMatchingTenantAndChannel() {
        RealtimeHub hub = new RealtimeHub();
        UUID tenant = UUID.randomUUID();
        AtomicReference<RealtimeEvent> seen = new AtomicReference<>();

        hub.register(new RealtimeSubscription(
                "s1", tenant, "actor-1", Set.of("episode:x"), seen::set));

        hub.dispatchLocal(tenant, "episode:x", "episode.updated", Map.of("revision", 1), "origin");
        assertThat(seen.get()).isNotNull();
        assertThat(seen.get().eventType()).isEqualTo("episode.updated");

        seen.set(null);
        hub.dispatchLocal(tenant, "episode:other", "episode.updated", Map.of(), "origin");
        assertThat(seen.get()).isNull();
    }

    @Test
    void invalidationHintCarriesNoClinicalFields() {
        UUID episode = UUID.randomUUID();
        Map<String, Object> frame = InvalidationHint.episodeFrame("episode.state_changed", episode, 7L);
        assertThat(frame.keySet()).containsExactly("event_type", "channel", "episode_id", "revision");
        assertThat(frame.get("channel")).isEqualTo(RealtimeChannels.episodeChannel(episode));
    }
}
