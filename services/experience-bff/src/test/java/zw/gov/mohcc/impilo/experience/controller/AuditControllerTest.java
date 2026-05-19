package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.experience.client.TshepoAuditServiceClient;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class AuditControllerTest {

    private static final ObjectMapper mapper = new ObjectMapper();
    private static String lastSubjectRef;
    private static String lastEventType;

    @Test
    void listAuditEntries_returnsDataAndMeta() {
        AuditController controller = new AuditController(new StubAuditClient());
        ResponseEntity<Map<String, Object>> response =
                controller.listAuditEntries("t1", "req-1", "corr-1", 0, 20, null, null);
        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("req-1", ((Map<?, ?>) response.getBody().get("meta")).get("request_id"));
        assertEquals(0, ((Map<?, ?>) response.getBody().get("meta")).get("page"));
    }

    @Test
    void listAuditEntries_forwardsAggregateFilters() {
        lastSubjectRef = null;
        lastEventType = null;
        AuditController controller = new AuditController(new StubAuditClient());
        ResponseEntity<Map<String, Object>> response =
                controller.listAuditEntries("t1", "req-3", "corr-3", 1, 10, "PAYMENT_INTENT", "PI-123");
        assertEquals(200, response.getStatusCode().value());
        assertEquals("PI-123", lastSubjectRef);
        assertEquals("PAYMENT_INTENT", lastEventType);
        assertEquals("PAYMENT_INTENT", ((Map<?, ?>) response.getBody().get("meta")).get("aggregate_type"));
        assertEquals("PI-123", ((Map<?, ?>) response.getBody().get("meta")).get("aggregate_id"));
    }

    @Test
    void getAuditEntry_returnsDataAndMeta() {
        AuditController controller = new AuditController(new StubAuditClient());
        UUID id = UUID.randomUUID();
        ResponseEntity<Map<String, Object>> response =
                controller.getAuditEntry(id, "t1", "req-2", "corr-2");
        assertEquals(200, response.getStatusCode().value());
        assertEquals("req-2", ((Map<?, ?>) response.getBody().get("meta")).get("request_id"));
    }

    private static ServiceClientConfig.ServiceEndpoints endpoints() {
        return ServiceClientConfig.testServiceEndpoints();
    }

    private static final class StubAuditClient extends TshepoAuditServiceClient {
        StubAuditClient() { super(new RestTemplate(), endpoints()); }

        @Override public JsonNode queryEvents(String actorId, String subjectRef, String eventType,
                                              String fromTime, String toTime, int page, int size) {
            lastSubjectRef = subjectRef;
            lastEventType = eventType;
            ArrayNode arr = mapper.createArrayNode();
            ObjectNode event = mapper.createObjectNode();
            event.put("id", UUID.randomUUID().toString());
            event.put("eventType", "ACCESS");
            arr.add(event);
            return arr;
        }

        @Override public JsonNode getEvent(UUID id) {
            ObjectNode node = mapper.createObjectNode();
            node.put("id", id.toString());
            node.put("eventType", "ACCESS");
            return node;
        }
    }
}
