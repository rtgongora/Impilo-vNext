package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.experience.client.TusoServiceClient;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Proxy + write-protection tests for the Facility Configuration Console admin routes. Uses a subclassed
 * stub TusoServiceClient (no live stack). RBAC for {@code /internal/v1/admin/**} is enforced by
 * {@code SecurityConfig} (verified elsewhere); these tests assert envelope shape and 4xx passthrough.
 */
class AdminFacilityConfigurationControllerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final long FAC = 42L;

    @Test
    void configurationWrapsTusoDataInEnvelope() {
        var controller = new AdminFacilityConfigurationController(new StubTusoClient());
        ResponseEntity<Map<String, Object>> response = controller.configuration(FAC, "req-1", "corr-1");
        assertEquals(200, response.getStatusCode().value());
        JsonNode data = (JsonNode) response.getBody().get("data");
        assertEquals("CONFIGURED", data.path("configurationState").asText());
        assertNotNull(response.getBody().get("meta"));
    }

    @Test
    void setupChecklistProxies() {
        var controller = new AdminFacilityConfigurationController(new StubTusoClient());
        ResponseEntity<Map<String, Object>> response = controller.setupChecklist(FAC, "req-1", "corr-1");
        assertEquals(200, response.getStatusCode().value());
        JsonNode data = (JsonNode) response.getBody().get("data");
        assertTrue(data.path("items").isArray());
    }

    @Test
    void queueDefinitionsProxiesReadOnly() {
        var controller = new AdminFacilityConfigurationController(new StubTusoClient());
        ResponseEntity<Map<String, Object>> response = controller.queueDefinitions(FAC, "req-1", "corr-1");
        assertEquals(200, response.getStatusCode().value());
        assertTrue(((JsonNode) response.getBody().get("data")).isArray());
    }

    @Test
    void configurationNotFoundWhenTusoReturnsNull() {
        var controller = new AdminFacilityConfigurationController(new NullTusoClient());
        ResponseEntity<Map<String, Object>> response = controller.configuration(99L, "req-1", "corr-1");
        assertEquals(404, response.getStatusCode().value());
    }

    @Test
    void createServicePointProxiesWrite() {
        var controller = new AdminFacilityConfigurationController(new StubTusoClient());
        ResponseEntity<Map<String, Object>> response =
                controller.createServicePoint(FAC, Map.of("name", "Triage"), "req-1", "corr-1");
        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody().get("data"));
    }

    @Test
    void writeValidationErrorIsPropagatedNotMaskedAs502() {
        var controller = new AdminFacilityConfigurationController(new ConflictTusoClient());
        ResponseEntity<Map<String, Object>> response =
                controller.createServicePoint(FAC, Map.of(), "req-1", "corr-1");
        assertEquals(400, response.getStatusCode().value());
        assertTrue(response.getBody().containsKey("error"));
    }

    @Test
    void retireWorkspaceProxiesWrite() {
        var controller = new AdminFacilityConfigurationController(new StubTusoClient());
        ResponseEntity<Map<String, Object>> response =
                controller.retireWorkspace(FAC, "ws-1", "req-1", "corr-1");
        assertEquals(200, response.getStatusCode().value());
    }

    private static ServiceClientConfig.ServiceEndpoints endpoints() {
        return ServiceClientConfig.testServiceEndpoints();
    }

    private static JsonNode node(String json) {
        try {
            return MAPPER.readTree(json);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static class StubTusoClient extends TusoServiceClient {
        StubTusoClient() { super(new RestTemplate(), endpoints()); }

        @Override
        public JsonNode getFacilityConfiguration(long facilityId) {
            return node("{\"facilityId\":" + facilityId + ",\"configurationState\":\"CONFIGURED\","
                    + "\"setupComplete\":true,\"activeServicePointCount\":2,\"missingSections\":[]}");
        }

        @Override
        public JsonNode getFacilitySetupChecklist(long facilityId) {
            return node("{\"facilityId\":" + facilityId + ",\"setupComplete\":true,"
                    + "\"items\":[{\"key\":\"facility_code\",\"status\":\"COMPLETE\"}]}");
        }

        @Override
        public JsonNode getFacilityConfigQueueDefinitions(long facilityId) {
            return node("[{\"sourceRef\":\"sp-1\",\"displayName\":\"Triage\",\"active\":true}]");
        }

        @Override
        public JsonNode createServicePoint(long facilityId, Map<String, Object> body) {
            return node("{\"id\":\"sp-1\",\"name\":\"Triage\",\"active\":true}");
        }

        @Override
        public JsonNode retireWorkspace(String workspaceId) {
            return node("{\"id\":\"" + workspaceId + "\",\"active\":false}");
        }
    }

    private static final class NullTusoClient extends StubTusoClient {
        @Override
        public JsonNode getFacilityConfiguration(long facilityId) {
            return null;
        }
    }

    private static final class ConflictTusoClient extends StubTusoClient {
        @Override
        public JsonNode createServicePoint(long facilityId, Map<String, Object> body) {
            throw HttpClientErrorException.create(HttpStatus.BAD_REQUEST, "Bad Request",
                    HttpHeaders.EMPTY, new byte[0], null);
        }
    }
}
