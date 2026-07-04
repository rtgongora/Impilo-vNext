package zw.gov.mohcc.impilo.rtc;

import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import zw.gov.mohcc.impilo.rtc.model.RtcParticipant;
import zw.gov.mohcc.impilo.rtc.model.RtcParticipantTokenRequest;
import zw.gov.mohcc.impilo.rtc.model.RtcSessionProvisionRequest;
import zw.gov.mohcc.impilo.rtc.model.RtcSessionResponse;
import zw.gov.mohcc.impilo.rtc.persistence.InMemoryRtcSessionPersistence;
import zw.gov.mohcc.impilo.sessiontemplates.SessionMode;
import zw.gov.mohcc.impilo.sessiontemplates.SessionTemplateRegistry;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;

class RtcGatewayServiceTest {

    private static final String TEST_SECRET = "test-livekit-secret-0123456789-0123456789";

    private final SessionTemplateRegistry templates = new SessionTemplateRegistry();
    private InMemoryRtcSessionPersistence sessions;
    private RtcOutboxPublisher outboxPublisher;
    private RtcGatewayService service;

    @BeforeEach
    void setUp() {
        sessions = new InMemoryRtcSessionPersistence();
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
                outboxPublisher,
                new SimpleMeterRegistry(),
                templates);
    }

    @Test
    void provisionsDevModeRoomWithoutLiveKitSecrets() {
        RtcSessionResponse response = service.provision(request());

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
    void issuesAdditionalParticipantTokenForProvisionedRoom() {
        service.provision(request());

        RtcSessionResponse token = service.issueToken(
                "session-1",
                new RtcParticipantTokenRequest(new RtcParticipant("patient-1", "Patient", "PATIENT")));

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

        RtcSessionResponse response = signing.provision(request(
                "event-1", "consent-1", "LIVE_EVENT",
                new RtcParticipant("viewer-1", "Viewer", "AUDIENCE")));

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
        RtcSessionResponse response = signing.provision(request(
                "call-1", "khuluma-call:call-1", "VIDEO",
                new RtcParticipant("caller-1", "Caller", "HOST")));

        Map<String, Object> videoGrant = videoGrant(response.accessToken());
        assertEquals(true, videoGrant.get("canPublish"));
        assertEquals(true, videoGrant.get("roomAdmin"));
    }

    @Test
    void hiddenObserverGrantCarriesHiddenClaim() throws Exception {
        RtcGatewayService signing = service(signingProperties());

        RtcSessionResponse response = signing.provision(request(
                "session-4", "consent-1", "TELEMEDICINE",
                new RtcParticipant("auditor-1", "Auditor", "OBSERVER")));

        Map<String, Object> videoGrant = videoGrant(response.accessToken());
        assertEquals(false, videoGrant.get("canPublish"));
        assertEquals(true, videoGrant.get("hidden"));
    }

    @Test
    void usesPerModeRoomPrefix() {
        RtcSessionResponse meeting = service.provision(request(
                "meet-1", null, "MEETING", new RtcParticipant("host-1", "Host", "HOST")));
        RtcSessionResponse liveEvent = service.provision(request(
                "event-2", null, "LIVE_EVENT", new RtcParticipant("host-2", "Host", "HOST")));

        assertTrue(meeting.roomName().startsWith("impilo-meet-"));
        assertTrue(liveEvent.roomName().startsWith("impilo-live-"));
    }

    @Test
    void unknownSessionTypeDefaultsToTelemedicine() {
        RtcSessionResponse response = service.provision(request(
                "session-5", "consent-1", "TELECONSULT",
                new RtcParticipant("provider-1", "Provider", "PROVIDER")));

        assertTrue(response.roomName().startsWith("impilo-telemedicine-"));
        assertEquals("TELEMEDICINE", sessions.findById("session-5").orElseThrow().sessionMode());
    }

    @Test
    void tokenTtlComesFromTemplate() throws Exception {
        RtcGatewayService signing = service(signingProperties());

        // live-event template TTL is 14400s; properties default is 300s in this test.
        RtcSessionResponse response = signing.provision(request(
                "event-3", null, "LIVE_EVENT", new RtcParticipant("speaker-1", "Speaker", "SPEAKER")));

        assertTrue(response.tokenExpiresAt().isAfter(Instant.now().plusSeconds(14000)));
    }

    @Test
    void createRoomBodyUsesTemplateMaxParticipants() {
        Map<String, Object> body = service.createRoomBody(
                "impilo-live-x", templates.get(SessionMode.LIVE_EVENT));

        assertEquals(500, body.get("maxParticipants"));
        assertEquals("impilo-live-x", body.get("name"));
    }

    @Test
    void persistsOwningServiceAndRefFromRequest() {
        service.provision(new RtcSessionProvisionRequest(
                "tenant-1", "session-6", null, null, "patient-1", "provider-1",
                "facility-1", "TREATMENT", "consent-1", "TELEMEDICINE",
                "PCT", "encounter:enc-9",
                new RtcParticipant("provider-1", "Provider", "PROVIDER"),
                Map.of()));

        var record = sessions.findById("session-6").orElseThrow();
        assertEquals("PCT", record.owningService());
        assertEquals("encounter:enc-9", record.owningRef());
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
                participant,
                Map.of()
        );
    }
}
