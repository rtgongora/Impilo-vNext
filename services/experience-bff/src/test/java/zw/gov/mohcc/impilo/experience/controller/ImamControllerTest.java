package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.experience.client.PctServiceClient;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The property under test is the one that hid three broken verticals in this programme: a proxy
 * that swallows an upstream failure into an empty 200 is indistinguishable, at every layer above
 * it, from a genuine absence of data.
 */
class ImamControllerTest {

    private static final ObjectMapper mapper = new ObjectMapper();

    @Test
    void listEpisodesReturnsWhatPctHolds() {
        ImamController controller = new ImamController(new StubPctClient());

        ResponseEntity<Map<String, Object>> response =
                controller.listEpisodes("req-1", "corr-1", "patient-1");

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("req-1", ((Map<?, ?>) response.getBody().get("meta")).get("request_id"));
    }

    @Test
    void aFailedReadIs502RatherThanAnEmptyListThatReadsAsNoTreatment() {
        ImamController controller = new ImamController(new FailingPctClient());

        ResponseEntity<Map<String, Object>> response =
                controller.listEpisodes("req-2", "corr-2", "patient-1");

        assertEquals(502, response.getStatusCode().value());
        assertEquals("imam_episodes_unavailable", response.getBody().get("error"));
        assertTrue(String.valueOf(response.getBody().get("message")).contains("Do not read this"));
    }

    @Test
    void aFailedTracingQueueIs502BecauseAnEmptyOneMeansNobodyIsOverdue() {
        // The most dangerous empty list in this feature: a clinic reading it goes home believing
        // no child needs a home visit.
        ImamController controller = new ImamController(new FailingPctClient());

        ResponseEntity<Map<String, Object>> response =
                controller.tracingQueue("req-3", "corr-3", null);

        assertEquals(502, response.getStatusCode().value());
        assertTrue(String.valueOf(response.getBody().get("message")).contains("has not been established"));
    }

    @Test
    void aRefusedCureIsPassedThroughAs422RatherThanFlattened() {
        // The clinician needs to see that the discharge criteria were not met and that an override
        // reason would let them proceed. A generic 500 tells them none of that.
        ImamController controller = new ImamController(new RefusingPctClient());

        ResponseEntity<Map<String, Object>> response =
                controller.closeOutcome("req-4", "corr-4", "ep-1", Map.of("outcome", "CURED"));

        assertEquals(422, response.getStatusCode().value());
        assertEquals(422, response.getBody().get("status"));
        assertTrue(String.valueOf(response.getBody().get("message")).contains("MUAC_SUSTAINED"));
    }

    @Test
    void enrolReturns201WithWhatPctCreated() {
        ImamController controller = new ImamController(new StubPctClient());

        ResponseEntity<Map<String, Object>> response = controller.enrol("req-5", "corr-5",
                Map.of("patient_id", "patient-1",
                        "source_classification", "UNCOMPLICATED_SEVERE_ACUTE_MALNUTRITION"));

        assertEquals(201, response.getStatusCode().value());
        assertNotNull(response.getBody().get("data"));
    }

    private static ServiceClientConfig.ServiceEndpoints endpoints() {
        return ServiceClientConfig.testServiceEndpoints();
    }

    private static class StubPctClient extends PctServiceClient {
        StubPctClient() {
            super(new RestTemplate(), endpoints(), mapper);
        }

        @Override public JsonNode listImamEpisodes(String patientCpid) {
            ArrayNode arr = mapper.createArrayNode();
            arr.add(mapper.createObjectNode().put("imam_episode_id", "ep-1").put("programme", "OTP"));
            return arr;
        }

        @Override public JsonNode enrolImamEpisode(Map<String, Object> body) {
            ObjectNode node = mapper.createObjectNode();
            node.put("imam_episode_id", "ep-new");
            node.put("programme", "OTP");
            return node;
        }

        @Override public JsonNode imamTracingQueue(String facilityId) {
            return mapper.createArrayNode();
        }
    }

    private static final class FailingPctClient extends StubPctClient {
        @Override public JsonNode listImamEpisodes(String patientCpid) {
            throw new IllegalStateException("connection refused");
        }

        @Override public JsonNode imamTracingQueue(String facilityId) {
            throw new IllegalStateException("connection refused");
        }
    }

    private static final class RefusingPctClient extends StubPctClient {
        @Override public JsonNode closeImamEpisode(String episodeId, Map<String, Object> body) {
            throw HttpClientErrorException.create(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Unprocessable Entity", null,
                    ("The discharge criteria are not met ([MUAC_SUSTAINED]), so this episode cannot be "
                            + "recorded as CURED without a stated reason.").getBytes(), null);
        }
    }
}
