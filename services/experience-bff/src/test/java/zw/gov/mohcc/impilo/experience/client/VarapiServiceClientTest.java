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

class VarapiServiceClientTest {

    @Test
    void createProviderPostsToCanonicalVarapiEndpoint() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        ServiceClientConfig.ServiceEndpoints endpoints = ServiceClientConfig.testEndpointsStandardWireMocks();
        VarapiServiceClient client = new VarapiServiceClient(restTemplate, endpoints);

        server.expect(requestTo("http://varapi/v1/internal/providers"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(
                        "{\"data\":{\"providerPublicId\":\"VAR-2026-001\",\"givenName\":\"Tariro\"}}",
                        MediaType.APPLICATION_JSON
                ));

        JsonNode result = client.createProvider(Map.of(
                "givenName", "Tariro",
                "familyName", "Moyo",
                "profession", "GENERAL_PRACTITIONER"
        ));

        assertEquals("VAR-2026-001", result.get("providerPublicId").asText());
        assertEquals("Tariro", result.get("givenName").asText());
        server.verify();
    }

    @Test
    void publicFacilityPractitionersUnwrapsApiResponseArray() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        ServiceClientConfig.ServiceEndpoints endpoints = ServiceClientConfig.testEndpointsStandardWireMocks();
        VarapiServiceClient client = new VarapiServiceClient(restTemplate, endpoints);

        server.expect(requestTo("http://varapi/v1/public/facilities/42/practitioners"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(
                        "{\"data\":[{\"displayName\":\"Dr Tariro Moyo\",\"profession\":\"GENERAL_PRACTITIONER\","
                                + "\"registerStatus\":\"REGISTERED\"}]}",
                        MediaType.APPLICATION_JSON
                ));

        JsonNode result = client.publicFacilityPractitioners(42L);

        assertEquals(1, result.size());
        assertEquals("Dr Tariro Moyo", result.get(0).get("displayName").asText());
        server.verify();
    }

    @Test
    void publicFacilityPractitionersUnwrapsEmptyListOnMiss() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        ServiceClientConfig.ServiceEndpoints endpoints = ServiceClientConfig.testEndpointsStandardWireMocks();
        VarapiServiceClient client = new VarapiServiceClient(restTemplate, endpoints);

        server.expect(requestTo("http://varapi/v1/public/facilities/999/practitioners"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"data\":[]}", MediaType.APPLICATION_JSON));

        JsonNode result = client.publicFacilityPractitioners(999L);

        assertEquals(0, result.size());
        server.verify();
    }
}
