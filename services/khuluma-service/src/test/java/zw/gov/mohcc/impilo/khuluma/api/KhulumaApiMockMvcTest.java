package zw.gov.mohcc.impilo.khuluma.api;

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

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Internal API contract for the Khuluma Comms Hub — the surface experience-bff consumes (W1.5).
 * Drives the W1 acceptance shape end-to-end over HTTP: A and B converse, unread + read receipts,
 * presence, and a 1:1 call ring/accept/end. Also asserts the v1.1 mandatory-header gate.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class KhulumaApiMockMvcTest {

    @Autowired private MockMvc mockMvc;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String TENANT = UUID.randomUUID().toString();
    private static final String ACTOR_A = "provider-A";
    private static final String ACTOR_B = "provider-B";

    private MockHttpServletRequestBuilder asActor(MockHttpServletRequestBuilder builder, String actor) {
        return builder
                .header("X-Tenant-ID", TENANT)
                .header("X-Pod-ID", "national")
                .header("X-Request-ID", UUID.randomUUID().toString())
                .header("X-Correlation-ID", UUID.randomUUID().toString())
                .header("X-Actor-ID", actor)
                .header("X-Actor-Type", "PROVIDER")
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON);
    }

    @Test
    void missingMandatoryHeaders_yield400() throws Exception {
        mockMvc.perform(get("/internal/v1/khuluma/conversations"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void fullConversationAndCallFlow() throws Exception {
        // A creates a direct conversation with B.
        String createBody = """
                {"type":"DIRECT","title":"Ward round","participants":[
                  {"actorId":"%s","actorType":"PROVIDER","displayName":"Dr B","role":"MEMBER"}],
                 "links":[{"objectType":"PATIENT","objectId":"cpid-9","linkRole":"SUBJECT"}]}
                """.formatted(ACTOR_B);
        String createJson = mockMvc.perform(asActor(post("/internal/v1/khuluma/conversations"), ACTOR_A)
                        .content(createBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.conversationId").exists())
                .andReturn().getResponse().getContentAsString();
        String convId = MAPPER.readTree(createJson).get("conversationId").asText();

        // A sends a message.
        mockMvc.perform(asActor(post("/internal/v1/khuluma/conversations/" + convId + "/messages"), ACTOR_A)
                        .content("{\"body\":\"Morning, please review bed 4\",\"contentType\":\"TEXT\",\"clientMessageId\":\"m1\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.messageId").exists());

        // B's inbox shows the conversation with 1 unread.
        String inboxJson = mockMvc.perform(asActor(get("/internal/v1/khuluma/conversations"), ACTOR_B))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode inbox = MAPPER.readTree(inboxJson);
        JsonNode row = null;
        for (JsonNode n : inbox) {
            if (n.get("conversationId").asText().equals(convId)) { row = n; break; }
        }
        assertThat(row).isNotNull();
        assertThat(row.get("unreadCount").asLong()).isEqualTo(1);

        // B reads the messages, grabs the message id, marks read → unread clears.
        String msgsJson = mockMvc.perform(asActor(get("/internal/v1/khuluma/conversations/" + convId + "/messages"), ACTOR_B))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String messageId = MAPPER.readTree(msgsJson).get(0).get("messageId").asText();
        mockMvc.perform(asActor(post("/internal/v1/khuluma/conversations/" + convId + "/messages/read"), ACTOR_B)
                        .content("{\"messageId\":\"%s\"}".formatted(messageId)))
                .andExpect(status().isNoContent());
        mockMvc.perform(asActor(get("/internal/v1/khuluma/unread-count"), ACTOR_B))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount").value(0));

        // Presence update + read-back.
        mockMvc.perform(asActor(put("/internal/v1/khuluma/presence"), ACTOR_B)
                        .content("{\"status\":\"ONLINE\",\"statusMessage\":\"On call\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ONLINE"));
        mockMvc.perform(asActor(get("/internal/v1/khuluma/presence/" + ACTOR_B), ACTOR_A))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ONLINE"));

        // A starts a 1:1 call to B; B accepts; A ends.
        String startBody = """
                {"conversationId":"%s","callType":"AUDIO","displayName":"Dr A",
                 "callees":[{"actorId":"%s","actorType":"PROVIDER","displayName":"Dr B"}]}
                """.formatted(convId, ACTOR_B);
        String callJson = mockMvc.perform(asActor(post("/internal/v1/khuluma/calls"), ACTOR_A)
                        .content(startBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("RINGING"))
                .andReturn().getResponse().getContentAsString();
        String callId = MAPPER.readTree(callJson).get("callId").asText();

        mockMvc.perform(asActor(post("/internal/v1/khuluma/calls/" + callId + "/accept"), ACTOR_B)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        mockMvc.perform(asActor(post("/internal/v1/khuluma/calls/" + callId + "/end"), ACTOR_A)
                        .content("{\"reason\":\"HANGUP\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ENDED"));
    }
}
