package zw.gov.mohcc.impilo.pct.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import zw.gov.mohcc.impilo.pct.core.EmergencyAlertService;
import zw.gov.mohcc.impilo.pct.core.EmergencyAlertServiceTest;
import zw.gov.mohcc.impilo.pct.core.EmergencyDispositionService;
import zw.gov.mohcc.impilo.pct.core.EmergencyDispositionServiceTest;
import zw.gov.mohcc.impilo.pct.core.EmergencyEpisodeService;
import zw.gov.mohcc.impilo.pct.core.EmergencyEpisodeServiceTest;
import zw.gov.mohcc.impilo.pct.core.EmergencyObservationStayService;
import zw.gov.mohcc.impilo.pct.core.EmergencyObservationStayServiceTest;
import zw.gov.mohcc.impilo.shared.auth.AccessMode;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The controller that finally makes {@link EmergencyEpisodeService} reachable over HTTP. Three
 * fully-tested waves (W2 open/FSM, W5 alert clocks, W9 the acceptance handshake) had zero callers —
 * this proves the wiring end to end through the actual HTTP-shaped request/response, not just the
 * service layer underneath it (already covered by 27 cases in EmergencyEpisodeServiceTest).
 *
 * <p>Reuses {@link EmergencyEpisodeServiceTest}'s package-visible in-memory fakes rather than
 * duplicating them — same tenant/facility constants, same fake repositories, so this test is
 * genuinely exercising the same service the unit tests already trust.
 */
class EmergencyEpisodeControllerTest {

    private static final UUID TENANT = UUID.fromString("00000000-0000-4000-8000-000000000001");
    private static final UUID FACILITY = UUID.fromString("00000000-0000-4000-8000-000000000002");

    private EmergencyEpisodeController controller;

    @BeforeEach
    void setUp() {
        var repo = new EmergencyEpisodeServiceTest.InMemoryRepo();
        var handoverRepo = new EmergencyEpisodeServiceTest.InMemoryHandoverRepo();
        var outbox = new EmergencyEpisodeServiceTest.CountingOutbox();
        EmergencyEpisodeService service = new EmergencyEpisodeService(repo, handoverRepo, outbox, new ObjectMapper());
        EmergencyDispositionService dispositionService = new EmergencyDispositionService(
                repo, new EmergencyDispositionServiceTest.InMemoryDispositionRepo(), service);
        EmergencyObservationStayService observationStayService = new EmergencyObservationStayService(
                repo, new EmergencyObservationStayServiceTest.InMemoryStayRepo());
        EmergencyAlertService alertService = new EmergencyAlertService(new EmergencyAlertServiceTest.InMemoryAlertRepo());
        var mciBulkMintService = new zw.gov.mohcc.impilo.pct.core.MciBulkMintService(
                service, new zw.gov.mohcc.impilo.pct.integration.DaidzaiEpisodeClient(null, "http://unused"));
        controller = new EmergencyEpisodeController(service, dispositionService, observationStayService, alertService, mciBulkMintService);
        TrustContextHolder.set(new TrustContext(TENANT, "nurse-A", "PROVIDER", "TREATMENT",
                null, UUID.randomUUID(), FACILITY, null, null, AccessMode.INTERNAL));
    }

    @AfterEach
    void tearDown() {
        TrustContextHolder.clear();
    }

    @Test
    @DisplayName("opening an episode over HTTP returns the episode row with a real episode_id")
    void openReturnsEpisodeRow() {
        ResponseEntity<ApiResponse<Map<String, Object>>> resp =
                controller.open(Map.of("entryRoute", "WALK_IN", "facilityId", FACILITY.toString()));

        assertEquals(201, resp.getStatusCode().value());
        Map<String, Object> data = resp.getBody().data();
        assertNotNull(data.get("episode_id"));
        assertEquals(true, data.get("created"));
        assertEquals("WALK_IN", data.get("entry_route"));
    }

