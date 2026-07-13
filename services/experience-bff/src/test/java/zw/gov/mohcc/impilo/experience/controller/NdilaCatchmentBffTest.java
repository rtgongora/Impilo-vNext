package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.experience.client.NdilaServiceClient;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class NdilaCatchmentBffTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private NdilaServiceClient stub(JsonNode result) {
        return new NdilaServiceClient(new RestTemplate(),
                ServiceClientConfig.testServiceEndpoints(), MAPPER) {
            @Override public JsonNode catchmentCheckPoint(Map<String, Object> body) { return result; }
        };
    }

    @Test
    void checkPoint_wrapsCatchmentResolutionFromNdila() {
        JsonNode matches = MAPPER.createArrayNode().add(MAPPER.createObjectNode().put("id", "c-1").put("name", "Ward 7"));
        NdilaController controller = new NdilaController(stub(matches));

        var response = controller.catchmentCheckPoint(
                Map.of("point", Map.of("latitude", "-17.8", "longitude", "31.0")), "req-1", "corr-1");

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody().get("data"));
        assertEquals("req-1", ((Map<?, ?>) response.getBody().get("meta")).get("request_id"));
    }

    @Test
    void checkPoint_returnsBadGatewayWhenNdilaUnavailable() {
        NdilaController controller = new NdilaController(new NdilaServiceClient(new RestTemplate(),
                ServiceClientConfig.testServiceEndpoints(), MAPPER) {
            @Override public JsonNode catchmentCheckPoint(Map<String, Object> body) {
                throw new RuntimeException("ndila down");
            }
        });

        var response = controller.catchmentCheckPoint(Map.of("point", Map.of()), "req-2", "corr-2");

        assertEquals(502, response.getStatusCode().value());
        assertEquals("NDILA_UNAVAILABLE", ((Map<?, ?>) response.getBody().get("error")).get("code"));
    }
}
