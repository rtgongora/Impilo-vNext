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
    private static final UUID ALERT_ID = UUID.fromString("00000000-0000-4000-8000-000000000004");
    private static final UUID ITEM_ID = UUID.fromString("00000000-0000-4000-8000-000000000005");
    private static final UUID STAY_ID = UUID.fromString("00000000-0000-4000-8000-000000000006");

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

    // ── Reachability closure: every route below existed on pct and reached nothing ────────────

    @Test
    void closeAlert_isReachable_soASuppressedHazardCanReRaise() {
        var controller = new EmergencyEpisodeController(new StubPctClient());
        ResponseEntity<Map<String, Object>> response = controller.closeAlert(ALERT_ID, Map.of("closedBy", "nurse-A"));
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        JsonNode data = (JsonNode) response.getBody().get("data");
        assertThat(data.get("status").asText()).isEqualTo("CLOSED");
    }

    @Test
    void invokeOrderSet_returns201WithItems() {
        var controller = new EmergencyEpisodeController(new StubPctClient());
        ResponseEntity<Map<String, Object>> response =
                controller.invokeOrderSet(EPISODE_ID, Map.of("orderSetCode", "EM_SEPSIS_BUNDLE"));
        assertThat(response.getStatusCode().value()).isEqualTo(201);
        JsonNode data = (JsonNode) response.getBody().get("data");
        assertThat(data.get("items").isArray()).isTrue();
    }

    @Test
    void orderSetsForEpisode_returnsList() {
        var controller = new EmergencyEpisodeController(new StubPctClient());
        JsonNode data = (JsonNode) controller.orderSets(EPISODE_ID).getBody().get("data");
        assertThat(data.isArray()).isTrue();
    }

    @Test
    void declineOrderSetItem_withoutReason_surfacesPctsOwn422_notA502() {
        var controller = new EmergencyEpisodeController(new PctClientRejectingReasonlessDecline());
        assertThatThrownBy(() -> controller.declineItem(ITEM_ID, Map.of()))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    var rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode().value()).isEqualTo(422);
                    assertThat(rse.getReason()).contains("decline_reason");
                });
    }

    @Test
    void recordMedication_returns201() {
        var controller = new EmergencyEpisodeController(new StubPctClient());
        ResponseEntity<Map<String, Object>> response = controller.recordMedication(EPISODE_ID,
                Map.of("medicationCode", "ADRENALINE", "doseValue", 1, "doseUnit", "mg"));
        assertThat(response.getStatusCode().value()).isEqualTo(201);
    }

    @Test
    void observationStay_startListAndEnd_areAllReachable() {
        var controller = new EmergencyEpisodeController(new StubPctClient());
        assertThat(controller.startObservationStay(EPISODE_ID, Map.of("reason", "head injury watch"))
                .getStatusCode().value()).isEqualTo(201);
        assertThat(((JsonNode) controller.observationStays(EPISODE_ID).getBody().get("data")).isArray()).isTrue();
        assertThat(controller.endObservationStay(STAY_ID, Map.of("outcome", "DISCHARGED"))
                .getStatusCode().value()).isEqualTo(200);
    }

    @Test
    void commandBoard_stillRendersWhenOneSourceIsBlind_andNamesWhichOne() {
        // A 502 here would blank a board a charge nurse is standing in front of, on the strength of
        // one dead upstream. The board renders; the tile that cannot be read says so.
        var controller = new EmergencyEpisodeController(new PctClientWithDeadAlerts());
        ResponseEntity<Map<String, Object>> response = controller.commandBoard(FACILITY_ID);

        assertThat(response.getStatusCode().value()).isEqualTo(200);

        @SuppressWarnings("unchecked")
        var items = (Map<String, Object>) response.getBody().get("items");
        assertThat(items).containsKeys("summary", "episodes");
        assertThat(items).doesNotContainKey("alerts");

        @SuppressWarnings("unchecked")
        var failures = (java.util.List<Map<String, Object>>) response.getBody().get("failures");
        assertThat(failures).hasSize(1);
        assertThat(failures.get(0).get("source")).isEqualTo("alerts");
        assertThat((String) failures.get(0).get("message"))
                .contains("Do not treat this as an absence of emergency alerts");
    }

    @Test
    void commandBoard_isNotMarkedDegradedWhenEverySourceAnswered() {
        var controller = new EmergencyEpisodeController(new StubPctClient());
        ResponseEntity<Map<String, Object>> response = controller.commandBoard(FACILITY_ID);

        @SuppressWarnings("unchecked")
        var meta = (Map<String, Object>) response.getBody().get("meta");
        assertThat(meta.get("degraded")).isEqualTo(false);
    }

    @Test
    void identityLink_isAppendOnly_historyIsAList() {
        var controller = new EmergencyEpisodeController(new StubPctClient());
        assertThat(controller.linkIdentity(EPISODE_ID,
                Map.of("resolvedSubjectCpid", "CPID-1", "resolutionMethod", "CLINICAL_RECOGNITION"))
                .getStatusCode().value()).isEqualTo(201);
        JsonNode history = (JsonNode) controller.identityLinks(EPISODE_ID).getBody().get("data");
        assertThat(history.isArray()).isTrue();
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
        @Override public JsonNode closeEmergencyAlert(UUID alertId, Map<String, Object> body) {
            return mapper.createObjectNode().put("alert_id", alertId.toString()).put("status", "CLOSED");
        }
        @Override public JsonNode invokeEmergencyOrderSet(UUID episodeId, Map<String, Object> body) {
            var node = mapper.createObjectNode().put("instance_id", UUID.randomUUID().toString())
                    .put("episode_id", episodeId.toString()).put("status", "ACTIVE");
            node.putArray("items").add(mapper.createObjectNode().put("item_id", ITEM_ID.toString()));
            return node;
        }
        @Override public JsonNode emergencyOrderSets(UUID episodeId) {
            var arr = mapper.createArrayNode();
            arr.add(mapper.createObjectNode().put("episode_id", episodeId.toString()));
            return arr;
        }
        @Override public JsonNode recordEmergencyMedication(UUID episodeId, Map<String, Object> body) {
            return mapper.createObjectNode().put("episode_id", episodeId.toString()).put("status", "ADMINISTERED");
        }
        @Override public JsonNode startEmergencyObservationStay(UUID episodeId, Map<String, Object> body) {
            return mapper.createObjectNode().put("stay_id", STAY_ID.toString()).put("status", "OPEN");
        }
        @Override public JsonNode emergencyObservationStays(UUID episodeId) {
            return mapper.createArrayNode().add(mapper.createObjectNode().put("stay_id", STAY_ID.toString()));
        }
        @Override public JsonNode endEmergencyObservationStay(UUID stayId, Map<String, Object> body) {
            return mapper.createObjectNode().put("stay_id", stayId.toString()).put("status", "ENDED");
        }
        @Override public JsonNode linkEmergencyIdentity(UUID episodeId, Map<String, Object> body) {
            return mapper.createObjectNode().put("episode_id", episodeId.toString())
                    .put("resolution_method", "CLINICAL_RECOGNITION");
        }
        @Override public JsonNode emergencyIdentityLinks(UUID episodeId) {
            return mapper.createArrayNode().add(mapper.createObjectNode().put("episode_id", episodeId.toString()));
        }
        @Override public JsonNode emergencyCommandSummary(UUID facilityId) {
            return mapper.createObjectNode().set("episodes_by_state",
                    mapper.createObjectNode().put("OPEN_UNTRIAGED", 2));
        }
        @Override public JsonNode emergencyAlertBoard(UUID facilityId) {
            return mapper.createArrayNode().add(mapper.createObjectNode().put("alert_id", ALERT_ID.toString()));
        }
    }

    /** Summary and episodes answer; the alert source does not. */
    private static final class PctClientWithDeadAlerts extends PctServiceClient {
        PctClientWithDeadAlerts() { super(new RestTemplate(), endpoints(), mapper); }
        @Override public JsonNode emergencyCommandSummary(UUID facilityId) {
            return mapper.createObjectNode().set("episodes_by_state",
                    mapper.createObjectNode().put("OPEN_UNTRIAGED", 2));
        }
        @Override public JsonNode emergencyEpisodeBoard(UUID facilityId) {
            return mapper.createArrayNode();
        }
        @Override public JsonNode emergencyAlertBoard(UUID facilityId) {
            throw new org.springframework.web.client.ResourceAccessException("connection refused");
        }
    }

    /** pct's own gate: a decline with no reason is a validation failure, not an outage. */
    private static final class PctClientRejectingReasonlessDecline extends PctServiceClient {
        PctClientRejectingReasonlessDecline() { super(new RestTemplate(), endpoints(), mapper); }
        @Override public JsonNode declineEmergencyOrderSetItem(UUID itemId, Map<String, Object> body) {
            throw HttpClientErrorException.create(org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY,
                    "Unprocessable Entity", null, "decline_reason is required".getBytes(), null);
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
