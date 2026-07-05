package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.experience.client.DocumentServiceClient;
import zw.gov.mohcc.impilo.experience.client.LearningServiceClient;
import zw.gov.mohcc.impilo.experience.client.NotificationServiceClient;
import zw.gov.mohcc.impilo.experience.config.LearningServiceRuntimeProperties;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LearningControllerTest {

    private static LearningController controller() {
        return new LearningController(
                new StubLearningClient(),
                new StubNotificationClient(),
                new StubDocumentClient(),
                new RestTemplate(),
                "http://localhost:8265");
    }

    @Test
    void v11Catalog_returnsProxyEnvelope() {
        ResponseEntity<Map<String, Object>> response =
                controller().v11Catalog("tenant-1", null, null, null, null, null, null, 5);
        assertEquals(200, response.getStatusCode().value());
    }

    @Test
    void myLearning_returnsProxyEnvelope() {
        ResponseEntity<Map<String, Object>> response =
                controller().myLearning("tenant-1", "PERSON", "actor-1");
        assertEquals(200, response.getStatusCode().value());
    }

    @Test
    void v11MetadataLanguages_returnsProxyEnvelope() {
        ResponseEntity<Map<String, Object>> response = controller().v11MetadataLanguages("tenant-1");
        assertEquals(200, response.getStatusCode().value());
        assertEquals(true, response.getBody().containsKey("data"));
    }

    /** W3: the live-linkage join fields flow through the sessions passthrough unmodified. */
    @Test
    void listSessions_passesLiveLinkageFieldsThrough() {
        ResponseEntity<Map<String, Object>> response = controller().listSessions("tenant-1", 50);
        assertEquals(200, response.getStatusCode().value());
        JsonNode data = (JsonNode) response.getBody().get("data");
        JsonNode item = data.get("items").get(0);
        assertEquals("LIVE", item.get("sessionMode").asText());
        assertEquals("11111111-1111-1111-1111-111111111111", item.get("liveEventId").asText());
        assertEquals("/live/event/11111111-1111-1111-1111-111111111111", item.get("joinPath").asText());
    }

    @Test
    void completionRules_roundTripThroughPassthrough() {
        LearningController controller = controller();
        ResponseEntity<Map<String, Object>> listed =
                controller.listCompletionRules("tenant-1", "22222222-2222-2222-2222-222222222222");
        assertEquals(200, listed.getStatusCode().value());
        assertEquals(true, listed.getBody().containsKey("data"));

        ResponseEntity<Map<String, Object>> upserted = controller.upsertCompletionRule(
                "tenant-1", "22222222-2222-2222-2222-222222222222",
                Map.of("ruleType", "ATTENDANCE_THRESHOLD", "thresholdValue", 30));
        assertEquals(200, upserted.getStatusCode().value());

        ResponseEntity<Map<String, Object>> confirmed =
                controller.facilitatorConfirm("tenant-1", "33333333-3333-3333-3333-333333333333");
        assertEquals(200, confirmed.getStatusCode().value());
        assertEquals(true, confirmed.getBody().containsKey("data"));
    }

    /** W4: DOCUMENT_OBJECT storage refs are exchanged for document-service signed URLs. */
    @Test
    void mediaPlayback_exchangesDocumentObjectForSignedUrl() {
        ResponseEntity<Map<String, Object>> response = controller().mediaPlayback(
                "tenant-1", StubLearningClient.DOC_ASSET_ID, "44444444-4444-4444-4444-444444444444");
        assertEquals(200, response.getStatusCode().value());
        JsonNode data = (JsonNode) response.getBody().get("data");
        assertEquals("https://minio.example/signed/replay.mp4", data.get("playbackUrl").asText());
        assertEquals("DOCUMENT_OBJECT", data.get("asset").get("storageKind").asText());
        assertTrue(data.hasNonNull("expiresAt"));
    }

    /** W4: legacy EXTERNAL_URL assets return their storage ref directly. */
    @Test
    void mediaPlayback_passesLegacyExternalUrlThrough() {
        ResponseEntity<Map<String, Object>> response = controller().mediaPlayback(
                "tenant-1", StubLearningClient.URL_ASSET_ID, "44444444-4444-4444-4444-444444444444");
        assertEquals(200, response.getStatusCode().value());
        JsonNode data = (JsonNode) response.getBody().get("data");
        assertEquals("https://cdn.example/lesson.mp4", data.get("playbackUrl").asText());
    }

    @Test
    void mediaWatchProgress_roundTripThroughPassthrough() {
        LearningController controller = controller();
        ResponseEntity<Map<String, Object>> upserted = controller.upsertMediaWatchProgress(
                "tenant-1", StubLearningClient.DOC_ASSET_ID,
                Map.of("enrolmentId", "44444444-4444-4444-4444-444444444444",
                        "positionSeconds", 120, "watchedSeconds", 120, "durationSeconds", 600));
        assertEquals(200, upserted.getStatusCode().value());

        ResponseEntity<Map<String, Object>> chapters =
                controller.listMediaChapters("tenant-1", StubLearningClient.DOC_ASSET_ID);
        assertEquals(200, chapters.getStatusCode().value());
        assertEquals(true, chapters.getBody().containsKey("data"));

        ResponseEntity<Map<String, Object>> deleted = controller.deleteMediaBookmark(
                "tenant-1", UUID.randomUUID().toString(), "44444444-4444-4444-4444-444444444444");
        assertEquals(200, deleted.getStatusCode().value());
    }

    private static final class StubLearningClient extends LearningServiceClient {
        private static final ObjectMapper mapper = new ObjectMapper();

        static final String DOC_ASSET_ID = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa";
        static final String URL_ASSET_ID = "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb";
        static final String DOC_OBJECT_ID = "cccccccc-cccc-cccc-cccc-cccccccccccc";

        StubLearningClient() {
            super(new RestTemplate(), learningProps());
        }

        @Override
        public JsonNode getV11(String relativePath, Map<String, Object> queryParams) {
            if ("metadata/languages".equals(relativePath)) {
                return mapper.createObjectNode()
                        .set("items", mapper.createArrayNode()
                                .add(mapper.createObjectNode()
                                        .put("code", "en")
                                        .put("label", "English")
                                        .put("nativeLabel", "English")));
            }
            if ("sessions".equals(relativePath)) {
                return mapper.createObjectNode()
                        .set("items", mapper.createArrayNode()
                                .add(mapper.createObjectNode()
                                        .put("id", "s-1")
                                        .put("sessionMode", "LIVE")
                                        .put("liveEventId", "11111111-1111-1111-1111-111111111111")
                                        .put("joinPath", "/live/event/11111111-1111-1111-1111-111111111111")));
            }
            if (("media/" + DOC_ASSET_ID + "/playback-ref").equals(relativePath)) {
                return mapper.createObjectNode()
                        .set("asset", mapper.createObjectNode()
                                .put("id", DOC_ASSET_ID)
                                .put("storageKind", "DOCUMENT_OBJECT")
                                .put("storageRef", DOC_OBJECT_ID)
                                .put("mimeType", "video/mp4"));
            }
            if (("media/" + URL_ASSET_ID + "/playback-ref").equals(relativePath)) {
                return mapper.createObjectNode()
                        .set("asset", mapper.createObjectNode()
                                .put("id", URL_ASSET_ID)
                                .put("storageKind", "EXTERNAL_URL")
                                .put("storageRef", "https://cdn.example/lesson.mp4"));
            }
            return mapper.createObjectNode().put("status", "OK");
        }

        @Override
        public JsonNode postV11(String relativePath, Map<String, Object> body) {
            return mapper.createObjectNode().put("status", "OK").put("path", relativePath);
        }

        @Override
        public JsonNode deleteV11(String relativePath, Map<String, Object> queryParams) {
            return mapper.createObjectNode().put("deleted", true).put("path", relativePath);
        }

        @Override
        public JsonNode getV11Catalog(
                String status, String category, String level, Boolean cpdEligible, Boolean mandatory, String language, int limit) {
            return mapper.createObjectNode().put("limit", limit).set("items", mapper.createArrayNode());
        }

        private static LearningServiceRuntimeProperties learningProps() {
            LearningServiceRuntimeProperties props = new LearningServiceRuntimeProperties();
            props.setBaseUrl("http://localhost:8101");
            return props;
        }
    }

    private static final class StubDocumentClient extends DocumentServiceClient {
        private static final ObjectMapper mapper = new ObjectMapper();

        StubDocumentClient() {
            super(new RestTemplate(), ServiceClientConfig.testServiceEndpoints());
        }

        @Override
        public JsonNode getSignedUrl(UUID objectId) {
            return mapper.createObjectNode()
                    .put("url", "https://minio.example/signed/replay.mp4")
                    .put("token", "t-1")
                    .put("expiresAt", "2026-07-05T12:00:00Z");
        }
    }

    private static final class StubNotificationClient extends NotificationServiceClient {
        StubNotificationClient() {
            super(new RestTemplate(), ServiceClientConfig.testServiceEndpoints());
        }
    }
}
