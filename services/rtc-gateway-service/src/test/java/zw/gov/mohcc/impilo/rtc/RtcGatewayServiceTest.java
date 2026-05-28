package zw.gov.mohcc.impilo.rtc;

import org.junit.jupiter.api.Test;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import zw.gov.mohcc.impilo.rtc.model.RtcParticipant;
import zw.gov.mohcc.impilo.rtc.model.RtcParticipantTokenRequest;
import zw.gov.mohcc.impilo.rtc.model.RtcSessionProvisionRequest;
import zw.gov.mohcc.impilo.rtc.model.RtcSessionResponse;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RtcGatewayServiceTest {

    @Test
    void provisionsDevModeRoomWithoutLiveKitSecrets() {
        RtcGatewayProperties props = properties();
        props.getGateway().setDevModeEnabled(true);
        RtcGatewayService service = new RtcGatewayService(props, new LiveKitTokenService(props), new RtcSessionStore(), new SimpleMeterRegistry());

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
        RtcGatewayService service = new RtcGatewayService(props, new LiveKitTokenService(props), new RtcSessionStore(), new SimpleMeterRegistry());

        assertThrows(IllegalStateException.class, () -> service.provision(request()));
    }

    @Test
    void issuesAdditionalParticipantTokenForProvisionedRoom() {
        RtcGatewayProperties props = properties();
        props.getGateway().setDevModeEnabled(true);
        RtcGatewayService service = new RtcGatewayService(props, new LiveKitTokenService(props), new RtcSessionStore(), new SimpleMeterRegistry());
        service.provision(request());

        RtcSessionResponse token = service.issueToken(
                "session-1",
                new RtcParticipantTokenRequest(new RtcParticipant("patient-1", "Patient", "PATIENT")));

        assertNotNull(token.accessToken());
        assertEquals("session-1", token.id());
    }

    @Test
    void rejectsMediaProvisioningWithoutConsentReferenceWhenRequired() {
        RtcGatewayProperties props = properties();
        props.getGateway().setDevModeEnabled(true);
        props.getGateway().setRequireConsentReferenceForMedia(true);
        RtcGatewayService service = new RtcGatewayService(props, new LiveKitTokenService(props), new RtcSessionStore(), new SimpleMeterRegistry());

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

        assertThrows(IllegalArgumentException.class, () -> service.provision(request));
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
