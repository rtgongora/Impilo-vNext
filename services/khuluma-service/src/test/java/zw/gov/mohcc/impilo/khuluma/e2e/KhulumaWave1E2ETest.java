package zw.gov.mohcc.impilo.khuluma.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import zw.gov.mohcc.impilo.khuluma.repository.OutboxEventRepository;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Wave-1 acceptance E2E (brief §E2E) stitched end-to-end over HTTP against khuluma-service:
 * A↔B converse → read receipt → 1:1 call ring → accept → signal → end → policy/membership deny →
 * audit trail. (Media is exercised in the manual A/V QA checklist; meeting invites land in W2.)
 * This is the automated smoke that proves the slice composes; a runnable cross-service curl smoke
 * lives at {@code scripts/e2e/khuluma-wave1-smoke.sh}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class KhulumaWave1E2ETest {

    @Autowired private MockMvc mockMvc;
    @Autowired private OutboxEventRepository outbox;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String TENANT = UUID.randomUUID().toString();
    private static final String A = "provider-a";
    private static final String B = "provider-b";
    private static final String X = "provider-x"; // non-participant

    private MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder b, String actor) {
        return b.header("X-Tenant-ID", TENANT)
                .header("X-Pod-ID", "national")
                .header("X-Request-ID", UUID.randomUUID().toString())
                .header("X-Correlation-ID", UUID.randomUUID().toString())
                .header("X-Actor-ID", actor)
                .header("X-Actor-Type", "PROVIDER")
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON);
    }

    @Test
    void wave1_acceptance_journey() throws Exception {
        // 1) A opens a patient-linked conversation with B.
        String createBody = """
                {"type":"DIRECT","title":"Ward 4 round","participants":[
                   {"actorId":"%s","actorType":"PROVIDER","displayName":"Dr B","role":"MEMBER"}],
                 "links":[{"objectType":"PATIENT","objectId":"cpid-42","linkRole":"SUBJECT"}]}
                """.formatted(B);
        String convJson = mockMvc.perform(as(post("/internal/v1/khuluma/conversations"), A).content(createBody))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String convId = MAPPER.readTree(convJson).get("conversationId").asText();

        // 2) A sends a message.
        mockMvc.perform(as(post("/internal/v1/khuluma/conversations/" + convId + "/messages"), A)
                        .content("{\"body\":\"Please review bed 4\",\"clientMessageId\":\"m-1\"}"))
                .andExpect(status().isCreated());

        // 3) B sees 1 unread on that conversation.
        String inboxJson = mockMvc.perform(as(get("/internal/v1/khuluma/conversations"), B))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode row = findConversation(MAPPER.readTree(inboxJson), convId);
        assertThat(row).isNotNull();
        assertThat(row.get("unreadCount").asLong()).isEqualTo(1);

        // 4) B reads → read receipt; unread clears.
        String msgsJson = mockMvc.perform(as(get("/internal/v1/khuluma/conversations/" + convId + "/messages"), B))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String messageId = MAPPER.readTree(msgsJson).get(0).get("messageId").asText();
        mockMvc.perform(as(post("/internal/v1/khuluma/conversations/" + convId + "/messages/read"), B)
                        .content("{\"messageId\":\"%s\"}".formatted(messageId)))
                .andExpect(status().isNoContent());
        mockMvc.perform(as(get("/internal/v1/khuluma/unread-count"), B))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount").value(0));

        // 5) Policy/membership deny: a non-participant cannot post.
        mockMvc.perform(as(post("/internal/v1/khuluma/conversations/" + convId + "/messages"), X)
                        .content("{\"body\":\"intruding\"}"))
                .andExpect(status().isForbidden());

        // 6) A starts a 1:1 call → ringing.
        String startBody = """
                {"conversationId":"%s","callType":"AUDIO","displayName":"Dr A",
                 "callees":[{"actorId":"%s","actorType":"PROVIDER","displayName":"Dr B"}]}
                """.formatted(convId, B);
        String callJson = mockMvc.perform(as(post("/internal/v1/khuluma/calls"), A).content(startBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("RINGING"))
                .andReturn().getResponse().getContentAsString();
        String callId = MAPPER.readTree(callJson).get("callId").asText();

        // 7) B accepts → active.
        mockMvc.perform(as(post("/internal/v1/khuluma/calls/" + callId + "/accept"), B).content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        // 8) A signals (e.g. typing / data-channel passthrough).
        mockMvc.perform(as(post("/internal/v1/khuluma/calls/" + callId + "/signals"), A)
                        .content("{\"signalType\":\"typing\",\"detail\":{\"on\":true}}"))
                .andExpect(status().isAccepted());

        // 9) A ends the call.
        mockMvc.perform(as(post("/internal/v1/khuluma/calls/" + callId + "/end"), A)
                        .content("{\"reason\":\"HANGUP\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ENDED"));

        // 10) Audit trail: every governed action emitted a domain/audit outbox event.
        List<String> events = outbox.findAll().stream().map(e -> e.getEventType()).toList();
        assertThat(events).contains(
                "impilo.khuluma.conversation.created.v1",
                "impilo.khuluma.conversation.linked.v1",
                "impilo.khuluma.message.sent.v1",
                "impilo.khuluma.message.read.v1",
                "impilo.khuluma.call.started.v1",
                "impilo.khuluma.call.accepted.v1",
                "impilo.khuluma.call.ended.v1");
    }

    private static JsonNode findConversation(JsonNode inbox, String convId) {
        for (JsonNode n : inbox) {
            if (n.get("conversationId").asText().equals(convId)) {
                return n;
            }
        }
        return null;
    }
}
