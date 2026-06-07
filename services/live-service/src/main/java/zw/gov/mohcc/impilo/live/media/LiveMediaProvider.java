package zw.gov.mohcc.impilo.live.media;

/**
 * Abstraction over rtc-gateway (production) and local dev tokens.
 */
public interface LiveMediaProvider {

    String providerType();

    MediaRoomContext provisionRoom(MediaRoomContext context);

    MediaTokenResult issueToken(MediaRoomContext context);

    MediaHealthStatus checkHealth(String roomId);

    void startSession(String roomId);

    MediaRoomContext endSession(String roomId);

    String startRecording(String roomId);

    void stopRecording(String roomId, String recordingRef);
}
