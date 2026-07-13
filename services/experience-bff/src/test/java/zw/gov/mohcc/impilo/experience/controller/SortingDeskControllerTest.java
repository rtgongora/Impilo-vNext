package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.experience.client.PctServiceClient;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class SortingDeskControllerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private PctServiceClient stub(JsonNode sortResult) {
        return new PctServiceClient(new RestTemplate(), ServiceClientConfig.testServiceEndpoints(), MAPPER) {
            @Override public JsonNode sortJourney(String journeyId, Map<String, Object> body) { return sortResult; }
        };
    }

    @Test
    void sort_returns201WithDataAndMeta() {
        JsonNode result = MAPPER.createObjectNode().put("journeyId", "J-1").put("visitType", "ED_TRAUMA");
        SortingDeskController controller = new SortingDeskController(stub(result));

        var response = controller.sort("J-1",
                Map.of("context", "CASUALTY", "visitType", "ED_TRAUMA"), "req-1", "corr-1");

        assertEquals(201, response.getStatusCode().value());
        assertNotNull(response.getBody().get("data"));
        assertEquals("req-1", ((Map<?, ?>) response.getBody().get("meta")).get("request_id"));
    }

    @Test
    void sort_passesThroughUpstream4xx() {
        PctServiceClient failing = new PctServiceClient(new RestTemplate(), ServiceClientConfig.testServiceEndpoints(), MAPPER) {
            @Override public JsonNode sortJourney(String journeyId, Map<String, Object> body) {
                throw org.springframework.web.client.HttpClientErrorException.create(
                        org.springframework.http.HttpStatus.BAD_REQUEST, "Bad Request",
                        org.springframework.http.HttpHeaders.EMPTY, "unknown visit type".getBytes(), null);
            }
        };
        SortingDeskController controller = new SortingDeskController(failing);

        var response = controller.sort("J-1", Map.of("context", "X"), "req-2", "corr-2");

        assertEquals(400, response.getStatusCode().value());
        assertEquals("SORT_REJECTED", ((Map<?, ?>) response.getBody().get("error")).get("code"));
    }
}
