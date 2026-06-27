package zw.gov.mohcc.impilo.khuluma.realtime;

import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Stable per-process identifier. Stamped onto every published {@link RealtimeEvent} so the
 * Redis fan-out listener can skip events this instance already delivered locally.
 */
@Component
public class RealtimeInstance {

    private final String id = UUID.randomUUID().toString();

    public String id() {
        return id;
    }
}
