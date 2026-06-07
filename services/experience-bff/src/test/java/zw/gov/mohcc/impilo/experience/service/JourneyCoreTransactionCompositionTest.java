package zw.gov.mohcc.impilo.experience.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.experience.client.PctServiceClient;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class JourneyCoreTransactionCompositionTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void getCoreTransaction_resolvesJourneyBackedId() {
        CoreTransactionCompositionService service = new CoreTransactionCompositionService(
                null,
                new StubPctJourneyClient(),
                null,
                null,
                null,
                MAPPER);

        ObjectNode view = service.getCoreTransaction("journey-42");
        assertNotNull(view);
        assertEquals("journey-42", view.path("transaction").path("id").asText());
        assertEquals("QUEUED", view.path("transaction").path("currentState").asText());
    }

    private static final class StubPctJourneyClient extends PctServiceClient {
        StubPctJourneyClient() {
            super(new RestTemplate(), ServiceClientConfig.testServiceEndpoints(), MAPPER);
        }

        @Override
        public JsonNode getJourney(String journeyId) {
            ObjectNode node = MAPPER.createObjectNode();
            node.put("journeyId", journeyId);
            node.put("state", "WAITING");
            node.put("patientCpid", "CPID-ZW-00001");
            node.put("facilityId", "f1000000-0000-0000-0000-000000000001");
            return node;
        }
    }
}
