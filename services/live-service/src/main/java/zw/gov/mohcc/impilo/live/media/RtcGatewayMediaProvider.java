package zw.gov.mohcc.impilo.live.media;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import zw.gov.mohcc.impilo.live.integration.RtcGatewayClient;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

public class RtcGatewayMediaProvider implements LiveMediaProvider {

    private static final Logger log = LoggerFactory.getLogger(RtcGatewayMediaProvider.class);

    private final RtcGatewayClient rtcGatewayClient;

    public RtcGatewayMediaProvider(RtcGatewayClient rtcGatewayClient) {
        this.rtcGatewayClient = rtcGatewayClient;
    }

    @Override
    public String providerType() {
        return "RTC_GATEWAY";
    }

    @Override
    public MediaRoomContext provisionRoom(MediaRoomContext context) {
        TrustContext ctx = TrustContextHolder.get();
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("tenantId", context.tenantId().toString());
        request.put("sessionId", context.sessionId().toString());
        request.put("patientId", context.participantId());
        request.put("providerId", context.participantId());
        request.put("facilityId", context.facilityId());
        request.put("sessionType", "LIVE_EVENT");
        request.put("attributes", context.attributes() != null ? context.attributes() : Map.of());
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
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("identity", context.participantId());
        request.put("role", context.participantRole());
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
        return "rtc-recording-" + roomId;
    }

    @Override
    public void stopRecording(String roomId, String recordingRef) {
        log.info("Recording stopped roomId={} ref={}", roomId, recordingRef);
    }

    private static String str(Map<String, Object> map, String key, String fallback) {
        Object v = map.get(key);
        return v != null ? v.toString() : fallback;
    }
}
