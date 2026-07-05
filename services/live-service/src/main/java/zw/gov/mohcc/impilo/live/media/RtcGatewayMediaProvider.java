package zw.gov.mohcc.impilo.live.media;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import zw.gov.mohcc.impilo.live.integration.DocumentServiceClient;
import zw.gov.mohcc.impilo.live.integration.RtcGatewayClient;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public class RtcGatewayMediaProvider implements LiveMediaProvider {

    private static final Logger log = LoggerFactory.getLogger(RtcGatewayMediaProvider.class);

    private final RtcGatewayClient rtcGatewayClient;
    private final DocumentServiceClient documentServiceClient;

    public RtcGatewayMediaProvider(RtcGatewayClient rtcGatewayClient,
                                   DocumentServiceClient documentServiceClient) {
        this.rtcGatewayClient = rtcGatewayClient;
        this.documentServiceClient = documentServiceClient;
    }

    @Override
    public String providerType() {
        return "RTC_GATEWAY";
    }

    @Override
    public MediaRoomContext provisionRoom(MediaRoomContext context) {
        TrustContext ctx = TrustContextHolder.get();
        Map<String, Object> attributes = context.attributes() != null ? context.attributes() : Map.of();
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("tenantId", context.tenantId().toString());
        // Linked child rooms (backstage) provision under a suffixed session id —
        // rtc-gateway derives the '-backstage' room name from it.
        Object suffix = attributes.get("sessionIdSuffix");
        request.put("sessionId", context.sessionId().toString() + (suffix != null ? suffix.toString() : ""));
        request.put("patientId", context.participantId());
        request.put("providerId", context.participantId());
        request.put("facilityId", context.facilityId());
        // SessionMode carried by LiveRoomService from the event's LiveMode —
        // determines which session-template grant taxonomy governs the room.
        Object sessionMode = attributes.get("sessionMode");
        request.put("sessionType", sessionMode != null ? sessionMode.toString() : "LIVE_EVENT");
        // Stamp ownership so impilo.rtc.* consumers can filter (LIVE) and resolve (event id).
        request.put("owningService", "LIVE");
        if (context.eventId() != null) {
            request.put("owningRef", context.eventId().toString());
        }
        // Child-room linkage: rtc validates the parent exists (same tenant).
        Object parentSessionId = attributes.get("parentSessionId");
        if (parentSessionId != null) {
            request.put("parentSessionId", parentSessionId.toString());
        }
        // Event capacity → LiveKit room max_participants (rtc clamps sensibly).
        Object maxParticipants = attributes.get("maxParticipants");
        if (maxParticipants instanceof Number number && number.intValue() > 0) {
            request.put("maxParticipants", number.intValue());
        }
        request.put("attributes", attributes);
        Map<String, Object> participant = new LinkedHashMap<>();
        participant.put("identity", context.participantId());
        participant.put("role", context.participantRole());
        request.put("participant", participant);

        Map<String, Object> response = rtcGatewayClient.provisionSession(ctx, request);
        @SuppressWarnings("unchecked")
        Map<String, Object> data = response.get("data") instanceof Map<?, ?> raw
                ? (Map<String, Object>) raw : response;
        String roomId = str(data, "id", context.sessionId().toString());
        return new MediaRoomContext(
                context.tenantId(),
                context.eventId(),
                context.sessionId(),
                context.participantId(),
                context.participantRole(),
                context.facilityId(),
                roomId,
                providerType(),
                context.attributes());
    }

    @Override
    public MediaTokenResult issueToken(MediaRoomContext context) {
        TrustContext ctx = TrustContextHolder.get();
        // rtc-gateway's token contract wraps the caller in a participant object.
        Map<String, Object> participant = new LinkedHashMap<>();
        participant.put("identity", context.participantId());
        participant.put("role", context.participantRole());
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("participant", participant);
        Map<String, Object> response = rtcGatewayClient.issueToken(ctx, context.providerRoomId(), request);
        @SuppressWarnings("unchecked")
        Map<String, Object> data = response.get("data") instanceof Map<?, ?> raw
                ? (Map<String, Object>) raw : response;
        return new MediaTokenResult(
                str(data, "id", context.providerRoomId()),
                str(data, "roomUrl", null),
                str(data, "accessToken", null),
                data.get("tokenExpiresAt") != null
                        ? Instant.parse(data.get("tokenExpiresAt").toString()) : Instant.now().plusSeconds(3600),
                str(data, "provider", providerType()),
                str(data, "channel", "live"));
    }

    @Override
    public MediaHealthStatus checkHealth(String roomId) {
        TrustContext ctx = TrustContextHolder.get();
        try {
            Map<String, Object> response = rtcGatewayClient.getSession(ctx, roomId);
            @SuppressWarnings("unchecked")
            Map<String, Object> data = response.get("data") instanceof Map<?, ?> raw
                    ? (Map<String, Object>) raw : response;
            String status = str(data, "status", "UNKNOWN");
            return new MediaHealthStatus(roomId, status, !"ENDED".equals(status), providerType(), null);
        } catch (Exception ex) {
            log.warn("RTC health check failed for {}: {}", roomId, ex.getMessage());
            return new MediaHealthStatus(roomId, "UNHEALTHY", false, providerType(), ex.getMessage());
        }
    }

    @Override
    public void startSession(String roomId) {
        // RTC gateway provisions active sessions on create
    }

    @Override
    public MediaRoomContext endSession(String roomId) {
        TrustContext ctx = TrustContextHolder.get();
        rtcGatewayClient.endSession(ctx, roomId);
        return null;
    }

    @Override
    public String startRecording(String roomId) {
        TrustContext ctx = TrustContextHolder.get();
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("startedBy", ctx != null && ctx.actorId() != null ? ctx.actorId() : "live-service");
        request.put("startedByRole", ctx != null && ctx.actorType() != null ? ctx.actorType() : "SERVICE");
        Map<String, Object> data = unwrap(rtcGatewayClient.startRecording(ctx, roomId, request));
        String recordingRef = str(data, "recordingId", str(data, "egressId", null));
        if (recordingRef == null) {
            log.warn("RTC recording start for room {} returned no recordingId/egressId", roomId);
        }
        return recordingRef;
    }

    @Override
    public void stopRecording(String roomId, String recordingRef) {
        rtcGatewayClient.stopRecording(TrustContextHolder.get(), roomId);
        log.info("RTC recording stop requested roomId={} ref={}", roomId, recordingRef);
    }

    /**
     * Playback for rtc-gateway recordings is a time-limited signed URL minted by
     * document-service for the catalogued artifact. The recordingRef is the
     * document object id stamped onto the session by the replay pipeline; refs
     * that are not document object ids (recording still processing, or legacy
     * fabricated refs) have no playable artifact yet.
     */
    @Override
    public String getPlaybackUrl(String roomId, String recordingRef) {
        UUID documentObjectId = uuidOrNull(recordingRef);
        if (documentObjectId == null) {
            log.debug("Recording ref '{}' for room {} is not a catalogued document object — no playback URL",
                    recordingRef, roomId);
            return null;
        }
        TrustContext ctx = TrustContextHolder.get();
        Map<String, Object> data = unwrap(documentServiceClient.getSignedUrl(
                ctx != null ? ctx.tenantId() : null, documentObjectId.toString()));
        return str(data, "url", null);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> unwrap(Map<String, Object> response) {
        return response.get("data") instanceof Map<?, ?> raw ? (Map<String, Object>) raw : response;
    }

    private static UUID uuidOrNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String str(Map<String, Object> map, String key, String fallback) {
        Object v = map.get(key);
        return v != null ? v.toString() : fallback;
    }
}
