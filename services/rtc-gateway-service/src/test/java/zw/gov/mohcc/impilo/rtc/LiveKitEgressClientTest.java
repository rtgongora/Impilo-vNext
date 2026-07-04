package zw.gov.mohcc.impilo.rtc;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LiveKitEgressClientTest {

    private LiveKitEgressClient client() {
        RtcGatewayProperties props = new RtcGatewayProperties();
        props.getRecording().getS3().setAccessKey("preview-access");
        props.getRecording().getS3().setSecretKey("preview-secret");
        props.getRecording().getS3().setEndpoint("http://minio:9000");
        props.getRecording().getS3().setBucket("impilo-recordings");
        return new LiveKitEgressClient(props, new LiveKitTokenService(props));
    }

    @Test
    @SuppressWarnings("unchecked")
    void startRoomCompositeBodyCarriesRequestLevelS3Target() {
        Map<String, Object> body = client().startRoomCompositeBody(
                "impilo-telemedicine-session-1", "consult",
                "recordings/impilo-telemedicine-session-1-1750000000.mp4");

        assertEquals("impilo-telemedicine-session-1", body.get("room_name"));
        assertEquals("consult", body.get("layout"));

        List<Map<String, Object>> outputs = (List<Map<String, Object>>) body.get("file_outputs");
        assertEquals(1, outputs.size());
        Map<String, Object> fileOutput = outputs.get(0);
        assertEquals("recordings/impilo-telemedicine-session-1-1750000000.mp4", fileOutput.get("filepath"));

        Map<String, Object> s3 = (Map<String, Object>) fileOutput.get("s3");
        assertEquals("preview-access", s3.get("access_key"));
        assertEquals("preview-secret", s3.get("secret"));
        assertEquals("http://minio:9000", s3.get("endpoint"));
        assertEquals("impilo-recordings", s3.get("bucket"));
        assertEquals(true, s3.get("force_path_style"));
        assertEquals("us-east-1", s3.get("region"));
    }

    @Test
    void blankLayoutIsOmittedFromBody() {
        Map<String, Object> body = client().startRoomCompositeBody("room-1", " ", "recordings/x.mp4");

        assertNull(body.get("layout"));
    }

    @Test
    void filepathIsBucketRelativeMp4KeyedByRoom() {
        String filepath = client().filepath("impilo-live-event-1");

        assertTrue(filepath.startsWith("recordings/impilo-live-event-1-"));
        assertTrue(filepath.endsWith(".mp4"));
    }
}
