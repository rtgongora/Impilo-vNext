package zw.gov.mohcc.impilo.live.media;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Local development media provider — issues synthetic tokens clearly marked as non-production.
 */
public class LocalDevMediaProvider implements LiveMediaProvider {

    private final Map<String, MediaRoomContext> rooms = new ConcurrentHashMap<>();

    @Override
    public String providerType() {
        return "LOCAL_DEV";
    }

    @Override
    public MediaRoomContext provisionRoom(MediaRoomContext context) {
        Object suffix = context.attributes() != null ? context.attributes().get("sessionIdSuffix") : null;
        String roomId = context.providerRoomId() != null
                ? context.providerRoomId()
                : "dev-room-" + context.eventId() + (suffix != null ? suffix.toString() : "");
        MediaRoomContext provisioned = new MediaRoomContext(
                context.tenantId(),
                context.eventId(),
                context.sessionId(),
                context.participantId(),
                context.participantRole(),
                context.facilityId(),
                roomId,
                providerType(),
                context.attributes());
        rooms.put(roomId, provisioned);
        return provisioned;
    }

    @Override
    public MediaTokenResult issueToken(MediaRoomContext context) {
        String roomId = context.providerRoomId();
        return new MediaTokenResult(
                roomId,
                "ws://localhost:7880/dev/" + roomId,
                "DEV-TOKEN-NOT-FOR-PRODUCTION:" + UUID.randomUUID(),
                Instant.now().plusSeconds(3600),
                providerType(),
                "dev");
    }

    @Override
    public MediaHealthStatus checkHealth(String roomId) {
        boolean exists = rooms.containsKey(roomId);
        return new MediaHealthStatus(roomId, exists ? "HEALTHY" : "UNKNOWN", exists, providerType(),
                "LOCAL_DEV provider — not for production use");
    }

    @Override
    public void startSession(String roomId) {
        // no-op for local dev
    }

    @Override
    public MediaRoomContext endSession(String roomId) {
        return rooms.remove(roomId);
    }

    @Override
    public String startRecording(String roomId) {
        return "dev-recording-" + roomId + "-" + UUID.randomUUID();
    }

    @Override
    public void stopRecording(String roomId, String recordingRef) {
        // no-op for local dev
    }

    @Override
    public String getPlaybackUrl(String roomId, String recordingRef) {
        return "dev-replay://localhost/live/" + roomId + "/" + recordingRef;
    }
}
