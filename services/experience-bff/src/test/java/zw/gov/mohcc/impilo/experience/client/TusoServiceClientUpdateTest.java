package zw.gov.mohcc.impilo.experience.client;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Proves the BFF service-point update targets TUSO's POST update alias rather than PATCH. The shared
 * RestTemplate is backed by {@code SimpleClientHttpRequestFactory}, which cannot issue PATCH at runtime,
 * so the client must use POST {@code /service-points/{id}/update}.
 */
class TusoServiceClientUpdateTest {

    // What this test is about is the path and the verb, not the host. It takes the host from the
    // same helper the client is built from, so it cannot drift and does not name a live dev port.
    private static final String BASE =
            ServiceClientConfig.UNREACHABLE_TEST_ENDPOINT + "/v1/internal/facilities";

    private TusoServiceClient client(RestTemplate rt) {
        return new TusoServiceClient(rt, ServiceClientConfig.testServiceEndpoints());
    }

    @Test
    void updateServicePointUsesPostUpdateAliasAndUnwrapsData() {
        RestTemplate rt = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(rt).build();
        server.expect(requestTo(BASE + "/7/service-points/sp-1/update"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"data\":{\"id\":\"sp-1\",\"name\":\"New Name\"}}",
                        MediaType.APPLICATION_JSON));

        JsonNode data = client(rt).updateServicePoint(7L, "sp-1", Map.of("name", "New Name"));
        assertEquals("New Name", data.get("name").asText());
        server.verify();
    }
}
