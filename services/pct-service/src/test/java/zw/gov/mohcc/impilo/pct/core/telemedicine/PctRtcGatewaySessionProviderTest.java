package zw.gov.mohcc.impilo.pct.core.telemedicine;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Proves a PCT teleconsult provisions on the canonical self-hosted media path: the provider maps the
 * neutral session request onto rtc-gateway's contract (incl. a consentReference so the VIDEO media
 * consent gate passes) and returns the real LiveKit room URL + token.
 */
class PctRtcGatewaySessionProviderTest {

    @Test
    void provision_mapsToRtcGateway_andReturnsRealMedia() {
        RestTemplate rt = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.createServer(rt);
        PctRtcGatewaySessionProvider provider =
                new PctRtcGatewaySessionProvider("http://rtc-test:8195", "national-spine", rt);

        server.expect(requestTo("http://rtc-test:8195/internal/v1/rtc/sessions"))
                .andExpect(method(org.springframework.http.HttpMethod.POST))
                .andExpect(jsonPath("$.sessionType").value("VIDEO"))
                .andExpect(jsonPath("$.patientId").value("cpid-1"))
                .andExpect(jsonPath("$.consentReference").value("pct-encounter:enc-1"))
                .andExpect(jsonPath("$.participant.role").value("PROVIDER"))
                .andExpect(jsonPath("$.participant.identity").value("prov-1"))
                .andRespond(withSuccess("""
                        {"data":{"id":"sess-1","provider":"LIVEKIT","roomName":"impilo-sess-1",
                          "roomUrl":"ws://livekit:7880","accessToken":"real-livekit-jwt","status":"PROVISIONED"}}
                        """, MediaType.APPLICATION_JSON));

        var request = new TelemedicineSessionProvider.SessionProvisioningRequest(
                UUID.randomUUID(), "cpid-1", "prov-1", "fac-1", "VIDEO", "ref-1", "enc-1",
                Map.of("sessionId", "s1"));

        SessionProvisioningResult result = provider.provision(request);

        assertThat(provider.providerType()).isEqualTo("RTC_GATEWAY");
        assertThat(result.roomUrl()).isEqualTo("ws://livekit:7880");
        assertThat(result.accessToken()).isEqualTo("real-livekit-jwt");
        assertThat(result.channel()).isEqualTo("rtc-gateway:sess-1");
        server.verify();
    }
}
