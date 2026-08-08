package zw.gov.mohcc.impilo.experience.controller.mobile;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.experience.client.ChannelsServiceClient;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mobile messaging must never answer on channels-service's behalf.
 *
 * <p>Every one of these endpoints previously swallowed the upstream failure
 * ({@code catch (Exception ignored) {}}) and answered anyway: the reads returned
 * {@code data: []}, and the writes returned {@code 201 Created} carrying a freshly minted
 * {@link UUID} and the caller's own content echoed back. A provider was told their message had
 * been sent when nothing had been persisted anywhere.</p>
 *
 * <p>Each failure case is paired with a success case. A suite that only ever exercises the
 * failing client proves the catch block works, not that the happy path survived the change.</p>
 */
class MobileMessagingControllerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final UUID CONVERSATION = UUID.fromString("11111111-1111-4111-8111-111111111111");

    // ── Reads: an upstream failure is UNAVAILABLE, never EMPTY ──────────────

    @Test
    @DisplayName("listConversations: upstream failure is 502, not an empty mailbox")
    void listConversationsFailsHonestly() {
        ResponseEntity<Map<String, Object>> response =
                controller(new FailingChannelsClient()).listConversations(
                        "tenant-1", "req-1", "corr-1", "actor-1", 0, 20);

        assertEquals(502, response.getStatusCode().value());
        assertEquals("CONVERSATIONS_UNAVAILABLE", errorCode(response));
        // The defect being guarded: a 200 whose data is [] reads as "you have no conversations".
        assertNull(response.getBody().get("data"));
    }

    @Test
    @DisplayName("listConversations: a real payload still passes through")
    void listConversationsSucceeds() {
        ResponseEntity<Map<String, Object>> response =
                controller(new SuccessChannelsClient()).listConversations(
                        "tenant-1", "req-2", "corr-2", "actor-1", 0, 20);

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody().get("data"));
    }

    @Test
    @DisplayName("getMessages: upstream failure is 502, not an empty conversation")
    void getMessagesFailsHonestly() {
        ResponseEntity<Map<String, Object>> response =
                controller(new FailingChannelsClient()).getMessages(
                        CONVERSATION, "tenant-1", "req-3", "corr-3", 0, 50);

        assertEquals(502, response.getStatusCode().value());
        assertEquals("MESSAGES_UNAVAILABLE", errorCode(response));
        assertNull(response.getBody().get("data"));
    }

    @Test
    @DisplayName("getMessages: a real payload still passes through")
    void getMessagesSucceeds() {
        ResponseEntity<Map<String, Object>> response =
                controller(new SuccessChannelsClient()).getMessages(
                        CONVERSATION, "tenant-1", "req-4", "corr-4", 0, 50);

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody().get("data"));
    }

    // ── Writes: no 201 without an upstream confirmation ─────────────────────

    @Test
    @DisplayName("sendMessage: upstream failure must NOT yield a 201 with a fabricated id")
    void sendMessageFailsHonestly() {
        ResponseEntity<Map<String, Object>> response =
                controller(new FailingChannelsClient()).sendMessage(
                        CONVERSATION, "tenant-1", "pod-1", "req-5", "corr-5", "idem-5",
                        new MobileMessagingController.SendMessageRequest("sender-1", "hello", "TEXT"));

        assertEquals(502, response.getStatusCode().value());
        assertEquals("MESSAGE_NOT_SENT", errorCode(response));
        // The caller must be told plainly that nothing was delivered, so it can retry.
        assertTrue(errorMessage(response).contains("has NOT been delivered"));
    }

    @Test
    @DisplayName("sendMessage: upstream returning no body is also a refusal, not a 201")
    void sendMessageNullBodyFailsHonestly() {
        ResponseEntity<Map<String, Object>> response =
                controller(new NullBodyChannelsClient()).sendMessage(
                        CONVERSATION, "tenant-1", "pod-1", "req-6", "corr-6", "idem-6",
                        new MobileMessagingController.SendMessageRequest("sender-1", "hello", "TEXT"));

        assertEquals(502, response.getStatusCode().value());
        assertEquals("MESSAGE_NOT_SENT", errorCode(response));
    }

    @Test
    @DisplayName("sendMessage: a confirmed send still returns 201")
    void sendMessageSucceeds() {
        ResponseEntity<Map<String, Object>> response =
                controller(new SuccessChannelsClient()).sendMessage(
                        CONVERSATION, "tenant-1", "pod-1", "req-7", "corr-7", "idem-7",
                        new MobileMessagingController.SendMessageRequest("sender-1", "hello", "TEXT"));

        assertEquals(201, response.getStatusCode().value());
        assertNotNull(response.getBody().get("data"));
    }

    @Test
    @DisplayName("createConversation: upstream failure must NOT yield a fabricated conversation")
    void createConversationFailsHonestly() {
        ResponseEntity<Map<String, Object>> response =
                controller(new FailingChannelsClient()).createConversation(
                        "tenant-1", "pod-1", "req-8", "corr-8", "idem-8",
                        new MobileMessagingController.CreateConversationRequest(
                                "subject", List.of("p1", "p2"), "DIRECT", null));

        assertEquals(502, response.getStatusCode().value());
        assertEquals("CONVERSATION_NOT_CREATED", errorCode(response));
    }

    @Test
    @DisplayName("createConversation: a confirmed create still returns 201")
    void createConversationSucceeds() {
        ResponseEntity<Map<String, Object>> response =
                controller(new SuccessChannelsClient()).createConversation(
                        "tenant-1", "pod-1", "req-9", "corr-9", "idem-9",
                        new MobileMessagingController.CreateConversationRequest(
                                "subject", List.of("p1", "p2"), "DIRECT", null));

        assertEquals(201, response.getStatusCode().value());
        assertNotNull(response.getBody().get("data"));
    }

    // ── Unbuilt capability: refuse rather than mint a receipt ───────────────

    @Test
    @DisplayName("markRead: 501 — channels-service tracks no read state, so nothing can be marked")
    void markReadIsNotImplemented() {
        // This one never called upstream at all: it built a "ConversationReadReceipt" out of its
        // own arguments and returned 200, so a working client cleared its unread badge against
        // nothing. A healthy upstream must not change that — the capability does not exist.
        ResponseEntity<Map<String, Object>> response =
                controller(new SuccessChannelsClient()).markRead(
                        CONVERSATION, "tenant-1", "pod-1", "req-10", "corr-10", "idem-10",
                        new MobileMessagingController.MarkReadRequest("participant-1"));

        assertEquals(501, response.getStatusCode().value());
        assertEquals("READ_RECEIPTS_NOT_SUPPORTED", errorCode(response));
        assertTrue(errorMessage(response).contains("has NOT been marked read"));
    }

    // ── helpers ────────────────────────────────────────────────────────────

    private static MobileMessagingController controller(ChannelsServiceClient client) {
        return new MobileMessagingController(client);
    }

    private static String errorCode(ResponseEntity<Map<String, Object>> response) {
        assertNotNull(response.getBody());
        return (String) ((Map<?, ?>) response.getBody().get("error")).get("code");
    }

    private static String errorMessage(ResponseEntity<Map<String, Object>> response) {
        assertNotNull(response.getBody());
        return (String) ((Map<?, ?>) response.getBody().get("error")).get("message");
    }

    private static ServiceClientConfig.ServiceEndpoints endpoints() {
        return ServiceClientConfig.testServiceEndpoints();
    }

    /** channels-service unreachable / refusing / 404 — anything that throws. */
    private static final class FailingChannelsClient extends ChannelsServiceClient {
        private FailingChannelsClient() {
            super(new RestTemplate(), endpoints());
        }

        @Override
        public JsonNode listSessions() {
            throw new RuntimeException("channels-service unavailable");
        }

        @Override
        public JsonNode listMessagesForSession(UUID sessionId) {
            throw new RuntimeException("channels-service unavailable");
        }

        @Override
        public JsonNode sendOutboundMessage(Map<String, Object> body) {
            throw new RuntimeException("channels-service unavailable");
        }

        @Override
        public JsonNode createSession(Map<String, Object> body) {
            throw new RuntimeException("channels-service unavailable");
        }
    }

    /** A 200 with no body is not a confirmation either. */
    private static final class NullBodyChannelsClient extends ChannelsServiceClient {
        private NullBodyChannelsClient() {
            super(new RestTemplate(), endpoints());
        }

        @Override
        public JsonNode sendOutboundMessage(Map<String, Object> body) {
            return null;
        }
    }

    private static final class SuccessChannelsClient extends ChannelsServiceClient {
        private SuccessChannelsClient() {
            super(new RestTemplate(), endpoints());
        }

        @Override
        public JsonNode listSessions() {
            return MAPPER.createArrayNode();
        }

        @Override
        public JsonNode listMessagesForSession(UUID sessionId) {
            return MAPPER.createArrayNode();
        }

        @Override
        public JsonNode sendOutboundMessage(Map<String, Object> body) {
            return MAPPER.createObjectNode().put("id", UUID.randomUUID().toString());
        }

        @Override
        public JsonNode createSession(Map<String, Object> body) {
            return MAPPER.createObjectNode().put("id", UUID.randomUUID().toString());
        }
    }
}
