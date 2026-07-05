package zw.gov.mohcc.impilo.rtc;

import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import zw.gov.mohcc.impilo.rtc.model.RtcParticipant;
import zw.gov.mohcc.impilo.rtc.model.RtcParticipantRecord;
import zw.gov.mohcc.impilo.rtc.model.RtcParticipantTokenRequest;
import zw.gov.mohcc.impilo.rtc.model.RtcParticipantView;
import zw.gov.mohcc.impilo.rtc.model.RtcSessionProvisionRequest;
import zw.gov.mohcc.impilo.rtc.model.RtcSessionResponse;
import zw.gov.mohcc.impilo.rtc.model.RtcSessionResult;
import zw.gov.mohcc.impilo.rtc.model.RtcWaitingResponse;
import zw.gov.mohcc.impilo.rtc.persistence.InMemoryRtcParticipantPersistence;
import zw.gov.mohcc.impilo.rtc.persistence.InMemoryRtcSessionPersistence;
import zw.gov.mohcc.impilo.sessiontemplates.SessionMode;
import zw.gov.mohcc.impilo.sessiontemplates.SessionTemplateRegistry;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class RtcGatewayServiceTest {

    private static final String TEST_SECRET = "test-livekit-secret-0123456789-0123456789";

    private final SessionTemplateRegistry templates = new SessionTemplateRegistry();
    private InMemoryRtcSessionPersistence sessions;
    private InMemoryRtcParticipantPersistence participants;
    private RtcOutboxPublisher outboxPublisher;
    private RtcGatewayService service;

    @BeforeEach
    void setUp() {
        sessions = new InMemoryRtcSessionPersistence();
        participants = new InMemoryRtcParticipantPersistence();
        outboxPublisher = mock(RtcOutboxPublisher.class);
        doNothing().when(outboxPublisher).append(anyString(), anyString(), anyString(), any());
        RtcGatewayProperties props = properties();
        props.getGateway().setDevModeEnabled(true);
        service = service(props);
    }

    private RtcGatewayService service(RtcGatewayProperties props) {
        return new RtcGatewayService(
                props,
                new LiveKitTokenService(props),
                sessions,
                participants,
                outboxPublisher,
                new SimpleMeterRegistry(),
                templates);
    }

    @Test
    void provisionsDevModeRoomWithoutLiveKitSecrets() {
        RtcSessionResponse response = session(service.provision(request()));

        assertEquals("LIVEKIT", response.provider());
        assertEquals("PROVISIONED", response.status());
        assertNotNull(response.accessToken());
        assertNotNull(response.roomUrl());
        assertTrue(response.roomUrl().startsWith("dev://") || response.roomUrl().startsWith("ws"));
    }

    @Test
    void failsClosedWhenLiveKitDisabledOutsideDevMode() {
        RtcGatewayProperties props = properties();
        props.getGateway().setDevModeEnabled(false);
        RtcGatewayService strictService = service(props);

        assertThrows(IllegalStateException.class, () -> strictService.provision(request()));
    }

    @Test
    void issuesAdditionalRoomAdminTokenForProvisionedRoom() {
        service.provision(request());

        RtcSessionResponse token = session(service.issueToken(
                "session-1",
                new RtcParticipantTokenRequest(new RtcParticipant("provider-2", "Second Provider", "PROVIDER"))));

        assertNotNull(token.accessToken());
        assertEquals("session-1", token.id());
    }

    @Test
    void opsHealthReportsDevPreviewBoundaryWithoutSecrets() {
        service.provision(request());

        Map<String, Object> health = service.opsHealth();

        assertEquals("LIVEKIT", health.get("provider"));
        assertEquals(true, health.get("devModeEnabled"));
        assertEquals(false, health.get("livekitEnabled"));
        assertEquals(false, health.get("livekitConfigured"));
        assertFalse((Boolean) health.get("productionReady"));
        assertNotNull(health.get("serverUrl"));
        assertEquals(1, health.get("activeSessions"));
    }

    @Test
    void rejectsMediaProvisioningWithoutConsentReferenceWhenRequired() {
        RtcGatewayProperties props = properties();
        props.getGateway().setDevModeEnabled(true);
        props.getGateway().setRequireConsentReferenceForMedia(true);
        RtcGatewayService strictService = service(props);

        RtcSessionProvisionRequest request = request(
                "session-2", null, "VIDEO", new RtcParticipant("provider-1", "Provider", "PROVIDER"));

        assertThrows(IllegalArgumentException.class, () -> strictService.provision(request));
    }

    // ── Session-template grant enforcement ─────────────────────────

    @Test
    void audienceRoleForLiveEventModeGetsNoPublishToken() throws Exception {
        RtcGatewayService signing = service(signingProperties());

        RtcSessionResponse response = session(signing.provision(request(
                "event-1", "consent-1", "LIVE_EVENT",
                new RtcParticipant("viewer-1", "Viewer", "AUDIENCE"))));

        Map<String, Object> videoGrant = videoGrant(response.accessToken());
        assertEquals(false, videoGrant.get("canPublish"));
        assertEquals(true, videoGrant.get("canSubscribe"));
        assertEquals(false, videoGrant.get("canPublishData"));
        assertNull(videoGrant.get("roomAdmin"));
    }

    @Test
    void refusesRoleWithoutGrantInTemplate() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.provision(request(
                        "session-3", "consent-1", "VIDEO",
                        new RtcParticipant("someone", "Someone", "AUDIENCE"))));

        assertTrue(ex.getMessage().contains("not permitted for session mode TELEMEDICINE"));
    }

    @Test
    void legacyHostRoleMapsToProviderForTelemedicine() throws Exception {
        RtcGatewayService signing = service(signingProperties());

        // khuluma sends sessionType VIDEO/AUDIO with role HOST for native calls.
        RtcSessionResponse response = session(signing.provision(request(
                "call-1", "khuluma-call:call-1", "VIDEO",
                new RtcParticipant("caller-1", "Caller", "HOST"))));

        Map<String, Object> videoGrant = videoGrant(response.accessToken());
        assertEquals(true, videoGrant.get("canPublish"));
        assertEquals(true, videoGrant.get("roomAdmin"));
    }

    @Test
    void hiddenObserverGrantCarriesHiddenClaimAfterAdmission() throws Exception {
        RtcGatewayService signing = service(signingProperties());
        signing.provision(request("session-4", "consent-1", "TELEMEDICINE",
                new RtcParticipant("provider-1", "Provider", "PROVIDER")));

        // OBSERVER is not roomAdmin: it waits in the telemedicine lobby until admitted.
        signing.admit("session-4", "auditor-1", "provider-1");
        RtcSessionResponse response = session(signing.issueToken("session-4",
                new RtcParticipantTokenRequest(new RtcParticipant("auditor-1", "Auditor", "OBSERVER"))));

        Map<String, Object> videoGrant = videoGrant(response.accessToken());
        assertEquals(false, videoGrant.get("canPublish"));
        assertEquals(true, videoGrant.get("hidden"));
    }

    @Test
    void usesPerModeRoomPrefix() {
        RtcSessionResponse meeting = session(service.provision(request(
                "meet-1", null, "MEETING", new RtcParticipant("host-1", "Host", "HOST"))));
        RtcSessionResponse liveEvent = session(service.provision(request(
                "event-2", null, "LIVE_EVENT", new RtcParticipant("host-2", "Host", "HOST"))));

        assertTrue(meeting.roomName().startsWith("impilo-meet-"));
        assertTrue(liveEvent.roomName().startsWith("impilo-live-"));
    }

    @Test
    void unknownSessionTypeDefaultsToTelemedicine() {
        RtcSessionResponse response = session(service.provision(request(
                "session-5", "consent-1", "TELECONSULT",
                new RtcParticipant("provider-1", "Provider", "PROVIDER"))));

        assertTrue(response.roomName().startsWith("impilo-telemedicine-"));
        assertEquals("TELEMEDICINE", sessions.findById("session-5").orElseThrow().sessionMode());
    }

    @Test
    void tokenTtlComesFromTemplate() {
        RtcGatewayService signing = service(signingProperties());

        // live-event template TTL is 14400s; properties default is 300s in this test.
        RtcSessionResponse response = session(signing.provision(request(
                "event-3", null, "LIVE_EVENT", new RtcParticipant("speaker-1", "Speaker", "SPEAKER"))));

        assertTrue(response.tokenExpiresAt().isAfter(Instant.now().plusSeconds(14000)));
    }

    @Test
    void createRoomBodyUsesTemplateMaxParticipants() {
        Map<String, Object> body = service.createRoomBody(
                "impilo-live-x", templates.get(SessionMode.LIVE_EVENT), null);

        assertEquals(500, body.get("maxParticipants"));
        assertEquals("impilo-live-x", body.get("name"));
    }

    @Test
    void createRoomBodyPrefersRequestedCapacityOverTemplate() {
        Map<String, Object> body = service.createRoomBody(
                "impilo-live-x", templates.get(SessionMode.LIVE_EVENT), 120);

        assertEquals(120, body.get("maxParticipants"));
    }

    @Test
    void createRoomBodyIgnoresNonPositiveRequestedCapacity() {
        Map<String, Object> body = service.createRoomBody(
                "impilo-live-x", templates.get(SessionMode.LIVE_EVENT), 0);

        assertEquals(500, body.get("maxParticipants"));
    }

    @Test
    void provisionPersistsValidatedParentSessionLink() {
        service.provision(request()); // parent: session-1

        service.provision(new RtcSessionProvisionRequest(
                "tenant-1", "session-1-backstage", null, null, "producer-1", "producer-1",
                "facility-1", "OPERATIONS", null, "LIVE_EVENT",
                "LIVE", "event-77", "session-1", 20,
                new RtcParticipant("producer-1", "Producer", "PRODUCER"),
                Map.of()));

        var child = sessions.findById("session-1-backstage").orElseThrow();
        assertEquals("session-1", child.parentSessionId());
        assertTrue(child.roomName().endsWith("-backstage"));
    }

    @Test
    void provisionRejectsUnknownParentSession() {
        assertThrows(IllegalArgumentException.class, () -> service.provision(new RtcSessionProvisionRequest(
                "tenant-1", "orphan-backstage", null, null, "producer-1", "producer-1",
                "facility-1", "OPERATIONS", null, "LIVE_EVENT",
                "LIVE", "event-77", "no-such-session", null,
                new RtcParticipant("producer-1", "Producer", "PRODUCER"),
                Map.of())));
    }

    @Test
    void provisionRejectsCrossTenantParentSession() {
        service.provision(request()); // tenant-1, session-1

        assertThrows(IllegalArgumentException.class, () -> service.provision(new RtcSessionProvisionRequest(
                "tenant-2", "session-1-backstage", null, null, "producer-1", "producer-1",
                "facility-1", "OPERATIONS", null, "LIVE_EVENT",
                "LIVE", "event-77", "session-1", null,
                new RtcParticipant("producer-1", "Producer", "PRODUCER"),
                Map.of())));
    }

    @Test
    void persistsOwningServiceAndRefFromRequest() {
        service.provision(new RtcSessionProvisionRequest(
                "tenant-1", "session-6", null, null, "patient-1", "provider-1",
                "facility-1", "TREATMENT", "consent-1", "TELEMEDICINE",
                "PCT", "encounter:enc-9", null, null,
                new RtcParticipant("provider-1", "Provider", "PROVIDER"),
                Map.of()));

        var record = sessions.findById("session-6").orElseThrow();
        assertEquals("PCT", record.owningService());
        assertEquals("encounter:enc-9", record.owningRef());
    }

    // ── Waiting-room lobby ──────────────────────────────────────────

    @Test
    void patientFirstTokenRequestWaitsWithRowAndEvent() {
        service.provision(request());

        RtcSessionResult result = service.issueToken("session-1",
                tokenRequest("patient-1", "Patient", "PATIENT"));

        RtcWaitingResponse waiting = assertInstanceOf(RtcWaitingResponse.class, result);
        assertEquals("WAITING", waiting.status());
        assertEquals("session-1", waiting.sessionId());
        assertEquals("patient-1", waiting.identity());

        RtcParticipantRecord row = participants.find("session-1", "patient-1").orElseThrow();
        assertEquals(RtcParticipantRecord.STATE_WAITING, row.state());
        assertNotNull(row.requestedAt());

        Map<String, Object> payload = captureEvent(RtcGatewayService.EVT_PARTICIPANT_WAITING);
        assertEquals("session-1", payload.get("sessionId"));
        assertEquals("TELEMEDICINE", payload.get("sessionMode"));
        assertEquals("PCT", payload.get("owningService"));
        assertEquals("patient-1", payload.get("identity"));
        assertEquals("Patient", payload.get("displayName"));
        assertEquals("PATIENT", payload.get("role"));
        assertNotNull(payload.get("eventId"));
    }

    @Test
    void waitingEventEmittedOnlyOnFirstTransitionNotEveryPoll() {
        service.provision(request());

        service.issueToken("session-1", tokenRequest("patient-1", "Patient", "PATIENT"));
        service.issueToken("session-1", tokenRequest("patient-1", "Patient", "PATIENT"));
        service.issueToken("session-1", tokenRequest("patient-1", "Patient", "PATIENT"));

        verify(outboxPublisher, times(1)).append(
                eq("RtcSession"), eq("session-1"), eq(RtcGatewayService.EVT_PARTICIPANT_WAITING), any());
    }

    @Test
    void providerTokenIsImmediateAndAutoAdmitted() {
        RtcSessionResponse response = session(service.provision(request()));

        assertNotNull(response.accessToken());
        RtcParticipantRecord row = participants.find("session-1", "provider-1").orElseThrow();
        assertEquals(RtcParticipantRecord.STATE_ADMITTED, row.state());
        assertEquals("ROOM_ADMIN_AUTO", row.admittedBy());
        verify(outboxPublisher, never()).append(
                anyString(), anyString(), eq(RtcGatewayService.EVT_PARTICIPANT_WAITING), any());
    }

    @Test
    void provisionPathAlsoGatesWaitingRoles() {
        service.provision(request());

        // A patient hitting the provision (join-existing) path is lobby-gated too.
        RtcSessionResult result = service.provision(request(
                "session-1", "consent-1", "VIDEO", new RtcParticipant("patient-1", "Patient", "PATIENT")));

        RtcWaitingResponse waiting = assertInstanceOf(RtcWaitingResponse.class, result);
        assertEquals("WAITING", waiting.status());
    }

    @Test
    void admitThenPatientReRequestGetsToken() {
        service.provision(request());
        service.issueToken("session-1", tokenRequest("patient-1", "Patient", "PATIENT"));

        RtcParticipantView admitted = service.admit("session-1", "patient-1", "provider-1");

        assertEquals(RtcParticipantRecord.STATE_ADMITTED, admitted.state());
        assertEquals("provider-1", admitted.admittedBy());
        assertNotNull(admitted.admittedAt());

        RtcSessionResponse token = session(service.issueToken("session-1",
                tokenRequest("patient-1", "Patient", "PATIENT")));
        assertNotNull(token.accessToken());

        Map<String, Object> payload = captureEvent(RtcGatewayService.EVT_PARTICIPANT_ADMITTED);
        assertEquals("patient-1", payload.get("identity"));
        assertEquals("provider-1", payload.get("admittedBy"));
    }

    @Test
    void admitIsIdempotent() {
        service.provision(request());
        service.issueToken("session-1", tokenRequest("patient-1", "Patient", "PATIENT"));

        service.admit("session-1", "patient-1", "provider-1");
        RtcParticipantView second = service.admit("session-1", "patient-1", "provider-9");

        assertEquals(RtcParticipantRecord.STATE_ADMITTED, second.state());
        assertEquals("provider-1", second.admittedBy());
        verify(outboxPublisher, times(1)).append(
                eq("RtcSession"), eq("session-1"), eq(RtcGatewayService.EVT_PARTICIPANT_ADMITTED), any());
    }

    @Test
    void denyReturnsDeniedStatusOnSubsequentTokenRequests() {
        service.provision(request());
        service.issueToken("session-1", tokenRequest("patient-1", "Patient", "PATIENT"));

        RtcParticipantView denied = service.deny("session-1", "patient-1", "wrong appointment");
        assertEquals(RtcParticipantRecord.STATE_DENIED, denied.state());
        assertEquals("wrong appointment", denied.deniedReason());

        RtcSessionResult result = service.issueToken("session-1",
                tokenRequest("patient-1", "Patient", "PATIENT"));
        RtcWaitingResponse status = assertInstanceOf(RtcWaitingResponse.class, result);
        assertEquals("DENIED", status.status());

        Map<String, Object> payload = captureEvent(RtcGatewayService.EVT_PARTICIPANT_DENIED);
        assertEquals("patient-1", payload.get("identity"));
        assertEquals("wrong appointment", payload.get("reason"));
    }

    @Test
    void refreshForAdmittedParticipantMintsToken() {
        service.provision(request());
        service.admit("session-1", "patient-1", "provider-1");

        RtcSessionResponse refreshed = session(service.refreshToken("session-1",
                tokenRequest("patient-1", "Patient", "PATIENT")));

        assertNotNull(refreshed.accessToken());
        assertEquals("session-1", refreshed.id());
    }

    @Test
    void refreshForWaitingParticipantStaysWaiting() {
        service.provision(request());
        service.issueToken("session-1", tokenRequest("patient-1", "Patient", "PATIENT"));

        RtcSessionResult result = service.refreshToken("session-1",
                tokenRequest("patient-1", "Patient", "PATIENT"));

        RtcWaitingResponse waiting = assertInstanceOf(RtcWaitingResponse.class, result);
        assertEquals("WAITING", waiting.status());
        // Still only the single first-transition waiting event.
        verify(outboxPublisher, times(1)).append(
                eq("RtcSession"), eq("session-1"), eq(RtcGatewayService.EVT_PARTICIPANT_WAITING), any());
    }

    @Test
    void refreshForDeniedParticipantStaysDenied() {
        service.provision(request());
        service.deny("session-1", "patient-1", null);

        RtcSessionResult result = service.refreshToken("session-1",
                tokenRequest("patient-1", "Patient", "PATIENT"));

        RtcWaitingResponse status = assertInstanceOf(RtcWaitingResponse.class, result);
        assertEquals("DENIED", status.status());
    }

    @Test
    void leftParticipantMayReconnectWithoutReAdmission() {
        service.provision(request());
        service.admit("session-1", "patient-1", "provider-1");
        participants.updateState("session-1", "patient-1", RtcParticipantRecord.STATE_LEFT);

        RtcSessionResponse token = session(service.issueToken("session-1",
                tokenRequest("patient-1", "Patient", "PATIENT")));

        assertNotNull(token.accessToken());
    }

    @Test
    void meetingKnockLobbyGatesNonAdminParticipantTemplateDriven() {
        service.provision(request("meet-2", null, "MEETING",
                new RtcParticipant("host-1", "Host", "HOST")));

        RtcSessionResult gated = service.issueToken("meet-2",
                tokenRequest("staff-1", "Staff", "PARTICIPANT"));
        RtcWaitingResponse waiting = assertInstanceOf(RtcWaitingResponse.class, gated);
        assertEquals("WAITING", waiting.status());

        service.admit("meet-2", "staff-1", "host-1");
        RtcSessionResponse token = session(service.issueToken("meet-2",
                tokenRequest("staff-1", "Staff", "PARTICIPANT")));
        assertNotNull(token.accessToken());
    }

    @Test
    void listParticipantsReturnsLobbyRoster() {
        service.provision(request());
        service.issueToken("session-1", tokenRequest("patient-1", "Patient", "PATIENT"));
        service.issueToken("session-1", tokenRequest("caregiver-1", "Caregiver", "CAREGIVER"));
        service.admit("session-1", "patient-1", "provider-1");

        List<RtcParticipantView> roster = service.listParticipants("session-1");

        assertEquals(3, roster.size()); // provider (auto-admitted) + patient + caregiver
        RtcParticipantView patient = roster.stream()
                .filter(p -> p.identity().equals("patient-1")).findFirst().orElseThrow();
        assertEquals(RtcParticipantRecord.STATE_ADMITTED, patient.state());
        assertEquals("provider-1", patient.admittedBy());
        RtcParticipantView caregiver = roster.stream()
                .filter(p -> p.identity().equals("caregiver-1")).findFirst().orElseThrow();
        assertEquals(RtcParticipantRecord.STATE_WAITING, caregiver.state());
    }

    @Test
    void lobbyEndpointsThrowNotFoundForUnknownSession() {
        assertThrows(RtcNotFoundException.class, () -> service.admit("nope", "p-1", "a-1"));
        assertThrows(RtcNotFoundException.class, () -> service.deny("nope", "p-1", null));
        assertThrows(RtcNotFoundException.class, () -> service.listParticipants("nope"));
        assertThrows(RtcNotFoundException.class,
                () -> service.issueToken("nope", tokenRequest("p-1", "P", "PATIENT")));
        assertThrows(RtcNotFoundException.class,
                () -> service.refreshToken("nope", tokenRequest("p-1", "P", "PATIENT")));
    }

    // ── Media profiles ──────────────────────────────────────────────

    @Test
    void audioOnlyProfileGrantsOnlyMicrophoneSource() throws Exception {
        RtcGatewayService signing = service(signingProperties());
        signing.provision(request("session-7", "consent-1", "TELEMEDICINE",
                new RtcParticipant("provider-1", "Provider", "PROVIDER")));
        signing.admit("session-7", "patient-1", "provider-1");

        RtcSessionResponse response = session(signing.issueToken("session-7",
                new RtcParticipantTokenRequest(
                        new RtcParticipant("patient-1", "Patient", "PATIENT", "AUDIO_ONLY"))));

        assertEquals("AUDIO_ONLY", response.mediaProfile());
        Map<String, Object> videoGrant = videoGrant(response.accessToken());
        assertEquals(List.of("microphone"), videoGrant.get("canPublishSources"));
        assertEquals(true, videoGrant.get("canPublish"));
    }

    @Test
    void interpreterDefaultsToAudioOnlyFromTemplateGrant() throws Exception {
        RtcGatewayService signing = service(signingProperties());
        signing.provision(request("session-8", "consent-1", "TELEMEDICINE",
                new RtcParticipant("provider-1", "Provider", "PROVIDER")));
        signing.admit("session-8", "interp-1", "provider-1");

        RtcSessionResponse response = session(signing.issueToken("session-8",
                new RtcParticipantTokenRequest(
                        new RtcParticipant("interp-1", "Interpreter", "INTERPRETER"))));

        assertEquals("AUDIO_ONLY", response.mediaProfile());
        assertEquals(List.of("microphone"), videoGrant(response.accessToken()).get("canPublishSources"));
    }

    @Test
    void fullProfileLeavesAllPublishSources() throws Exception {
        RtcGatewayService signing = service(signingProperties());

        RtcSessionResponse response = session(signing.provision(request(
                "session-9", "consent-1", "TELEMEDICINE",
                new RtcParticipant("provider-1", "Provider", "PROVIDER"))));

        assertEquals("FULL", response.mediaProfile());
        assertNull(videoGrant(response.accessToken()).get("canPublishSources"));
    }

    @Test
    void rejectsUnknownMediaProfile() {
        service.provision(request());

        assertThrows(IllegalArgumentException.class, () -> service.issueToken("session-1",
                new RtcParticipantTokenRequest(
                        new RtcParticipant("patient-1", "Patient", "PATIENT", "VIDEO_ONLY"))));
    }

    // ── Helpers ─────────────────────────────────────────────────────

    private RtcSessionResponse session(RtcSessionResult result) {
        return assertInstanceOf(RtcSessionResponse.class, result);
    }

    private RtcParticipantTokenRequest tokenRequest(String identity, String displayName, String role) {
        return new RtcParticipantTokenRequest(new RtcParticipant(identity, displayName, role));
    }

    private Map<String, Object> captureEvent(String eventType) {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(outboxPublisher).append(eq("RtcSession"), anyString(), eq(eventType), captor.capture());
        return captor.getValue();
    }

    private Map<String, Object> videoGrant(String accessToken) throws Exception {
        SignedJWT jwt = SignedJWT.parse(accessToken);
        @SuppressWarnings("unchecked")
        Map<String, Object> videoGrant = (Map<String, Object>) jwt.getJWTClaimsSet().getClaim("video");
        assertNotNull(videoGrant, "video grant claim missing");
        return videoGrant;
    }

    private RtcGatewayProperties properties() {
        RtcGatewayProperties props = new RtcGatewayProperties();
        props.getGateway().setRoomPrefix("test-room");
        props.getGateway().setTokenTtlSeconds(300);
        return props;
    }

    /** Dev mode + LiveKit configured: real HS256 tokens without any HTTP room calls. */
    private RtcGatewayProperties signingProperties() {
        RtcGatewayProperties props = properties();
        props.getGateway().setDevModeEnabled(true);
        props.getLivekit().setEnabled(true);
        props.getLivekit().setUrl("http://livekit.test:7880");
        props.getLivekit().setApiKey("test-api-key");
        props.getLivekit().setApiSecret(TEST_SECRET);
        return props;
    }

    private RtcSessionProvisionRequest request() {
        return request("session-1", "consent-1", "VIDEO",
                new RtcParticipant("provider-1", "Provider", "PROVIDER"));
    }

    private RtcSessionProvisionRequest request(String sessionId, String consentReference,
                                               String sessionType, RtcParticipant participant) {
        return new RtcSessionProvisionRequest(
                "tenant-1",
                sessionId,
                "ref-1",
                "enc-1",
                "patient-1",
                "provider-1",
                "facility-1",
                "TREATMENT",
                consentReference,
                sessionType,
                null,
                null,
                null,
                null,
                participant,
                Map.of()
        );
    }
}
