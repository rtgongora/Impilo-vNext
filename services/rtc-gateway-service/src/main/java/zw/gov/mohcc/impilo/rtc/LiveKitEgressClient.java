package zw.gov.mohcc.impilo.rtc;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Twirp client for the LiveKit Egress API (served by livekit-server, which
 * dispatches to egress workers over redis). Room-composite recordings write
 * straight to the request-level S3 target (preview: in-chart MinIO) — the
 * egress worker's own config is only a fallback, so the gateway stays the
 * single source of truth for artifact placement.
 */
@Service
public class LiveKitEgressClient {

    private static final Logger log = LoggerFactory.getLogger(LiveKitEgressClient.class);

    private final RtcGatewayProperties properties;
    private final LiveKitTokenService tokenService;
    private final RestTemplate restTemplate = new RestTemplate();

    public LiveKitEgressClient(RtcGatewayProperties properties, LiveKitTokenService tokenService) {
        this.properties = properties;
        this.tokenService = tokenService;
    }

    /** Result of a StartRoomCompositeEgress call. */
    public record EgressStart(String egressId, String filepath) {
    }

    public EgressStart startRoomComposite(String roomName, String layout) {
        String filepath = filepath(roomName);
        Map<String, Object> body = startRoomCompositeBody(roomName, layout, filepath);
        ResponseEntity<Map> response = restTemplate.postForEntity(
                liveKitUrl(properties.getLivekit().getStartRoomCompositeEgressPath()),
                new HttpEntity<>(body, headers(roomName)),
                Map.class);
        String egressId = egressId(response.getBody());
        if (egressId == null) {
            throw new IllegalStateException("LiveKit egress start returned no egressId for room " + roomName);
        }
        log.info("Started room-composite egress {} for room {} -> s3://{}/{}",
                egressId, roomName, properties.getRecording().getS3().getBucket(), filepath);
        return new EgressStart(egressId, filepath);
    }

    public void stopEgress(String egressId) {
        restTemplate.postForEntity(
                liveKitUrl(properties.getLivekit().getStopEgressPath()),
                new HttpEntity<>(Map.of("egress_id", egressId), headers(null)),
                Map.class);
        log.info("Requested stop for egress {}", egressId);
    }

    /**
     * Twirp StartRoomCompositeEgress body: file output with a request-level S3
     * destination (access_key/secret/endpoint/bucket/force_path_style/region).
     */
    Map<String, Object> startRoomCompositeBody(String roomName, String layout, String filepath) {
        RtcGatewayProperties.Recording.S3 s3 = properties.getRecording().getS3();
        Map<String, Object> s3Upload = new LinkedHashMap<>();
        s3Upload.put("access_key", s3.getAccessKey());
        s3Upload.put("secret", s3.getSecretKey());
        s3Upload.put("endpoint", s3.getEndpoint());
        s3Upload.put("bucket", s3.getBucket());
        s3Upload.put("force_path_style", s3.isForcePathStyle());
        s3Upload.put("region", s3.getRegion());

        Map<String, Object> fileOutput = new LinkedHashMap<>();
        fileOutput.put("filepath", filepath);
        fileOutput.put("s3", s3Upload);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("room_name", roomName);
        if (layout != null && !layout.isBlank()) {
            body.put("layout", layout);
        }
        body.put("file_outputs", List.of(fileOutput));
        return body;
    }

    /** Deterministic artifact key so the recordings row knows its target upfront. */
    String filepath(String roomName) {
        return "recordings/" + roomName + "-" + Instant.now().getEpochSecond() + ".mp4";
    }

    private String egressId(Map<?, ?> responseBody) {
        if (responseBody == null) {
            return null;
        }
        Object egressId = responseBody.get("egressId");
        if (egressId == null) {
            egressId = responseBody.get("egress_id");
        }
        return egressId == null ? null : egressId.toString();
    }

    private HttpHeaders headers(String roomName) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(tokenService.issueRoomRecordToken(roomName));
        return headers;
    }

    private String liveKitUrl(String path) {
        String base = properties.getLivekit().getUrl();
        String suffix = path == null || path.isBlank() ? "" : path;
        if (base.endsWith("/") && suffix.startsWith("/")) {
            return base.substring(0, base.length() - 1) + suffix;
        }
        if (!base.endsWith("/") && !suffix.startsWith("/")) {
            return base + "/" + suffix;
        }
        return base + suffix;
    }
}
