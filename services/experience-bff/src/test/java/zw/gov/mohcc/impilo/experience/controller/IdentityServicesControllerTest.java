package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.experience.client.VarapiServiceClient;
import zw.gov.mohcc.impilo.experience.client.VitoServiceClient;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class IdentityServicesControllerTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void createProviderDelegatesToVarapiCreateProvider() throws Exception {
        RecordingVarapiClient varapiClient =
                new RecordingVarapiClient(OBJECT_MAPPER.readTree("{\"providerPublicId\":\"VAR-2026-001\"}"));
        IdentityServicesController controller = new IdentityServicesController(new StubVitoClient(), varapiClient);

        ResponseEntity<Map<String, Object>> response = controller.createProviderIdentity(
                "req-1",
                "corr-1",
                Map.of("givenName", "Tariro", "familyName", "Moyo", "profession", "GENERAL_PRACTITIONER")
        );

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals("Tariro", varapiClient.lastRequest.get("givenName"));
        JsonNode data = (JsonNode) response.getBody().get("data");
        assertEquals("VAR-2026-001", data.get("providerPublicId").asText());
    }

    private static ServiceClientConfig.ServiceEndpoints endpoints() {
        return new ServiceClientConfig.ServiceEndpoints(
                null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null
        );
    }

    private static final class StubVitoClient extends VitoServiceClient {
        private StubVitoClient() {
            super(new RestTemplate(), endpoints());
        }
    }

    private static final class RecordingVarapiClient extends VarapiServiceClient {
        private final JsonNode response;
        private Map<String, Object> lastRequest;

        private RecordingVarapiClient(JsonNode response) {
            super(new RestTemplate(), endpoints());
            this.response = response;
        }

        @Override
        public JsonNode createProvider(Map<String, Object> request) {
            this.lastRequest = request;
            return response;
        }
    }
}
