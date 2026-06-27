package zw.gov.mohcc.impilo.khuluma.client;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Proves the rtc-gateway media path request is correct: Khuluma sends a {@code consentReference}
 * (so rtc-gateway's media consent gate does not deny the AUDIO/VIDEO session) and the right
 * sessionType, and parses the real LiveKit token + room URL from the gateway response. With a live
 * rtc-gateway + self-hosted LiveKit this yields a genuine, joinable call — the only reason media
 * is "unavailable" is the gateway being down, which the client degrades for honestly.
 */
class RtcGatewayClientTest {

    @Test
    void provision_sendsConsentAndSessionType_andParsesRealToken() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate);
        RtcGatewayClient client = new RtcGatewayClient(restTemplate, "http://rtc-test:8195");

        server.expect(requestTo("http://rtc-test:8195/internal/v1/rtc/sessions"))
                .andExpect(method(org.springframework.http.HttpMethod.POST))
                .andExpect(jsonPath("$.sessionType").value("VIDEO"))
                .andExpect(jsonPath("$.consentReference").value("khuluma-call:call-1"))
                .andExpect(jsonPath("$.participant.identity").value("provider-a"))
                .andRespond(withSuccess("""
                        {"data":{"id":"sess-1","provider":"livekit","roomName":"impilo-call-1",
                          "roomUrl":"ws://livekit:7880","accessToken":"real-livekit-jwt","status":"PROVISIONED"}}
                        """, MediaType.APPLICATION_JSON));

        RtcGatewayClient.RtcSessionResult result = client.provision(
                "tenant-1", "call-1", "cpid-9", "provider-a", "CARE_COORDINATION", "VIDEO",
                "khuluma-call:call-1", "provider-a", "Dr A", "HOST");

        assertThat(result.available()).isTrue();
        assertThat(result.accessToken()).isEqualTo("real-livekit-jwt");
        assertThat(result.roomUrl()).isEqualTo("ws://livekit:7880");
        assertThat(result.provider()).isEqualTo("livekit");
        server.verify();
    }

    @Test
    void provision_degradesHonestly_whenGatewayDown() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate);
        RtcGatewayClient client = new RtcGatewayClient(restTemplate, "http://rtc-test:8195");

        server.expect(requestTo("http://rtc-test:8195/internal/v1/rtc/sessions"))
                .andRespond(withServerError());

        RtcGatewayClient.RtcSessionResult result = client.provision(
                "tenant-1", "call-2", "cpid", null, "CARE_COORDINATION", "AUDIO",
                null, "provider-a", "Dr A", "HOST");

        assertThat(result.available()).isFalse();
        assertThat(result.error()).isNotNull();
    }
}
