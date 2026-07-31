package zw.gov.mohcc.impilo.realtime;

import java.util.UUID;

/** Canonical channel name helpers shared by every realtime host. */
public final class RealtimeChannels {

    private RealtimeChannels() {}

    public static String actorChannel(String actorId) {
        return "actor:" + actorId;
    }

    public static String tenantChannel(UUID tenantId) {
        return "tenant:" + tenantId;
    }

    public static String conversationChannel(UUID conversationId) {
        return "conversation:" + conversationId;
    }

    public static String callChannel(UUID callId) {
        return "call:" + callId;
    }

    /** Emergency episode invalidation channel — hints only, never clinical payload. */
    public static String episodeChannel(UUID episodeId) {
        return "episode:" + episodeId;
    }

    public static UUID parseUuidSuffix(String channel, String prefix) {
        if (channel == null || !channel.startsWith(prefix)) {
            return null;
        }
        try {
            return UUID.fromString(channel.substring(prefix.length()));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