    @Test
    @DisplayName("a replayed pre-alert over HTTP is reported as not-created, same episode_id")
    void replayIsIdempotentOverHttp() {
        var first = controller.open(Map.of("entryRoute", "AMBULANCE",
                "entrySourceRef", "ems-http-1", "facilityId", FACILITY.toString()));
        var second = controller.open(Map.of("entryRoute", "AMBULANCE",
                "entrySourceRef", "ems-http-1", "facilityId", FACILITY.toString()));

        assertEquals(first.getBody().data().get("episode_id"), second.getBody().data().get("episode_id"));
        assertEquals(false, second.getBody().data().get("created"));
    }

    @Test
    @DisplayName("get() returns 404-shaped failure for an episode in a different tenant — read the exception, not a leak")
    void getRefusesCrossTenantRead() {
        var opened = controller.open(Map.of("entryRoute", "WALK_IN", "facilityId", FACILITY.toString()));
        UUID episodeId = UUID.fromString((String) opened.getBody().data().get("episode_id"));

        TrustContextHolder.set(new TrustContext(UUID.randomUUID(), "nurse-B", "PROVIDER", "TREATMENT",
                null, UUID.randomUUID(), FACILITY, null, null, AccessMode.INTERNAL));
        assertTrue(org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> controller.get(episodeId)).getMessage().contains("not found"));
    }

    @Test
    @DisplayName("the full handshake is reachable end to end over the controller: open -> arrive -> in-care -> request -> accept")
    void fullHandshakeOverHttp() {
        var opened = controller.open(Map.of("entryRoute", "WALK_IN",
                "facilityId", FACILITY.toString(), "journeyId", "J-HTTP-1"));
        UUID episodeId = UUID.fromString((String) opened.getBody().data().get("episode_id"));

        controller.arrive(episodeId, Map.of("journeyId", "J-HTTP-1"));
        var toCare = controller.transition(episodeId, Map.of("state", "OPEN_IN_CARE"));
        assertEquals("OPEN_IN_CARE", toCare.getBody().data().get("state"));

        var handoverResp = controller.requestHandover(episodeId, Map.of(
                "targetType", "ADMISSION", "targetService", "inpatient-service", "requestedBy", "nurse-A"));
        assertEquals(201, handoverResp.getStatusCode().value());
        UUID handoverId = UUID.fromString((String) handoverResp.getBody().data().get("handover_id"));

        var awaiting = controller.get(episodeId);
        assertEquals("OPEN_AWAITING_ACCEPTANCE", awaiting.getBody().data().get("state"));

        var accepted = controller.acceptHandover(handoverId, Map.of(
                "acceptedBy", "clerk", "acceptingRef", "ADM-HTTP-1", "acceptingService", "inpatient-service"));
        assertEquals("CLOSED_HANDED_OVER", accepted.getBody().data().get("state"));
        assertEquals(handoverId.toString(), accepted.getBody().data().get("handover_id"));
    }

    @Test
    @DisplayName("declining a handover over HTTP returns the episode to OPEN_IN_CARE")
    void declineOverHttp() {
        var opened = controller.open(Map.of("entryRoute", "WALK_IN",
                "facilityId", FACILITY.toString(), "journeyId", "J-HTTP-2"));
        UUID episodeId = UUID.fromString((String) opened.getBody().data().get("episode_id"));
        controller.arrive(episodeId, Map.of("journeyId", "J-HTTP-2"));
        controller.transition(episodeId, Map.of("state", "OPEN_IN_CARE"));
        var handoverResp = controller.requestHandover(episodeId, Map.of(
                "targetType", "THEATRE", "requestedBy", "doctor-B"));
        UUID handoverId = UUID.fromString((String) handoverResp.getBody().data().get("handover_id"));

        var declined = controller.declineHandover(handoverId, Map.of(
                "declinedBy", "surgeon-C", "reason", "no theatre slot"));
        assertEquals("OPEN_IN_CARE", declined.getBody().data().get("state"));
    }

    @Test
    @DisplayName("command-summary composes episode-by-state and alert-by-severity counts for a facility")
    void commandSummaryComposesCounts() {
        controller.open(Map.of("entryRoute", "WALK_IN", "facilityId", FACILITY.toString()));
        controller.open(Map.of("entryRoute", "AMBULANCE", "facilityId", FACILITY.toString()));

        var response = controller.commandSummary(FACILITY);
        assertEquals(200, response.getStatusCode().value());
        var data = response.getBody().data();
        assertEquals(2, data.get("open_episode_count"));
        assertEquals(0, data.get("open_alert_count"));
        @SuppressWarnings("unchecked")
        var byState = (Map<String, Long>) data.get("episodes_by_state");
        assertEquals(2L, byState.get("PRE_ARRIVAL"));
    }

    @Test
    @DisplayName("recording a disposition over HTTP closes the episode and is retrievable")
    void dispositionOverHttp() {
        var opened = controller.open(Map.of("entryRoute", "WALK_IN",
                "facilityId", FACILITY.toString(), "journeyId", "J-HTTP-DISP"));
        UUID episodeId = UUID.fromString((String) opened.getBody().data().get("episode_id"));
        controller.arrive(episodeId, Map.of("journeyId", "J-HTTP-DISP"));
        controller.transition(episodeId, Map.of("state", "OPEN_IN_CARE"));

        var recorded = controller.recordDisposition(episodeId, Map.of(
                "dispositionType", "DISCHARGED_HOME",
                "dispositionReason", "stable, safe to go home",
                "disposedBy", "nurse-A"));
        assertEquals(201, recorded.getStatusCode().value());
        assertEquals("DISCHARGED_HOME", recorded.getBody().data().get("disposition_type"));

        var episode = controller.get(episodeId);
        assertEquals("CLOSED_DISCHARGED", episode.getBody().data().get("state"));

        var fetched = controller.getDisposition(episodeId);
        assertEquals("DISCHARGED_HOME", fetched.getBody().data().get("disposition_type"));
    }

    @Test
    @DisplayName("an observation stay is reachable over HTTP: start, list, end")
    void observationStayOverHttp() {
        var opened = controller.open(Map.of("entryRoute", "WALK_IN", "facilityId", FACILITY.toString()));
        UUID episodeId = UUID.fromString((String) opened.getBody().data().get("episode_id"));

        var started = controller.startObservationStay(episodeId, Map.of("startedBy", "nurse-A"));
        assertEquals(201, started.getStatusCode().value());
        UUID stayId = UUID.fromString((String) started.getBody().data().get("stay_id"));

        var list = controller.observationStays(episodeId);
        assertEquals(1, list.getBody().data().size());

        var ended = controller.endObservationStay(stayId, Map.of("endedBy", "nurse-A", "outcome", "DISCHARGED"));
        assertNotNull(ended.getBody().data().get("ended_at"));
        assertEquals("DISCHARGED", ended.getBody().data().get("outcome"));
    }

    @Test
    @DisplayName("the board endpoint returns only episodes for the caller's own tenant")
    void boardIsTenantScoped() {
        controller.open(Map.of("entryRoute", "WALK_IN", "facilityId", FACILITY.toString()));

        TrustContextHolder.set(new TrustContext(UUID.randomUUID(), "nurse-C", "PROVIDER", "TREATMENT",
                null, UUID.randomUUID(), FACILITY, null, null, AccessMode.INTERNAL));
        controller.open(Map.of("entryRoute", "WALK_IN", "facilityId", FACILITY.toString()));

        TrustContextHolder.set(new TrustContext(TENANT, "nurse-A", "PROVIDER", "TREATMENT",
                null, UUID.randomUUID(), FACILITY, null, null, AccessMode.INTERNAL));
        var board = controller.board(FACILITY);
        assertEquals(1, board.getBody().data().size());
    }
}
