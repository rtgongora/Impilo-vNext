package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.experience.client.TusoServiceClient;
import zw.gov.mohcc.impilo.experience.client.VarapiServiceClient;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class RegistryControllerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void listProviders_mapsVarapiSearchResults() {
        RegistryController controller = new RegistryController(new StubVarapiClient(), new TusoServiceClient(new RestTemplate(), endpoints()));
        ResponseEntity<Map<String, Object>> response =
                controller.listProviders("tenant-1", "req-1", "corr-1", 0, 20, "ncube", "ACTIVE");

        assertEquals(200, response.getStatusCode().value());
        List<?> data = (List<?>) response.getBody().get("data");
        assertFalse(data.isEmpty());
    }

    @Test
    void listProviders_returnsEmptyWhenVarapiFails() {
        RegistryController controller = new RegistryController(new FailingVarapiClient(), new TusoServiceClient(new RestTemplate(), endpoints()));
        ResponseEntity<Map<String, Object>> response =
                controller.listProviders("tenant-1", "req-2", "corr-2", 0, 20, null, null);

        assertEquals(200, response.getStatusCode().value());
        assertEquals(0, ((List<?>) response.getBody().get("data")).size());
    }

    private static ServiceClientConfig.ServiceEndpoints endpoints() {
        return ServiceClientConfig.testServiceEndpoints();
    }

    private static final class StubVarapiClient extends VarapiServiceClient {
        StubVarapiClient() {
            super(new RestTemplate(), endpoints());
        }

        @Override
        public JsonNode searchProviders(Map<String, Object> body) {
            ObjectNode page = MAPPER.createObjectNode();
            ArrayNode items = MAPPER.createArrayNode();
            items.add(MAPPER.createObjectNode()
                    .put("providerPublicId", "PRV-1")
                    .put("givenName", "Tariro")
                    .put("familyName", "Ncube")
                    .put("status", "ACTIVE")
                    .put("profession", "MEDICAL_PRACTITIONER"));
            page.set("items", items);
            return page;
        }
    }

    private static final class FailingVarapiClient extends VarapiServiceClient {
        FailingVarapiClient() {
            super(new RestTemplate(), endpoints());
        }

        @Override
        public JsonNode searchProviders(Map<String, Object> body) {
            throw new RuntimeException("varapi down");
        }
    }
}
