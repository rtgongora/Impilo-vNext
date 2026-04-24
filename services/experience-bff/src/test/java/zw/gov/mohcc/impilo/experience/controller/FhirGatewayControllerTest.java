package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.experience.client.FhirGatewayServiceClient;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class FhirGatewayControllerTest {

    private static final ObjectMapper mapper = new ObjectMapper();

    @Test
    void getCapabilityStatement_returnsData() {
        FhirInteropController controller = new FhirInteropController(new StubFhirClient());
        ResponseEntity<Map<String, Object>> response =
                controller.getCapabilityStatement("req-1", "corr-1");
        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody().get("data"));
    }

    @Test
    void searchFhirResource_returnsData() {
        FhirInteropController controller = new FhirInteropController(new StubFhirClient());
        ResponseEntity<Map<String, Object>> response =
                controller.searchFhirResource("Patient", "name=Smith", "req-2", "corr-2");
        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody().get("data"));
    }

    @Test
    void getFhirResource_returnsData() {
        FhirInteropController controller = new FhirInteropController(new StubFhirClient());
        ResponseEntity<Map<String, Object>> response =
                controller.getFhirResource("Patient", "123", "req-3", "corr-3");
        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody().get("data"));
    }

    private static ServiceClientConfig.ServiceEndpoints endpoints() {
        return new ServiceClientConfig.ServiceEndpoints(
                null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null
        );
    }

    private static final class StubFhirClient extends FhirGatewayServiceClient {
        StubFhirClient() { super(new RestTemplate(), endpoints()); }

        @Override public JsonNode getCapabilityStatement() {
            ObjectNode node = mapper.createObjectNode();
            node.put("resourceType", "CapabilityStatement");
            node.put("status", "active");
            return node;
        }

        @Override public JsonNode searchResource(String resourceType, String params) {
            ObjectNode bundle = mapper.createObjectNode();
            bundle.put("resourceType", "Bundle");
            bundle.put("type", "searchset");
            bundle.put("total", 0);
            return bundle;
        }

        @Override public JsonNode getResource(String resourceType, String id) {
            ObjectNode node = mapper.createObjectNode();
            node.put("resourceType", resourceType);
            node.put("id", id);
            return node;
        }
    }
}
