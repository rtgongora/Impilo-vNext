package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.server.ResponseStatusException;
import zw.gov.mohcc.impilo.experience.client.MentalHealthServiceClient;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * mental-health-service serves twenty-four operations. Until W15 the BFF reached five of them, and
 * had no test at all. These cover the nineteen that were unreachable, plus the two behaviours that
 * decide whether a clinician reads a refusal correctly: a lawful 4xx must survive the proxy, and an
 * outage must not be dressed up as one.
 */
class MentalHealthControllerTest {

    private final ObjectMapper mapper = new ObjectMapper();

    private static final String REFERRAL = UUID.randomUUID().toString();
    private static final String ASSESSMENT = UUID.randomUUID().toString();

    private ArrayNode listOf(String key, String value) {
        ArrayNode array = mapper.createArrayNode();
        array.add(mapper.createObjectNode().put(key, value));
        return array;
    }

    // ── Assessment and risk formulation ──────────────────────────────────────────────────────

    @Test
    void recordingAnAssessment_reachesTheService() {
        MentalHealthServiceClient client = mock(MentalHealthServiceClient.class);
        when(client.recordAssessment(eq(REFERRAL), any()))
                .thenReturn(mapper.createObjectNode().put("assessment_type", "INITIAL"));

        var resp = new MentalHealthController(client)
                .recordAssessment(REFERRAL, Map.of("assessmentType", "INITIAL"));

        assertEquals(201, resp.getStatusCode().value());
        assertEquals("INITIAL", ((JsonNode) resp.getBody().get("data")).get("assessment_type").asText());
        verify(client).recordAssessment(eq(REFERRAL), any());
    }

    @Test
    void riskFormulationHangsOffTheAssessment_notTheReferral() {
        MentalHealthServiceClient client = mock(MentalHealthServiceClient.class);
        when(client.recordRiskFormulation(eq(ASSESSMENT), any()))
                .thenReturn(mapper.createObjectNode().put("risk_to_self", "HIGH"));

        var resp = new MentalHealthController(client).recordRiskFormulation(
                ASSESSMENT, Map.of("riskToSelf", "HIGH", "riskToOthers", "LOW", "riskSummary", "expressed intent"));

        assertEquals(201, resp.getStatusCode().value());
        verify(client).recordRiskFormulation(eq(ASSESSMENT), any());
    }

    @Test
    void riskFormulationHistoryIsAList_becauseALaterAssessmentReachesItsOwnConclusion() {
        MentalHealthServiceClient client = mock(MentalHealthServiceClient.class);
        when(client.listRiskFormulations(ASSESSMENT)).thenReturn(listOf("risk_to_self", "MODERATE"));

        var resp = new MentalHealthController(client).listRiskFormulations(ASSESSMENT);

        assertEquals(200, resp.getStatusCode().value());
        assertTrue(((JsonNode) resp.getBody().get("data")).isArray());
    }

    /**
     * The service refuses a capacity outcome that no capacity assessment supports
     * (chk_mha_capacity_consistency). That is a statement about the record, not about the service's
     * health, and the clinician has to see it to fix the form.
     */
    @Test
    void aCapacityOutcomeWithNoCapacityAssessment_surfacesTheServicesRefusal() {
        MentalHealthServiceClient client = mock(MentalHealthServiceClient.class);
        when(client.recordAssessment(eq(REFERRAL), any())).thenThrow(HttpClientErrorException.create(
                HttpStatus.BAD_REQUEST, "Bad Request", HttpHeaders.EMPTY,
                "capacity_outcome requires capacity_assessed".getBytes(StandardCharsets.UTF_8),
                StandardCharsets.UTF_8));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> new MentalHealthController(client)
                        .recordAssessment(REFERRAL, Map.of("capacityOutcome", "LACKS_CAPACITY")));

