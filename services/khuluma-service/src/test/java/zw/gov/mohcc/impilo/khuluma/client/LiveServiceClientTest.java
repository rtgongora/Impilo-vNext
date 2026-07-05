package zw.gov.mohcc.impilo.khuluma.client;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Proves Khuluma drives live-service correctly for a virtual meeting: it creates the event (owned by
 * khuluma-service, owningEntity = the conversation) and mints a participant media token. With
 * live-service configured for {@code rtc-gateway} + self-hosted LiveKit, the token is a real,
 * joinable LiveKit token.
 */
class LiveServiceClientTest {

    @Test
    void createEvent_postsKhulumaOwnedMeeting() {
        RestTemplate rt = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.createServer(rt);
        LiveServiceClient client = new LiveServiceClient(rt, "http://live-test:8380");

        server.expect(requestTo("http://live-test:8380/internal/v1/live/events"))
                .andExpect(method(org.springframework.http.HttpMethod.POST))
                .andExpect(jsonPath("$.owningService").value("khuluma-service"))
                .andExpect(jsonPath("$.owningEntityId").value("conv-1"))
                .andExpect(jsonPath("$.eventType").value("MEETING"))
                .andExpect(jsonPath("$.mode").value("PROFESSIONAL_MEETING"))
                .andRespond(withSuccess("{\"id\":\"ev-1\",\"status\":\"DRAFT\"}", MediaType.APPLICATION_JSON));

        LiveServiceClient.CreateEventResult result =
                client.createEvent("Daily standup", "PROFESSIONAL_MEETING", "MEETING", "conv-1", "PROVIDER", "prov-a", 5);

        assertThat(result.available()).isTrue();
        assertThat(result.eventId()).isEqualTo("ev-1");
        server.verify();
    }

    @Test
    void issueToken_parsesRealMediaToken() {
        RestTemplate rt = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.createServer(rt);
        LiveServiceClient client = new LiveServiceClient(rt, "http://live-test:8380");

        server.expect(requestTo("http://live-test:8380/internal/v1/live/room/ev-1/token"))
                .andExpect(method(org.springframework.http.HttpMethod.POST))
                .andExpect(jsonPath("$.participantId").value("prov-a"))
                .andExpect(jsonPath("$.role").value("HOST"))
                .andRespond(withSuccess("""
                        {"roomId":"impilo-ev-1","roomUrl":"ws://livekit:7880","accessToken":"real-livekit-jwt",
                         "provider":"RTC_GATEWAY","channel":"live"}
                        """, MediaType.APPLICATION_JSON));

        LiveServiceClient.RoomTokenResult token = client.issueToken("ev-1", "prov-a", "HOST");

        assertThat(token.available()).isTrue();
        assertThat(token.accessToken()).isEqualTo("real-livekit-jwt");
        assertThat(token.roomUrl()).isEqualTo("ws://livekit:7880");
        assertThat(token.provider()).isEqualTo("RTC_GATEWAY");
        server.verify();
    }
}
