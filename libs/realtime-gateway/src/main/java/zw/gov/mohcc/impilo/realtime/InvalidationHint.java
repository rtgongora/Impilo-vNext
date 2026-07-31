package zw.gov.mohcc.impilo.realtime;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Wire frame for emergency episode invalidation hints — never clinical content / PHI.
 *
 * <pre>{ "event_type", "channel", "episode_id", "revision" }</pre>
 */
public final class InvalidationHint {

    private InvalidationHint() {}

    public static Map<String, Object> episodeFrame(String eventType, UUID episodeId, long revision) {
        Map<String, Object> frame = new LinkedHashMap<>();
        frame.put("event_type", eventType);
        frame.put("channel", RealtimeChannels.episodeChannel(episodeId));
        frame.put("episode_id", episodeId.toString());
        frame.put("revision", revision);
        return frame;
    }
}