        assertEquals(400, ex.getStatusCode().value());
        assertTrue(ex.getReason().contains("capacity_outcome requires capacity_assessed"));
    }

    // ── Safety plan and follow-up ────────────────────────────────────────────────────────────

    @Test
    void safetyPlan_isWritableAndReadable() {
        MentalHealthServiceClient client = mock(MentalHealthServiceClient.class);
        when(client.recordSafetyPlan(eq(REFERRAL), any()))
                .thenReturn(mapper.createObjectNode().put("warning_signs", "isolating"));
        when(client.listSafetyPlans(REFERRAL)).thenReturn(listOf("warning_signs", "isolating"));

        MentalHealthController controller = new MentalHealthController(client);

        assertEquals(201, controller.recordSafetyPlan(REFERRAL, Map.of("warningSigns", "isolating"))
                .getStatusCode().value());
        assertEquals(200, controller.listSafetyPlans(REFERRAL).getStatusCode().value());
    }

    @Test
    void followup_schedulesListsAndCompletes() {
        MentalHealthServiceClient client = mock(MentalHealthServiceClient.class);
        String followupId = UUID.randomUUID().toString();
        when(client.scheduleFollowup(eq(REFERRAL), any()))
                .thenReturn(mapper.createObjectNode().put("followup_type", "HOME_VISIT"));
        when(client.listFollowups(REFERRAL)).thenReturn(listOf("followup_type", "HOME_VISIT"));
        when(client.completeFollowup(eq(followupId), any()))
                .thenReturn(mapper.createObjectNode().put("completed_at", "2026-07-30T09:00:00Z"));

        MentalHealthController controller = new MentalHealthController(client);

        assertEquals(201, controller.scheduleFollowup(
                REFERRAL, Map.of("scheduledAt", "2026-08-05T09:00:00Z")).getStatusCode().value());
        assertEquals(200, controller.listFollowups(REFERRAL).getStatusCode().value());
        assertEquals(200, controller.completeFollowup(followupId, null).getStatusCode().value());
    }

    /** Completing a follow-up carries only optional notes, so an absent body must not 500. */
    @Test
    void completingAFollowupToleratesAnAbsentBody() {
        MentalHealthServiceClient client = mock(MentalHealthServiceClient.class);
        String followupId = UUID.randomUUID().toString();
        when(client.completeFollowup(eq(followupId), any())).thenReturn(mapper.createObjectNode());

        assertEquals(200, new MentalHealthController(client)
                .completeFollowup(followupId, null).getStatusCode().value());
    }

    // ── Involuntary episode ──────────────────────────────────────────────────────────────────

    @Test
    void involuntaryEpisode_opensListsAndCloses() {
        MentalHealthServiceClient client = mock(MentalHealthServiceClient.class);
        String episodeId = UUID.randomUUID().toString();
        when(client.openInvoluntaryEpisode(eq(REFERRAL), any()))
                .thenReturn(mapper.createObjectNode().put("status", "OPEN"));
        when(client.involuntaryEpisodeHistory(REFERRAL)).thenReturn(listOf("status", "OPEN"));
        when(client.closeInvoluntaryEpisode(eq(episodeId), any()))
                .thenReturn(mapper.createObjectNode().put("status", "CLOSED"));

        MentalHealthController controller = new MentalHealthController(client);

        assertEquals(201, controller.openInvoluntaryEpisode(
                REFERRAL, Map.of("legalBasis", "Mental Health Act s.8")).getStatusCode().value());
        assertTrue(((JsonNode) controller.involuntaryEpisodeHistory(REFERRAL).getBody().get("data")).isArray());
        assertEquals("CLOSED", ((JsonNode) controller.closeInvoluntaryEpisode(
                episodeId, Map.of("endedReason", "capacity regained")).getBody().get("data"))
                .get("status").asText());
    }

    /**
     * A person can be detained under one authority at a time. A second open episode is a 409 from
     * the service, and it has to read as "there is already one open", not as an outage — the
     * clinician's next action is to find the existing episode, not to retry.
     */
    @Test
    void aSecondOpenInvoluntaryEpisode_isRefusedAsAConflict_notAnOutage() {
        MentalHealthServiceClient client = mock(MentalHealthServiceClient.class);
        when(client.openInvoluntaryEpisode(eq(REFERRAL), any())).thenThrow(HttpClientErrorException.create(
                HttpStatus.CONFLICT, "Conflict", HttpHeaders.EMPTY,
                "an open involuntary episode already exists".getBytes(StandardCharsets.UTF_8),
                StandardCharsets.UTF_8));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> new MentalHealthController(client)
                        .openInvoluntaryEpisode(REFERRAL, Map.of("legalBasis", "s.8")));

        assertEquals(409, ex.getStatusCode().value());
        assertTrue(ex.getReason().contains("already exists"));
    }

    // ── Restraint ────────────────────────────────────────────────────────────────────────────

    @Test
    void restraint_recordsEndsAndReviews() {
        MentalHealthServiceClient client = mock(MentalHealthServiceClient.class);
        String eventId = UUID.randomUUID().toString();
        when(client.recordRestraintEvent(eq(REFERRAL), any()))
                .thenReturn(mapper.createObjectNode().put("restraint_type", "SECLUSION"));
        when(client.endRestraintEvent(eventId))
                .thenReturn(mapper.createObjectNode().put("ended_at", "2026-07-30T10:00:00Z"));
        when(client.reviewRestraintEvent(eq(eventId), any()))
                .thenReturn(mapper.createObjectNode().put("review_outcome", "proportionate"));

        MentalHealthController controller = new MentalHealthController(client);

        assertEquals(201, controller.recordRestraintEvent(REFERRAL, Map.of(
                "restraintType", "SECLUSION",
                "reason", "imminent harm to others",
                "deEscalationAttempted", "verbal de-escalation, offered PRN")).getStatusCode().value());
        assertEquals(200, controller.endRestraintEvent(eventId).getStatusCode().value());
        assertEquals(200, controller.reviewRestraintEvent(
                eventId, Map.of("reviewOutcome", "proportionate")).getStatusCode().value());
    }

    /**
     * The review backlog is a facility-wide safety queue. An empty array means no restraint is
     * awaiting review; a failure must not be able to render as that same emptiness, which is why
     * this route returns the array itself and never substitutes one.
     */
    @Test
    void theUnreviewedBacklogIsAList_andAnOutageIsNotAnEmptyOne() {
        MentalHealthServiceClient client = mock(MentalHealthServiceClient.class);
        when(client.unreviewedRestraintEvents()).thenReturn(mapper.createArrayNode());

        var resp = new MentalHealthController(client).unreviewedRestraintEvents();
        assertEquals(200, resp.getStatusCode().value());
        assertTrue(((JsonNode) resp.getBody().get("data")).isArray());
        assertEquals(0, ((JsonNode) resp.getBody().get("data")).size());

        MentalHealthServiceClient down = mock(MentalHealthServiceClient.class);
        when(down.unreviewedRestraintEvents()).thenThrow(new ResourceAccessException("connection refused"));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> new MentalHealthController(down).unreviewedRestraintEvents());
        assertEquals(502, ex.getStatusCode().value());
    }

    // ── Admission requests ───────────────────────────────────────────────────────────────────

    @Test
    void admissionRequest_raisesListsAndAcknowledges() {
        MentalHealthServiceClient client = mock(MentalHealthServiceClient.class);
        String requestId = UUID.randomUUID().toString();
        when(client.raiseAdmissionRequest(eq(REFERRAL), any()))
                .thenReturn(mapper.createObjectNode().put("status", "RAISED"));
        when(client.admissionRequestHistory(REFERRAL)).thenReturn(listOf("status", "RAISED"));
        when(client.acknowledgeAdmissionRequest(eq(requestId), any()))
                .thenReturn(mapper.createObjectNode().put("status", "ACKNOWLEDGED"));

        MentalHealthController controller = new MentalHealthController(client);

        assertEquals(201, controller.raiseAdmissionRequest(
                REFERRAL, Map.of("reason", "requires inpatient care")).getStatusCode().value());
        assertTrue(((JsonNode) controller.admissionRequestHistory(REFERRAL).getBody().get("data")).isArray());
        assertEquals("ACKNOWLEDGED", ((JsonNode) controller.acknowledgeAdmissionRequest(
                requestId, Map.of("pctAdmissionId", "ADM-77")).getBody().get("data")).get("status").asText());
    }

    // ── Envelope and outage ──────────────────────────────────────────────────────────────────

    /**
     * The client now unwraps the upstream ApiResponse, so what the browser receives is
     * {@code {data: […]}} — one envelope, the shape the referral queue page has always read.
     * Before W15 the controller wrapped an already-wrapped body and the page read an object where
     * it expected an array.
     */
    @Test
    void theBrowserReceivesExactlyOneEnvelope() {
        MentalHealthServiceClient client = mock(MentalHealthServiceClient.class);
        when(client.listReferrals("PENDING")).thenReturn(listOf("status", "PENDING"));

        JsonNode data = (JsonNode) new MentalHealthController(client)
                .list("PENDING").getBody().get("data");

        assertTrue(data.isArray(), "the page reads data as a list of referrals");
        assertEquals("PENDING", data.get(0).get("status").asText());
    }

    /**
     * mental-health-service has no built image and no deployment. Every one of these routes answers
     * 502 in the preview estate today, and that is the correct answer — it is not an empty record.
     */
    @Test
    void anUndeployedServiceIsAnOutage_notAnEmptyClinicalRecord() {
        MentalHealthServiceClient client = mock(MentalHealthServiceClient.class);
        when(client.listAssessments(REFERRAL)).thenThrow(new ResourceAccessException("no route to host"));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> new MentalHealthController(client).listAssessments(REFERRAL));

        assertEquals(502, ex.getStatusCode().value());
    }

    /** A null payload from upstream is also an outage, not a record that happens to be blank. */
    @Test
    void aNullPayloadIsAnOutage() {
        MentalHealthServiceClient client = mock(MentalHealthServiceClient.class);
        when(client.listSafetyPlans(REFERRAL)).thenReturn(null);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> new MentalHealthController(client).listSafetyPlans(REFERRAL));

        assertEquals(502, ex.getStatusCode().value());
    }
}
