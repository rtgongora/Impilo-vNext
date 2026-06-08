package zw.gov.mohcc.impilo.experience.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.experience.client.CostaServiceClient;
import zw.gov.mohcc.impilo.experience.client.DispatchServiceClient;
import zw.gov.mohcc.impilo.experience.client.PctServiceClient;
import zw.gov.mohcc.impilo.experience.client.WorkflowServiceClient;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class EncounterCoreTransactionCompositionTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void listCoreTransactions_composesFromPctWhenWorkflowMissing() {
        CoreTransactionCompositionService service = new CoreTransactionCompositionService(
                new EmptyWorkflowClient(),
                new StubPctClient(),
                null,
                null,
                null,
                MAPPER);

        ObjectNode envelope = service.listCoreTransactions(null, "FACILITY_WALK_IN", "42");
        assertEquals(1, envelope.get("items").size());
        assertEquals("encounter-42", envelope.get("items").get(0).path("transaction").path("id").asText());
        assertEquals("42", envelope.get("items").get(0).path("clinicalContext").path("encounterId").asText());
    }

    @Test
    void getCoreTransaction_resolvesEncounterBackedId() {
        CoreTransactionCompositionService service = new CoreTransactionCompositionService(
                new EmptyWorkflowClient(),
                new StubPctClient(),
                null,
                null,
                null,
                MAPPER);

        ObjectNode view = service.getCoreTransaction("encounter-42");
        assertNotNull(view);
        assertEquals("IN_SERVICE", view.path("transaction").path("currentState").asText());
    }

    private static final class EmptyWorkflowClient extends WorkflowServiceClient {
        EmptyWorkflowClient() {
            super(new RestTemplate(), ServiceClientConfig.testServiceEndpoints());
        }

        @Override
        public JsonNode listWorkflows(String status) {
            return MAPPER.createArrayNode();
        }

        @Override
        public JsonNode listInstances(String status) {
            return MAPPER.createArrayNode();
        }
    }

    private static final class StubPctClient extends PctServiceClient {
        StubPctClient() {
            super(new RestTemplate(), ServiceClientConfig.testServiceEndpoints(), MAPPER);
        }

        @Override
        public JsonNode getEncounter(long encounterId) {
            ObjectNode node = MAPPER.createObjectNode();
            node.put("id", encounterId);
            node.put("status", "IN_PROGRESS");
            node.put("journeyId", "journey-42");
            node.put("subjectCpid", "CPID-ZW-00001");
            node.put("facilityId", "f1000000-0000-0000-0000-000000000001");
            return node;
        }
    }
}
