package zw.gov.mohcc.impilo.experience.khuluma;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;
import zw.gov.mohcc.impilo.experience.client.NotificationServiceClient;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * BFF Comms Hub controller: policy pre-checks gate sensitive actions, downstream status semantics
 * are relayed faithfully (e.g. membership 403), and notifications delegate to notification-service.
 */
class KhulumaBffControllerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final KhulumaAccessPolicyService policy = new KhulumaAccessPolicyService();
    private final StubKhulumaClient khuluma = new StubKhulumaClient();
    private final StubNotificationClient notifications = new StubNotificationClient();
    private final KhulumaBffController controller =
            new KhulumaBffController(khuluma, notifications, policy, MAPPER);

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateAs(String role) {
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken("sub-1", "n/a", List.of(new SimpleGrantedAuthority(role))));
    }

    @Test
    void createConversation_authenticated_relaysCreated() throws Exception {
        authenticateAs("ROLE_PATIENT");
        khuluma.next = new KhulumaServiceClient.Result(201, MAPPER.readTree("{\"conversationId\":\"c-1\"}"));

        ResponseEntity<JsonNode> response = controller.createConversation(MAPPER.readTree("{\"type\":\"DIRECT\"}"));

        assertThat(response.getStatusCode().value()).isEqualTo(201);
        assertThat(response.getBody().get("conversationId").asText()).isEqualTo("c-1");
    }

    @Test
    void createConversation_unauthenticated_is401() {
        assertThatThrownBy(() -> controller.createConversation(MAPPER.createObjectNode()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("401");
    }

    @Test
    void sendMessage_relaysDownstreamMembershipDenial() throws Exception {
        authenticateAs("ROLE_PATIENT");
        khuluma.next = new KhulumaServiceClient.Result(403,
                MAPPER.readTree("{\"error\":{\"code\":\"forbidden\"}}"));

        ResponseEntity<JsonNode> response =
                controller.sendMessage("c-1", MAPPER.readTree("{\"body\":\"hi\"}"));

        assertThat(response.getStatusCode().value()).isEqualTo(403);
        assertThat(response.getBody().get("error").get("code").asText()).isEqualTo("forbidden");
    }

    @Test
    void linkPatient_requiresClinicalRole() throws Exception {
        authenticateAs("ROLE_CLINICIAN");
        khuluma.next = new KhulumaServiceClient.Result(200, MAPPER.readTree("{\"conversationId\":\"c-1\"}"));
        ResponseEntity<JsonNode> ok = controller.linkPatient("c-1", MAPPER.readTree("{\"objectType\":\"PATIENT\"}"));
        assertThat(ok.getStatusCode().value()).isEqualTo(200);

        authenticateAs("ROLE_PATIENT");
        assertThatThrownBy(() -> controller.linkPatient("c-1", MAPPER.createObjectNode()))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void createMeeting_authenticated_relaysMediaToken() throws Exception {
        authenticateAs("ROLE_CLINICIAN");
        khuluma.next = new KhulumaServiceClient.Result(201,
                MAPPER.readTree("{\"conversationId\":\"c-9\",\"eventId\":\"ev-9\",\"mediaAvailable\":true}"));

        ResponseEntity<JsonNode> response =
                controller.createMeeting(MAPPER.readTree("{\"title\":\"Huddle\",\"participants\":[]}"));

        assertThat(response.getStatusCode().value()).isEqualTo(201);
        assertThat(response.getBody().get("eventId").asText()).isEqualTo("ev-9");
        assertThat(response.getBody().get("mediaAvailable").asBoolean()).isTrue();
    }

    @Test
    void joinMeeting_relaysLobbyWaitingOutcome() throws Exception {
        authenticateAs("ROLE_PATIENT");
        khuluma.next = new KhulumaServiceClient.Result(200, MAPPER.readTree(
                "{\"conversationId\":\"c-9\",\"status\":\"WAITING\",\"role\":\"PARTICIPANT\",\"accessToken\":null}"));

        ResponseEntity<JsonNode> response = controller.joinMeeting("c-9");

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody().get("status").asText()).isEqualTo("WAITING");
    }

    @Test
    void meetingLobbyModeration_requiresAuthenticatedActor_andRelays() throws Exception {
        // Unauthenticated admit is refused before any downstream call.
        assertThatThrownBy(() -> controller.admitToMeeting("c-9", MAPPER.createObjectNode()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("401");

        authenticateAs("ROLE_CLINICIAN");
        khuluma.next = new KhulumaServiceClient.Result(200,
                MAPPER.readTree("{\"actorId\":\"provider-b\",\"status\":\"ADMITTED\"}"));
        ResponseEntity<JsonNode> admitted =
                controller.admitToMeeting("c-9", MAPPER.readTree("{\"actorId\":\"provider-b\"}"));
        assertThat(admitted.getBody().get("status").asText()).isEqualTo("ADMITTED");

        // Downstream host-only denial (403) is relayed faithfully.
        khuluma.next = new KhulumaServiceClient.Result(403,
                MAPPER.readTree("{\"error\":{\"code\":\"forbidden\"}}"));
        assertThat(controller.denyFromMeeting("c-9", MAPPER.createObjectNode()).getStatusCode().value())
                .isEqualTo(403);
    }

    @Test
    void actionItems_inviteLinks_andDetail_relay() throws Exception {
        authenticateAs("ROLE_CLINICIAN");
        khuluma.next = new KhulumaServiceClient.Result(201,
                MAPPER.readTree("{\"actionItemId\":\"ai-1\",\"status\":\"OPEN\"}"));
        assertThat(controller.createActionItem("c-9", MAPPER.readTree("{\"description\":\"do x\"}"))
                .getStatusCode().value()).isEqualTo(201);

        khuluma.next = new KhulumaServiceClient.Result(200,
                MAPPER.readTree("{\"actionItemId\":\"ai-1\",\"status\":\"DONE\"}"));
        assertThat(controller.updateActionItem("c-9", "ai-1", MAPPER.readTree("{\"status\":\"DONE\"}"))
                .getBody().get("status").asText()).isEqualTo("DONE");

        khuluma.next = new KhulumaServiceClient.Result(201,
                MAPPER.readTree("{\"token\":\"abc.def\",\"role\":\"MEMBER\"}"));
        assertThat(controller.mintInvite("c-9", null).getBody().get("token").asText()).isEqualTo("abc.def");

        khuluma.next = new KhulumaServiceClient.Result(200,
                MAPPER.readTree("{\"conversationId\":\"c-9\",\"eventId\":\"ev-9\",\"scheduledAt\":\"2026-07-10T09:00:00Z\"}"));
        assertThat(controller.meetingDetail("c-9").getBody().get("scheduledAt").asText())
                .isEqualTo("2026-07-10T09:00:00Z");
    }

    @Test
    void notifications_delegateToNotificationService() {
        ResponseEntity<JsonNode> response = controller.notifications("user-1");
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody().isArray()).isTrue();
        assertThat(response.getBody().get(0).get("id").asText()).isEqualTo("notif-1");
    }

    @Test
    void unreadCount_compositesMessagesAndNotifications() throws Exception {
        khuluma.next = new KhulumaServiceClient.Result(200, MAPPER.readTree("{\"unreadCount\":3}"));
        ResponseEntity<JsonNode> response = controller.unreadCount("user-1");
        assertThat(response.getBody().get("messages").get("unreadCount").asInt()).isEqualTo(3);
        assertThat(response.getBody().has("notifications")).isTrue();
    }

    // ── stubs ─────────────────────────────────────────────────────────────────

    private static final class StubKhulumaClient extends KhulumaServiceClient {
        private Result next = new Result(200, MAPPER.createObjectNode());

        private StubKhulumaClient() {
            super(new RestTemplate(), ServiceClientConfig.testServiceEndpoints(), MAPPER);
        }

        @Override public Result get(String relativePath, Map<String, String> query) { return next; }
        @Override public Result post(String relativePath, Object body) { return next; }
        @Override public Result put(String relativePath, Object body) { return next; }
        @Override public Result delete(String relativePath) { return next; }
    }

    private static final class StubNotificationClient extends NotificationServiceClient {
        private StubNotificationClient() {
            super(new RestTemplate(), ServiceClientConfig.testServiceEndpoints());
        }

        @Override
        public JsonNode listNotifications(String recipientId) {
            var arr = MAPPER.createArrayNode();
            arr.add(MAPPER.createObjectNode().put("id", "notif-1").put("read", false));
            return arr;
        }

        @Override
        public JsonNode unreadCount(String recipientId) {
            return MAPPER.createObjectNode().put("unread", 2);
        }
    }
}
