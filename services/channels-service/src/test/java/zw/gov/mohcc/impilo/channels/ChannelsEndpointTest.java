package zw.gov.mohcc.impilo.channels;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import zw.gov.mohcc.impilo.channels.domain.OutboxEventEntity;
import zw.gov.mohcc.impilo.channels.repository.OutboxEventRepository;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ChannelsEndpointTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OutboxEventRepository outboxRepository;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String TENANT = "moh-zw";
    private static final String POD = "national";
    private static final String REQ_ID = "req-test-1";
    private static final String CORR_ID = "corr-test-1";

    // ── A) Missing Header → 400 ─────────────────────────────────

    @Nested
    @DisplayName("A. Missing header returns 400")
    class MissingHeaderTests {

        @Test
        @DisplayName("GET /internal/v1/channels/sessions without X-Tenant-ID → 400")
        void missingTenantOnGet() throws Exception {
            MvcResult result = mockMvc.perform(get("/internal/v1/channels/sessions")
                            .header("X-Pod-ID", POD)
                            .header("X-Request-ID", REQ_ID)
                            .header("X-Correlation-ID", CORR_ID))
                    .andExpect(status().isBadRequest())
                    .andReturn();

            assertErrorCode(result, "MISSING_REQUIRED_HEADER");
        }

        @Test
        @DisplayName("POST /internal/v1/channels/sessions without X-Pod-ID → 400")
        void missingPodOnPost() throws Exception {
            MvcResult result = mockMvc.perform(post("/internal/v1/channels/sessions")
                            .header("X-Tenant-ID", TENANT)
                            .header("X-Request-ID", REQ_ID)
                            .header("X-Correlation-ID", CORR_ID)
                            .header("Idempotency-Key", "idem-" + System.nanoTime())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"channelType\":\"USSD\"}"))
                    .andExpect(status().isBadRequest())
                    .andReturn();

            assertErrorCode(result, "MISSING_REQUIRED_HEADER");
        }
    }

    // ── B) Missing Idempotency-Key → 400 ────────────────────────

    @Nested
    @DisplayName("B. Missing Idempotency-Key returns 400")
    class MissingIdempotencyKeyTests {

        @Test
        @DisplayName("POST /internal/v1/channels/sessions without Idempotency-Key → 400")
        void missingIdempotencyOnSession() throws Exception {
            MvcResult result = mockMvc.perform(post("/internal/v1/channels/sessions")
                            .header("X-Tenant-ID", TENANT)
                            .header("X-Pod-ID", POD)
                            .header("X-Request-ID", REQ_ID)
                            .header("X-Correlation-ID", CORR_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"channelType\":\"USSD\"}"))
                    .andExpect(status().isBadRequest())
                    .andReturn();

            assertErrorCode(result, "IDEMPOTENCY_KEY_REQUIRED");
        }

        @Test
        @DisplayName("POST /internal/v1/channels/messages without Idempotency-Key → 400")
        void missingIdempotencyOnMessage() throws Exception {
            MvcResult result = mockMvc.perform(post("/internal/v1/channels/messages")
                            .header("X-Tenant-ID", TENANT)
                            .header("X-Pod-ID", POD)
                            .header("X-Request-ID", REQ_ID)
                            .header("X-Correlation-ID", CORR_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"sessionId\":\"00000000-0000-0000-0000-000000000001\",\"payloadJson\":\"{}\"}"))
                    .andExpect(status().isBadRequest())
                    .andReturn();

            assertErrorCode(result, "IDEMPOTENCY_KEY_REQUIRED");
        }

        @Test
        @DisplayName("POST /internal/v1/channels/assisted-interactions without Idempotency-Key → 400")
        void missingIdempotencyOnAssisted() throws Exception {
            MvcResult result = mockMvc.perform(post("/internal/v1/channels/assisted-interactions")
                            .header("X-Tenant-ID", TENANT)
                            .header("X-Pod-ID", POD)
                            .header("X-Request-ID", REQ_ID)
                            .header("X-Correlation-ID", CORR_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"sessionId\":\"00000000-0000-0000-0000-000000000001\",\"agentId\":\"agent-1\"}"))
                    .andExpect(status().isBadRequest())
                    .andReturn();

            assertErrorCode(result, "IDEMPOTENCY_KEY_REQUIRED");
        }
    }

    // ── C) Idempotency replay semantics ─────────────────────────

    @Nested
    @DisplayName("C. Idempotency replay semantics")
    class IdempotencyReplayTests {

        @Test
        @DisplayName("Same key + same body replays original response for session creation")
        void sessionReplay() throws Exception {
            String key = "idem-session-replay-" + System.nanoTime();
            String body = "{\"channelType\":\"WHATSAPP\",\"subjectRef\":\"CPID-001\"}";

            MvcResult first = mockMvc.perform(post("/internal/v1/channels/sessions")
                            .header("X-Tenant-ID", TENANT)
                            .header("X-Pod-ID", POD)
                            .header("X-Request-ID", REQ_ID)
                            .header("X-Correlation-ID", CORR_ID)
                            .header("Idempotency-Key", key)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isCreated())
                    .andReturn();

            MvcResult second = mockMvc.perform(post("/internal/v1/channels/sessions")
                            .header("X-Tenant-ID", TENANT)
                            .header("X-Pod-ID", POD)
                            .header("X-Request-ID", "req-2")
                            .header("X-Correlation-ID", CORR_ID)
                            .header("Idempotency-Key", key)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andReturn();

            assertThat(second.getResponse().getStatus())
                    .isEqualTo(first.getResponse().getStatus());
            assertThat(second.getResponse().getContentAsString())
                    .isEqualTo(first.getResponse().getContentAsString());
        }

        @Test
        @DisplayName("Same key + different body returns 409 IDENTITY_CONFLICT")
        void sessionConflict() throws Exception {
            String key = "idem-session-conflict-" + System.nanoTime();

            mockMvc.perform(post("/internal/v1/channels/sessions")
                            .header("X-Tenant-ID", TENANT)
                            .header("X-Pod-ID", POD)
                            .header("X-Request-ID", REQ_ID)
                            .header("X-Correlation-ID", CORR_ID)
                            .header("Idempotency-Key", key)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"channelType\":\"SMS\"}"))
                    .andExpect(status().isCreated());

            MvcResult conflict = mockMvc.perform(post("/internal/v1/channels/sessions")
                            .header("X-Tenant-ID", TENANT)
                            .header("X-Pod-ID", POD)
                            .header("X-Request-ID", "req-2")
                            .header("X-Correlation-ID", CORR_ID)
                            .header("Idempotency-Key", key)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"channelType\":\"WHATSAPP\"}"))
                    .andExpect(status().isConflict())
                    .andReturn();

            assertErrorCode(conflict, "IDENTITY_CONFLICT");
        }
    }

    // ── D) Outbox row verification ──────────────────────────────

    @Nested
    @DisplayName("D. Outbox row verification")
    class OutboxVerificationTests {

        @Test
        @DisplayName("Session creation writes session.started outbox event with EventEnvelope fields")
        void sessionCreationOutbox() throws Exception {
            long outboxCountBefore = outboxRepository.count();

            String key = "idem-outbox-session-" + System.nanoTime();
            MvcResult result = mockMvc.perform(post("/internal/v1/channels/sessions")
                            .header("X-Tenant-ID", TENANT)
                            .header("X-Pod-ID", POD)
                            .header("X-Request-ID", REQ_ID)
                            .header("X-Correlation-ID", CORR_ID)
                            .header("Idempotency-Key", key)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"channelType\":\"USSD\",\"subjectRef\":\"CPID-100\"}"))
                    .andExpect(status().isCreated())
                    .andReturn();

            JsonNode session = MAPPER.readTree(result.getResponse().getContentAsString());
            String sessionId = session.get("id").asText();

            List<OutboxEventEntity> events = outboxRepository.findByAggregateId(sessionId);
            assertThat(events).hasSize(1);

            OutboxEventEntity event = events.get(0);
            assertThat(event.getEventType()).isEqualTo("impilo.channels.session.started.v1");
            assertThat(event.getSchemaVersion()).isEqualTo(1);
            assertThat(event.getAggregateType()).isEqualTo("ChannelSession");
            assertThat(event.getTenantId()).isEqualTo(TENANT);
            assertThat(event.getPodId()).isEqualTo(POD);
            assertThat(event.getCorrelationId()).isEqualTo(CORR_ID);
            assertThat(event.getCausationId()).isEqualTo(REQ_ID);
            assertThat(event.getSubjectType()).isEqualTo("ChannelSession");
            assertThat(event.getSubjectId()).isEqualTo(sessionId);
            assertThat(event.getPublishedAt()).isNull();
            assertThat(event.getPayloadJson()).contains("impilo.channels.session.started.v1");
        }

        @Test
        @DisplayName("Outbound message writes 2 outbox events (message.outbound + delivery.updated)")
        void outboundMessageOutbox() throws Exception {
            String sessionId = createSession("WHATSAPP");

            String key = "idem-outbox-msg-" + System.nanoTime();
            mockMvc.perform(post("/internal/v1/channels/messages")
                            .header("X-Tenant-ID", TENANT)
                            .header("X-Pod-ID", POD)
                            .header("X-Request-ID", REQ_ID)
                            .header("X-Correlation-ID", CORR_ID)
                            .header("Idempotency-Key", key)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"sessionId\":\"" + sessionId + "\",\"contentType\":\"TEXT\",\"payloadJson\":\"{\\\"text\\\":\\\"hello\\\"}\"}"))
                    .andExpect(status().isCreated());

            List<OutboxEventEntity> allEvents = outboxRepository.findAll();
            List<OutboxEventEntity> messageEvents = allEvents.stream()
                    .filter(e -> e.getIdempotencyKey() != null && e.getIdempotencyKey().startsWith(key))
                    .toList();

            assertThat(messageEvents).hasSize(2);
            assertThat(messageEvents.stream().map(OutboxEventEntity::getEventType).toList())
                    .containsExactlyInAnyOrder(
                            "impilo.channels.message.outbound.v1",
                            "impilo.channels.delivery.updated.v1"
                    );
        }

        @Test
        @DisplayName("Escalation writes 2 outbox events (escalation.requested + escalation.completed)")
        void escalationOutbox() throws Exception {
            String sessionId = createSession("SMS");

            String key = "idem-outbox-esc-" + System.nanoTime();
            mockMvc.perform(post("/internal/v1/channels/sessions/" + sessionId + "/escalate")
                            .header("X-Tenant-ID", TENANT)
                            .header("X-Pod-ID", POD)
                            .header("X-Request-ID", REQ_ID)
                            .header("X-Correlation-ID", CORR_ID)
                            .header("Idempotency-Key", key)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"agentId\":\"agent-007\",\"reason\":\"patient request\"}"))
                    .andExpect(status().isOk());

            List<OutboxEventEntity> allEvents = outboxRepository.findAll();
            List<OutboxEventEntity> escEvents = allEvents.stream()
                    .filter(e -> e.getIdempotencyKey() != null && e.getIdempotencyKey().startsWith(key))
                    .toList();

            assertThat(escEvents).hasSize(2);
            assertThat(escEvents.stream().map(OutboxEventEntity::getEventType).toList())
                    .containsExactlyInAnyOrder(
                            "impilo.channels.escalation.requested.v1",
                            "impilo.channels.escalation.completed.v1"
                    );
        }

        @Test
        @DisplayName("Escalation of non-ACTIVE session returns error")
        void escalationRequiresActiveSession() throws Exception {
            String sessionId = createSession("SMS");

            // Escalate once (succeeds)
            mockMvc.perform(post("/internal/v1/channels/sessions/" + sessionId + "/escalate")
                            .header("X-Tenant-ID", TENANT)
                            .header("X-Pod-ID", POD)
                            .header("X-Request-ID", REQ_ID)
                            .header("X-Correlation-ID", CORR_ID)
                            .header("Idempotency-Key", "idem-esc1-" + System.nanoTime())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"agentId\":\"agent-1\",\"reason\":\"test\"}"))
                    .andExpect(status().isOk());

            // Escalate again (should fail — already ESCALATED)
            mockMvc.perform(post("/internal/v1/channels/sessions/" + sessionId + "/escalate")
                            .header("X-Tenant-ID", TENANT)
                            .header("X-Pod-ID", POD)
                            .header("X-Request-ID", "req-2")
                            .header("X-Correlation-ID", CORR_ID)
                            .header("Idempotency-Key", "idem-esc2-" + System.nanoTime())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"agentId\":\"agent-2\",\"reason\":\"second attempt\"}"))
                    .andExpect(status().isConflict());
        }

        @Test
        @DisplayName("Assisted interaction writes 1 outbox event")
        void assistedInteractionOutbox() throws Exception {
            String sessionId = createSession("WEB");

            String key = "idem-outbox-assist-" + System.nanoTime();
            mockMvc.perform(post("/internal/v1/channels/assisted-interactions")
                            .header("X-Tenant-ID", TENANT)
                            .header("X-Pod-ID", POD)
                            .header("X-Request-ID", REQ_ID)
                            .header("X-Correlation-ID", CORR_ID)
                            .header("Idempotency-Key", key)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"sessionId\":\"" + sessionId + "\",\"agentId\":\"agent-99\",\"clientId\":\"CPID-42\"}"))
                    .andExpect(status().isCreated());

            List<OutboxEventEntity> allEvents = outboxRepository.findAll();
            List<OutboxEventEntity> assistEvents = allEvents.stream()
                    .filter(e -> key.equals(e.getIdempotencyKey()))
                    .toList();

            assertThat(assistEvents).hasSize(1);
            assertThat(assistEvents.get(0).getEventType())
                    .isEqualTo("impilo.channels.assisted.interaction.recorded.v1");
        }

        @Test
        @DisplayName("Inbound message via external endpoint writes 2 outbox events")
        void inboundMessageOutbox() throws Exception {
            String sessionId = createSession("USSD");

            String key = "idem-outbox-inbound-" + System.nanoTime();
            mockMvc.perform(post("/external/v1/channels/inbound")
                            .header("X-Tenant-ID", TENANT)
                            .header("X-Pod-ID", POD)
                            .header("X-Request-ID", REQ_ID)
                            .header("X-Correlation-ID", CORR_ID)
                            .header("Idempotency-Key", key)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"sessionId\":\"" + sessionId + "\",\"channelType\":\"USSD\",\"payloadJson\":\"{\\\"input\\\":\\\"1\\\"}\",\"senderId\":\"263771234567\"}"))
                    .andExpect(status().isCreated());

            List<OutboxEventEntity> allEvents = outboxRepository.findAll();
            List<OutboxEventEntity> inboundEvents = allEvents.stream()
                    .filter(e -> e.getIdempotencyKey() != null && e.getIdempotencyKey().startsWith(key))
                    .toList();

            assertThat(inboundEvents).hasSize(2);
            assertThat(inboundEvents.stream().map(OutboxEventEntity::getEventType).toList())
                    .containsExactlyInAnyOrder(
                            "impilo.channels.message.inbound.v1",
                            "impilo.channels.delivery.updated.v1"
                    );
        }
    }

    // ── Helpers ──────────────────────────────────────────────────

    private String createSession(String channelType) throws Exception {
        String key = "idem-helper-" + System.nanoTime();
        MvcResult result = mockMvc.perform(post("/internal/v1/channels/sessions")
                        .header("X-Tenant-ID", TENANT)
                        .header("X-Pod-ID", POD)
                        .header("X-Request-ID", REQ_ID)
                        .header("X-Correlation-ID", CORR_ID)
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"channelType\":\"" + channelType + "\"}"))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode node = MAPPER.readTree(result.getResponse().getContentAsString());
        return node.get("id").asText();
    }

    private void assertErrorCode(MvcResult result, String expectedCode) throws Exception {
        String body = result.getResponse().getContentAsString();
        assertThat(body).isNotBlank();
        JsonNode root = MAPPER.readTree(body);
        assertThat(root.has("error")).as("Response should have 'error' field: " + body).isTrue();
        assertThat(root.get("error").get("code").asText()).isEqualTo(expectedCode);
    }
}
