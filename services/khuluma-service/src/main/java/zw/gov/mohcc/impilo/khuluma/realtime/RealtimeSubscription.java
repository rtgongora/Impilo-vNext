package zw.gov.mohcc.impilo.khuluma.realtime;

import java.util.Set;
import java.util.UUID;

/**
 * One connected subscriber (an SSE emitter or a WebSocket session) registered with the
 * {@link RealtimeHub}. It is scoped to a tenant and a fixed set of logical channels, and
 * carries a transport-agnostic {@link Sink} the hub calls to deliver an event.
 */
public final class RealtimeSubscription {

    /** Transport binding: how a delivered event reaches the wire (SSE or WS). */
    @FunctionalInterface
    public interface Sink {
        /** Deliver one event; throwing signals a broken connection and triggers unregistration. */
        void deliver(RealtimeEvent event) throws Exception;
    }

    private final String id;
    private final UUID tenantId;
    private final String actorId;
    private final Set<String> channels;
    private final Sink sink;

    public RealtimeSubscription(String id, UUID tenantId, String actorId, Set<String> channels, Sink sink) {
        this.id = id;
        this.tenantId = tenantId;
        this.actorId = actorId;
        this.channels = Set.copyOf(channels);
        this.sink = sink;
    }

    public String id() { return id; }
    public UUID tenantId() { return tenantId; }
    public String actorId() { return actorId; }
    public Set<String> channels() { return channels; }
    public Sink sink() { return sink; }

    public boolean listensTo(UUID tenantId, String channel) {
        return this.tenantId.equals(tenantId) && channels.contains(channel);
    }
}
