package zw.gov.mohcc.impilo.experience.client;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Proves the EmONC-readiness client targets tuso's internal path and unwraps {@code data}. Path and
 * verb only — container-free, no Spring context.
 */
class TusoServiceClientEmoncTest {

    private static final String BASE = ServiceClientConfig.UNREACHABLE_TEST_ENDPOINT;

    @Test
    void emoncReadinessHitsTheInternalPathAndUnwrapsData() {
        RestTemplate rt = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(rt).build();
        server.expect(requestTo(BASE + "/v1/internal/facilities/emonc-readiness/42"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"data\":{\"verdict\":\"INSUFFICIENT_EVIDENCE\"}}",
                        MediaType.APPLICATION_JSON));

        TusoServiceClient client = new TusoServiceClient(rt, ServiceClientConfig.testServiceEndpoints());
        JsonNode data = client.getFacilityEmoncReadiness(42L);

        assertEquals("INSUFFICIENT_EVIDENCE", data.get("verdict").asText());
        server.verify();
    }
}
