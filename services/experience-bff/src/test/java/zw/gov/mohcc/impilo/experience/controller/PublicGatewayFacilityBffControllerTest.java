package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.experience.client.TusoServiceClient;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PublicGatewayFacilityBffControllerTest {

    private static final ObjectMapper mapper = new ObjectMapper();

    @Test
    void search_passesFiltersAndCapsPageSize() {
        StubClient stub = new StubClient();
        PublicGatewayFacilityBffController controller = new PublicGatewayFacilityBffController(stub);
        ResponseEntity<JsonNode> response =
                controller.search("clinic", null, "Harare", null, null, 0, 500);
        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("clinic", stub.lastSearchParams.get("query"));
        assertEquals("Harare", stub.lastSearchParams.get("province"));
        assertNull(stub.lastSearchParams.get("district"));
        assertEquals("50", stub.lastSearchParams.get("size"));
    }

    @Test
    void search_blankParamsAreDropped() {
        StubClient stub = new StubClient();
        PublicGatewayFacilityBffController controller = new PublicGatewayFacilityBffController(stub);
        controller.search("  ", "", null, "  Mazowe ", null, -3, 0);
        assertNull(stub.lastSearchParams.get("query"));
        assertNull(stub.lastSearchParams.get("facilityType"));
        assertEquals("Mazowe", stub.lastSearchParams.get("district"));
        assertEquals("0", stub.lastSearchParams.get("page"));
        assertEquals("1", stub.lastSearchParams.get("size"));
    }

    @Test
    void profile_returnsBody() {
        PublicGatewayFacilityBffController controller = new PublicGatewayFacilityBffController(new StubClient());
        ResponseEntity<JsonNode> response = controller.profile(42L);
        assertEquals(200, response.getStatusCode().value());
        assertEquals(42L, response.getBody().get("id").asLong());
    }

    @Test
    void profile_notFoundMapsTo404() {
        StubClient stub = new StubClient();
        stub.notFound = true;
        PublicGatewayFacilityBffController controller = new PublicGatewayFacilityBffController(stub);
        assertEquals(404, controller.profile(7L).getStatusCode().value());
    }

    @Test
    void search_downstreamFailureMapsTo502() {
        StubClient stub = new StubClient();
        stub.explode = true;
        PublicGatewayFacilityBffController controller = new PublicGatewayFacilityBffController(stub);
        assertEquals(502, controller.search(null, null, null, null, null, 0, 20).getStatusCode().value());
    }

    private static ServiceClientConfig.ServiceEndpoints endpoints() {
        return ServiceClientConfig.testServiceEndpoints();
    }

    private static final class StubClient extends TusoServiceClient {
        Map<String, String> lastSearchParams = new HashMap<>();
        boolean notFound;
        boolean explode;

        StubClient() { super(new RestTemplate(), endpoints()); }

        @Override public JsonNode publicFacilitySearch(Map<String, String> params) {
            if (explode) throw new RuntimeException("downstream unavailable");
            lastSearchParams = new HashMap<>(params);
            ObjectNode node = mapper.createObjectNode();
            node.putArray("content");
            return node;
        }

        @Override public JsonNode publicFacilityProfile(long id) {
            if (notFound) {
                throw HttpClientErrorException.NotFound.create(
                        org.springframework.http.HttpStatus.NOT_FOUND, "not found",
                        org.springframework.http.HttpHeaders.EMPTY, new byte[0], StandardCharsets.UTF_8);
            }
            ObjectNode node = mapper.createObjectNode();
            node.put("id", id);
            node.put("name", "Example Clinic");
            return node;
        }
    }
}
