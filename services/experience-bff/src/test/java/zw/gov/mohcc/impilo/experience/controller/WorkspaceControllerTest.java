package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.experience.client.TusoServiceClient;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class WorkspaceControllerTest {

    private static final ObjectMapper mapper = new ObjectMapper();

    @Test
    void listWorkspaces_returnsSeededFallbackWhenTusoUnavailable() {
        WorkspaceController controller = new WorkspaceController(new StubTusoClient());
        ResponseEntity<Map<String, Object>> response =
                controller.listWorkspaces("tenant-1", "req-1", "corr-1", "fac-hch");
        assertEquals(200, response.getStatusCode().value());
        assertFalse(((java.util.List<?>) response.getBody().get("data")).isEmpty());
    }

    private static final class StubTusoClient extends TusoServiceClient {
        StubTusoClient() {
            super(new RestTemplate(), ServiceClientConfig.testServiceEndpoints());
        }

        @Override
        public JsonNode listWorkspaces(long facilityId) {
            return mapper.createArrayNode();
        }
    }
}
