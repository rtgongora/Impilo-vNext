package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import zw.gov.mohcc.impilo.experience.service.CoreTransactionCompositionService;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class CoreTransactionControllerTest {

    @Test
    void listCoreTransactions_returnsComposedEnvelope() {
        CoreTransactionController controller = new CoreTransactionController(new StubService());
        ResponseEntity<Map<String, Object>> response =
                controller.listCoreTransactions("QUEUED", "FACILITY_WALK_IN", null, "req-1", "corr-1");
        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("req-1", ((Map<?, ?>) response.getBody().get("meta")).get("request_id"));
    }

    @Test
    void listCoreTransactions_acceptsEncounterIdFilter() {
        CoreTransactionController controller = new CoreTransactionController(new StubService());
        ResponseEntity<Map<String, Object>> matched =
                controller.listCoreTransactions(null, null, "enc-1", "req-1", "corr-1");
        ResponseEntity<Map<String, Object>> unmatched =
                controller.listCoreTransactions(null, null, "enc-other", "req-1", "corr-1");
        assertEquals(200, matched.getStatusCode().value());
        assertEquals(200, unmatched.getStatusCode().value());
        assertNotNull(matched.getBody());
        assertNotNull(unmatched.getBody());
    }

    @Test
    void getCoreTransaction_returnsNotFoundWhenMissing() {
        CoreTransactionController controller = new CoreTransactionController(new MissingStubService());
        ResponseEntity<Map<String, Object>> response =
                controller.getCoreTransaction("tx-missing", "req-2", "corr-2");
        assertEquals(404, response.getStatusCode().value());
        assertEquals("CORE_TRANSACTION_NOT_FOUND", ((Map<?, ?>) response.getBody().get("error")).get("code"));
    }

    @Test
    void createCoreTransaction_returns201() {
        CoreTransactionController controller = new CoreTransactionController(new StubService());
        ResponseEntity<Map<String, Object>> response =
                controller.createCoreTransaction("req-3", "corr-3", Map.of("definitionId", "core.transaction"));
        assertEquals(201, response.getStatusCode().value());
    }

    @Test
    void action_returns202() {
        CoreTransactionController controller = new CoreTransactionController(new StubService());
        ResponseEntity<Map<String, Object>> response =
                controller.applyAction("tx-1", "START_TRIAGE", "req-4", "corr-4", Map.of("note", "triage start"));
        assertEquals(202, response.getStatusCode().value());
    }

    @Test
    void timelineAndNextActions_return200() {
        CoreTransactionController controller = new CoreTransactionController(new StubService());
        ResponseEntity<Map<String, Object>> timeline =
                controller.getTimeline("tx-1", "req-5", "corr-5");
        ResponseEntity<Map<String, Object>> nextActions =
                controller.getNextActions("tx-1", "req-6", "corr-6");
        assertEquals(200, timeline.getStatusCode().value());
        assertEquals(200, nextActions.getStatusCode().value());
    }

    @Test
    void journeysAndNompiloAndFeedbackAndHandoff_returnExpectedStatus() {
        CoreTransactionController controller = new CoreTransactionController(new StubService());
        ResponseEntity<Map<String, Object>> journeys =
                controller.getJourneys("tx-1", "req-7", "corr-7");
        ResponseEntity<Map<String, Object>> nompilo =
                controller.getNompilo("tx-1", "req-8", "corr-8");
        ResponseEntity<Map<String, Object>> feedback =
                controller.submitFeedback("tx-1", "req-9", "corr-9", Map.of("channel", "IN_APP"));
        ResponseEntity<Map<String, Object>> handoff =
                controller.requestNompiloHandoff("tx-1", "req-10", "corr-10", Map.of("handoffOptionId", "helpdesk"));
        ResponseEntity<Map<String, Object>> command =
                controller.executeNompiloCommand("tx-1", "req-11", "corr-11", Map.of("query", "find anc service"));
        assertEquals(200, journeys.getStatusCode().value());
        assertEquals(200, nompilo.getStatusCode().value());
        assertEquals(202, feedback.getStatusCode().value());
        assertEquals(202, handoff.getStatusCode().value());
        assertEquals(202, command.getStatusCode().value());
    }

    private static class StubService extends CoreTransactionCompositionService {
        private static final ObjectMapper MAPPER = new ObjectMapper();

        StubService() {
            super(null, null, null, null, null, MAPPER);
        }

        @Override
        public ObjectNode listCoreTransactions(String state, String type, String encounterId) {
            ObjectNode root = MAPPER.createObjectNode();
            ArrayNode items = MAPPER.createArrayNode();
            ObjectNode view = MAPPER.createObjectNode();
            ObjectNode tx = MAPPER.createObjectNode();
            tx.put("id", "tx-1");
            tx.put("type", type != null ? type : "FACILITY_WALK_IN");
            tx.put("currentState", state != null ? state : "INITIATED");
            view.set("transaction", tx);
            ObjectNode clinical = MAPPER.createObjectNode();
            clinical.put("encounterId", "enc-1");
            view.set("clinicalContext", clinical);
            items.add(view);
            if (encounterId != null && !encounterId.isBlank() && !"enc-1".equals(encounterId)) {
                items = MAPPER.createArrayNode();
            }
            root.set("items", items);
            root.set("failureModes", MAPPER.createArrayNode());
            return root;
        }

        @Override
        public ObjectNode getCoreTransaction(String transactionId) {
            ObjectNode root = MAPPER.createObjectNode();
            root.set("transaction", MAPPER.createObjectNode()
                    .put("id", transactionId)
                    .put("currentState", "INITIATED")
                    .put("type", "FACILITY_WALK_IN")
                    .put("lifecycleStage", "ENTRY")
                    .put("serviceCode", "svc.core"));
            root.set("timeline", MAPPER.createArrayNode());
            root.set("nextActions", MAPPER.createArrayNode());
            return root;
        }

        @Override
        public JsonNode createCoreTransaction(Map<String, Object> payload) {
            return MAPPER.createObjectNode().put("id", "tx-created");
        }

        @Override
        public JsonNode applyAction(String transactionId, String actionCode, Map<String, Object> payload) {
            return MAPPER.createObjectNode().put("id", transactionId).put("action", actionCode);
        }

        @Override
        public ArrayNode getTimeline(String transactionId) {
            ArrayNode arr = MAPPER.createArrayNode();
            arr.add(MAPPER.createObjectNode().put("eventName", "core.transaction.initiated"));
            return arr;
        }

        @Override
        public ArrayNode getNextActions(String transactionId) {
            ArrayNode arr = MAPPER.createArrayNode();
            arr.add(MAPPER.createObjectNode().put("code", "VIEW_TIMELINE"));
            return arr;
        }

        @Override
        public ObjectNode getJourneys(String transactionId) {
            ObjectNode journeys = MAPPER.createObjectNode();
            journeys.set("person", MAPPER.createObjectNode().put("currentStage", "RECEIVE_CARE"));
            return journeys;
        }

        @Override
        public ObjectNode getNompilo(String transactionId) {
            return MAPPER.createObjectNode().put("available", true).put("mode", "CONTEXTUAL_COMPANION");
        }

        @Override
        public ObjectNode submitFeedback(String transactionId, Map<String, Object> payload) {
            return MAPPER.createObjectNode().put("transactionId", transactionId).put("status", "ACCEPTED");
        }

        @Override
        public ObjectNode requestNompiloHandoff(String transactionId, Map<String, Object> payload) {
            return MAPPER.createObjectNode().put("transactionId", transactionId).put("status", "ACCEPTED");
        }

        @Override
        public ObjectNode executeNompiloCommand(String transactionId, Map<String, Object> payload) {
            return MAPPER.createObjectNode()
                    .put("transactionId", transactionId)
                    .put("status", "ACCEPTED")
                    .put("intent", "FIND_SERVICE");
        }
    }

    private static final class MissingStubService extends StubService {
        @Override
        public ObjectNode getCoreTransaction(String transactionId) {
            return null;
        }
    }
}
