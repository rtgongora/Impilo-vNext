package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;
import zw.gov.mohcc.impilo.experience.client.PctServiceClient;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * BFF proxy for pct's emergency-episode spine + acceptance handshake — the gap
 * {@code PctServiceClient}'s own comment named ("the emergency pack is about to serve the emergency
 * EPISODE at /v1/emergency on pct"). Proves the proxy forwards correctly, unwraps pct's {@code data}
 * envelope, and — the important behavioural rule shared with {@code EdWorkflowController} — a 4xx
 * from pct (an invalid transition, a resolved handover) surfaces as-is while a genuine upstream
 * outage collapses to 502.
 */
class EmergencyEpisodeControllerTest {

    private static final ObjectMapper mapper = new ObjectMapper();
    private static final UUID EPISODE_ID = UUID.fromString("00000000-0000-4000-8000-000000000001");
    private static final UUID FACILITY_ID = UUID.fromString("00000000-0000-4000-8000-000000000002");
    private static final UUID HANDOVER_ID = UUID.fromString("00000000-0000-4000-8000-000000000003");

    private static ServiceClientConfig.ServiceEndpoints endpoints() {
        return ServiceClientConfig.testServiceEndpoints();
    }

    @Test
    void open_returns201WithData() {
        var controller = new EmergencyEpisodeController(new StubPctClient());
        ResponseEntity<Map<String, Object>> response = controller.open(Map.of("entryRoute", "WALK_IN"));
        assertThat(response.getStatusCode().value()).isEqualTo(201);
        JsonNode data = (JsonNode) response.getBody().get("data");
        assertThat(data.get("episode_id").asText()).isEqualTo(EPISODE_ID.toString());
    }

    @Test
    void get_returns200WithData() {
        var controller = new EmergencyEpisodeController(new StubPctClient());
        ResponseEntity<Map<String, Object>> response = controller.get(EPISODE_ID);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
    }

    @Test
    void board_forwardsFacilityIdAndReturnsList() {
        var controller = new EmergencyEpisodeController(new StubPctClient());
        ResponseEntity<Map<String, Object>> response = controller.board(FACILITY_ID);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        JsonNode data = (JsonNode) response.getBody().get("data");
        assertThat(data.isArray()).isTrue();
    }

    @Test
    void requestHandover_returns201() {
        var controller = new EmergencyEpisodeController(new StubPctClient());
        ResponseEntity<Map<String, Object>> response = controller.requestHandover(EPISODE_ID,
                Map.of("targetType", "ADMISSION", "requestedBy", "nurse-A"));
        assertThat(response.getStatusCode().value()).isEqualTo(201);
    }

    @Test
    void acceptHandover_returns200AndClosedState() {
        var controller = new EmergencyEpisodeController(new StubPctClient());
        ResponseEntity<Map<String, Object>> response = controller.acceptHandover(HANDOVER_ID,
                Map.of("acceptedBy", "clerk", "acceptingRef", "ADM-1"));
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        JsonNode data = (JsonNode) response.getBody().get("data");
        assertThat(data.get("state").asText()).isEqualTo("CLOSED_HANDED_OVER");
    }

    @Test
    void aFourHundredFromPct_surfacesAsIs_notCollapsedTo502() {
        var controller = new EmergencyEpisodeController(new PctClientThrowing409());
        assertThatThrownBy(() -> controller.acceptHandover(HANDOVER_ID, Map.of("acceptedBy", "x")))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode().value()).isEqualTo(409));
    }

    @Test
    void aServerErrorFromPct_collapsesTo502() {
        var controller = new EmergencyEpisodeController(new PctClientThrowing500());
        assertThatThrownBy(() -> controller.get(EPISODE_ID))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode().value()).isEqualTo(502));
    }

    private static final class StubPctClient extends PctServiceClient {
        StubPctClient() { super(new RestTemplate(), endpoints(), mapper); }

        @Override public JsonNode openEmergencyEpisode(Map<String, Object> body) {
            return mapper.createObjectNode().put("episode_id", EPISODE_ID.toString()).put("state", "OPEN_UNTRIAGED");
        }
        @Override public JsonNode getEmergencyEpisode(UUID episodeId) {
            return mapper.createObjectNode().put("episode_id", episodeId.toString());
        }
        @Override public JsonNode emergencyEpisodeBoard(UUID facilityId) {
            var arr = mapper.createArrayNode();
            arr.add(mapper.createObjectNode().put("episode_id", EPISODE_ID.toString()));
            return arr;
        }
        @Override public JsonNode requestEmergencyHandover(UUID episodeId, Map<String, Object> body) {
            return mapper.createObjectNode().put("handover_id", HANDOVER_ID.toString()).put("status", "PENDING");
        }
        @Override public JsonNode acceptEmergencyHandover(UUID handoverId, Map<String, Object> body) {
            return mapper.createObjectNode().put("episode_id", EPISODE_ID.toString())
                    .put("state", "CLOSED_HANDED_OVER").put("handover_id", handoverId.toString());
        }
    }

    private static final class PctClientThrowing409 extends PctServiceClient {
        PctClientThrowing409() { super(new RestTemplate(), endpoints(), mapper); }
        @Override public JsonNode acceptEmergencyHandover(UUID handoverId, Map<String, Object> body) {
            throw HttpClientErrorException.create(org.springframework.http.HttpStatus.CONFLICT,
                    "Conflict", null, "Handover already accepted".getBytes(), null);
        }
    }

    private static final class PctClientThrowing500 extends PctServiceClient {
        PctClientThrowing500() { super(new RestTemplate(), endpoints(), mapper); }
        @Override public JsonNode getEmergencyEpisode(UUID episodeId) {
            throw new org.springframework.web.client.ResourceAccessException("connection refused");
        }
    }
}
