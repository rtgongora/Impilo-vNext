package zw.gov.mohcc.impilo.rtc;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import zw.gov.mohcc.impilo.rtc.model.RtcParticipant;
import zw.gov.mohcc.impilo.rtc.model.RtcParticipantTokenRequest;
import zw.gov.mohcc.impilo.rtc.model.RtcSessionProvisionRequest;
import zw.gov.mohcc.impilo.rtc.model.RtcSessionResponse;
import zw.gov.mohcc.impilo.rtc.persistence.InMemoryRtcSessionPersistence;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;

class RtcGatewayServiceTest {

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
        service = new RtcGatewayService(
                props,
                new LiveKitTokenService(props),
                sessions,
                outboxPublisher,
                new SimpleMeterRegistry());
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
        RtcGatewayService strictService = new RtcGatewayService(
                props, new LiveKitTokenService(props), sessions, outboxPublisher, new SimpleMeterRegistry());

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
        RtcGatewayService strictService = new RtcGatewayService(
                props, new LiveKitTokenService(props), sessions, outboxPublisher, new SimpleMeterRegistry());

        RtcSessionProvisionRequest request = new RtcSessionProvisionRequest(
                "tenant-1",
                "session-2",
                "ref-1",
                "enc-1",
                "patient-1",
                "provider-1",
                "facility-1",
                "TREATMENT",
                null,
                "VIDEO",
                new RtcParticipant("provider-1", "Provider", "PROVIDER"),
                Map.of()
        );

        assertThrows(IllegalArgumentException.class, () -> strictService.provision(request));
    }

    private RtcGatewayProperties properties() {
        RtcGatewayProperties props = new RtcGatewayProperties();
        props.getGateway().setRoomPrefix("test-room");
        props.getGateway().setTokenTtlSeconds(300);
        return props;
    }

    private RtcSessionProvisionRequest request() {
        return new RtcSessionProvisionRequest(
                "tenant-1",
                "session-1",
                "ref-1",
                "enc-1",
                "patient-1",
                "provider-1",
                "facility-1",
                "TREATMENT",
                "consent-1",
                "VIDEO",
                new RtcParticipant("provider-1", "Provider", "PROVIDER"),
                Map.of()
        );
    }
}
