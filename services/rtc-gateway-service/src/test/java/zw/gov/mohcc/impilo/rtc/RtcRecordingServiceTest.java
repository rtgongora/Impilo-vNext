package zw.gov.mohcc.impilo.rtc;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import zw.gov.mohcc.impilo.rtc.model.RtcRecordingRecord;
import zw.gov.mohcc.impilo.rtc.model.RtcRecordingStartRequest;
import zw.gov.mohcc.impilo.rtc.model.RtcSessionRecord;
import zw.gov.mohcc.impilo.rtc.persistence.InMemoryRtcSessionPersistence;
import zw.gov.mohcc.impilo.rtc.persistence.RtcRecordingPersistence;
import zw.gov.mohcc.impilo.sessiontemplates.SessionTemplateRegistry;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RtcRecordingServiceTest {

    private final SessionTemplateRegistry templates = new SessionTemplateRegistry();

    private InMemoryRtcSessionPersistence sessions;
    private RtcRecordingPersistence recordings;
    private LiveKitEgressClient egressClient;
    private RtcOutboxPublisher outbox;
    private RtcGatewayProperties properties;
    private RtcRecordingService service;

    @BeforeEach
    void setUp() {
        sessions = new InMemoryRtcSessionPersistence();
        recordings = mock(RtcRecordingPersistence.class);
        egressClient = mock(LiveKitEgressClient.class);
        outbox = mock(RtcOutboxPublisher.class);
        properties = new RtcGatewayProperties();
        properties.getGateway().setEgressEnabled(true);
        properties.getRecording().getS3().setBucket("impilo-recordings");
        service = new RtcRecordingService(properties, sessions, recordings, egressClient, outbox, templates);
    }

    // ── Policy gates (fail-closed, template-driven) ─────────────────

    @Test
    void telemedicinePatientMayNotStartRecording() {
        saveSession("session-1", "TELEMEDICINE", "consent-1");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.start("session-1", startRequest("patient-1", "PATIENT")));

        assertTrue(ex.getMessage().contains("may not start a recording"));
        verifyNoInteractions(egressClient);
    }

    @Test
    void telemedicineProviderStartsRecordingWithConsent() {
        saveSession("session-1", "TELEMEDICINE", "consent-1");
        when(egressClient.startRoomComposite(anyString(), anyString()))
                .thenReturn(new LiveKitEgressClient.EgressStart("EG_1", "recordings/room-session-1-1.mp4"));

        service.start("session-1", startRequest("provider-1", "PROVIDER"));

        verify(egressClient).startRoomComposite("impilo-telemedicine-session-1", "consult");
        verify(recordings).insertRequested("session-1", "EG_1", "consult",
                "impilo-recordings", "recordings/room-session-1-1.mp4",
                "provider-1", "PROVIDER", "consent-1");
        Map<String, Object> payload = captureRequestedPayload();
        assertEquals("EG_1", payload.get("egressId"));
        assertEquals("PROVIDER", payload.get("startedByRole"));
        assertEquals("consent-1", payload.get("consentReference"));
        assertEquals("CLINICAL", payload.get("sensitivityClass"));
        assertEquals("PCT", payload.get("artifactOwner"));
    }

    @Test
    void telemedicineWithoutConsentReferenceIsRefused() {
        saveSession("session-1", "TELEMEDICINE", null);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.start("session-1", startRequest("provider-1", "PROVIDER")));

        assertTrue(ex.getMessage().contains("consent"));
        verifyNoInteractions(egressClient);
    }

    @Test
    void liveEventHostStartsRecordingWithoutConsent() {
        saveSession("event-1", "LIVE_EVENT", null);
        when(egressClient.startRoomComposite(anyString(), anyString()))
                .thenReturn(new LiveKitEgressClient.EgressStart("EG_2", "recordings/room-event-1-1.mp4"));

        service.start("event-1", startRequest("host-1", "HOST"));

        verify(egressClient).startRoomComposite("impilo-live-event-1", "stage");
        verify(recordings).insertRequested(eq("event-1"), eq("EG_2"), eq("stage"),
                eq("impilo-recordings"), anyString(), eq("host-1"), eq("HOST"), any());
    }

    @Test
    void secondStartWhileRecordingInFlightIsRefused() {
        saveSession("session-1", "TELEMEDICINE", "consent-1");
        when(recordings.hasInFlight("session-1")).thenReturn(true);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.start("session-1", startRequest("provider-1", "PROVIDER")));

        assertTrue(ex.getMessage().contains("already in progress"));
        verifyNoInteractions(egressClient);
    }

    @Test
    void startRefusedWhenEgressDisabled() {
        properties.getGateway().setEgressEnabled(false);
        saveSession("session-1", "TELEMEDICINE", "consent-1");

        assertThrows(IllegalStateException.class,
                () -> service.start("session-1", startRequest("provider-1", "PROVIDER")));
        verifyNoInteractions(egressClient);
    }

    @Test
    void startRefusedOnEndedSession() {
        RtcSessionRecord session = session("session-1", "TELEMEDICINE", "consent-1");
        sessions.save(session.withStatus("ENDED"));

        assertThrows(IllegalArgumentException.class,
                () -> service.start("session-1", startRequest("provider-1", "PROVIDER")));
        verifyNoInteractions(egressClient);
    }

    @Test
    void unknownSessionIsNotFound() {
        assertThrows(RtcNotFoundException.class,
                () -> service.start("nope", startRequest("provider-1", "PROVIDER")));
    }

    // ── Stop transitions ─────────────────────────────────────────────

    @Test
    void stopTransitionsInFlightRecordingToStopped() {
        saveSession("session-1", "TELEMEDICINE", "consent-1");
        RtcRecordingRecord inFlight = recording("session-1", "EG_1", "ACTIVE");
        when(recordings.findInFlight("session-1")).thenReturn(Optional.of(inFlight));
        when(recordings.findByEgressId("EG_1"))
                .thenReturn(Optional.of(recording("session-1", "EG_1", "STOPPED")));

        RtcRecordingRecord stopped = service.stop("session-1");

        verify(egressClient).stopEgress("EG_1");
        verify(recordings).markStopped("EG_1");
        assertEquals("STOPPED", stopped.status());
    }

    @Test
    void stopWithoutInFlightRecordingIsNotFound() {
        saveSession("session-1", "TELEMEDICINE", "consent-1");
        when(recordings.findInFlight("session-1")).thenReturn(Optional.empty());

        assertThrows(RtcNotFoundException.class, () -> service.stop("session-1"));
        verifyNoInteractions(egressClient);
    }

    // ── Helpers ──────────────────────────────────────────────────────

    private void saveSession(String sessionId, String mode, String consentReference) {
        sessions.save(session(sessionId, mode, consentReference));
    }

    private RtcSessionRecord session(String sessionId, String mode, String consentReference) {
        String prefix = "LIVE_EVENT".equals(mode) ? "impilo-live-" : "impilo-telemedicine-";
        return new RtcSessionRecord(
                sessionId, "tenant-1", "LIVEKIT", prefix + sessionId, "wss://livekit.test",
                "PROVISIONED", mode, "PCT", "encounter:enc-1", null,
                "patient-1", "provider-1", "enc-1", "ref-1", consentReference,
                "rtc:" + prefix + sessionId, Map.of(), Map.of(), Instant.now(), Instant.now());
    }

    private RtcRecordingRecord recording(String sessionId, String egressId, String status) {
        return new RtcRecordingRecord(1L, sessionId, egressId, status, "consult",
                null, null, null, null, null, "provider-1", "PROVIDER", "consent-1",
                Instant.now(), null);
    }

    private RtcRecordingStartRequest startRequest(String startedBy, String role) {
        return new RtcRecordingStartRequest(startedBy, role, null);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> captureRequestedPayload() {
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(outbox).append(eq("RtcSession"), eq("session-1"),
                eq(RtcRecordingService.EVT_RECORDING_REQUESTED), captor.capture());
        return captor.getValue();
    }
}
