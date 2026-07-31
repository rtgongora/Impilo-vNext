package zw.gov.mohcc.impilo.realtime;

import java.util.UUID;

/**
 * Stable per-process identifier. Stamped onto every published {@link RealtimeEvent} so the
 * Redis fan-out listener can skip events this instance already delivered locally.
 *
 * Constructed via {@link RealtimeCoreConfiguration} — not component-scanned.
 */
public class RealtimeInstance {

    private final String id = UUID.randomUUID().toString();

    public String id() {
        return id;
    }
}
