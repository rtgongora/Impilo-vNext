package zw.gov.mohcc.impilo.support;

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
import zw.gov.mohcc.impilo.support.repository.OutboxEventRepository;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class SupportExtendedApiTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private OutboxEventRepository outboxRepository;

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String TENANT_ID = UUID.randomUUID().toString();

    // ── Helper: create a ticket and return its ticketId ──

    private String createTicket() throws Exception {
        return createTicket("Test Ticket", "Test description", "user-1", null, null);
    }

    private String createTicket(String title, String description, String reporterRef,
                                 String category, String priority) throws Exception {
        StringBuilder json = new StringBuilder();
        json.append("{\"title\":\"").append(title).append("\",");
        json.append("\"description\":\"").append(description).append("\",");
        json.append("\"reporterRef\":\"").append(reporterRef).append("\"");
        if (category != null) json.append(",\"category\":\"").append(category).append("\"");
        if (priority != null) json.append(",\"priority\":\"").append(priority).append("\"");
        json.append("}");

        MvcResult result = mockMvc.perform(post("/internal/v1/support/tickets")
                        .header("X-Tenant-ID", TENANT_ID)
                        .header("X-Pod-ID", "national")
                        .header("X-Request-ID", UUID.randomUUID().toString())
                        .header("X-Correlation-ID", UUID.randomUUID().toString())
                        .header("Idempotency-Key", "setup-ticket-" + System.nanoTime())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.toString()))
                .andExpect(status().isCreated())
                .andReturn();

        return MAPPER.readTree(result.getResponse().getContentAsString()).get("ticketId").asText();
    }

    private String createArticle() throws Exception {
        MvcResult result = mockMvc.perform(post("/internal/v1/support/articles")
                        .header("X-Tenant-ID", TENANT_ID)
                        .header("X-Pod-ID", "national")
                        .header("X-Request-ID", UUID.randomUUID().toString())
                        .header("X-Correlation-ID", UUID.randomUUID().toString())
                        .header("Idempotency-Key", "setup-article-" + System.nanoTime())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"KB Article\",\"body\":\"Article body content\",\"authorRef\":\"author-1\",\"category\":\"FAQ\"}"))
                .andExpect(status().isCreated())
                .andReturn();

        return MAPPER.readTree(result.getResponse().getContentAsString()).get("articleId").asText();
    }

    // ── 1) Comment Flow ──────────────────────────────────────────────────

    @Nested
    @DisplayName("1) Comment Flow")
    class CommentFlow {

        @Test
        @DisplayName("POST /tickets/{id}/comments returns 201 with comment fields")
        void addComment() throws Exception {
            String ticketId = createTicket();

            mockMvc.perform(post("/internal/v1/support/tickets/" + ticketId + "/comments")
                            .header("X-Tenant-ID", TENANT_ID)
                            .header("X-Pod-ID", "national")
                            .header("X-Request-ID", UUID.randomUUID().toString())
                            .header("X-Correlation-ID", UUID.randomUUID().toString())
                            .header("Idempotency-Key", "comment-" + System.nanoTime())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"authorRef\":\"nurse-001\",\"body\":\"This is a comment\"}"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.commentId").exists())
                    .andExpect(jsonPath("$.ticketId").value(ticketId))
                    .andExpect(jsonPath("$.authorRef").value("nurse-001"))
                    .andExpect(jsonPath("$.body").value("This is a comment"))
                    .andExpect(jsonPath("$.createdAt").exists());
        }

        @Test
        @DisplayName("GET /tickets/{id}/comments returns paged list")
        void listComments() throws Exception {
            String ticketId = createTicket();

            // Add two comments
            for (int i = 1; i <= 2; i++) {
                mockMvc.perform(post("/internal/v1/support/tickets/" + ticketId + "/comments")
                                .header("X-Tenant-ID", TENANT_ID)
                                .header("X-Pod-ID", "national")
                                .header("X-Request-ID", UUID.randomUUID().toString())
                                .header("X-Correlation-ID", UUID.randomUUID().toString())
                                .header("Idempotency-Key", "list-comment-" + i + "-" + System.nanoTime())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"authorRef\":\"user-" + i + "\",\"body\":\"Comment " + i + "\"}"))
                        .andExpect(status().isCreated());
            }

            mockMvc.perform(get("/internal/v1/support/tickets/" + ticketId + "/comments")
                            .header("X-Tenant-ID", TENANT_ID)
                            .header("X-Pod-ID", "national")
                            .header("X-Request-ID", UUID.randomUUID().toString())
                            .header("X-Correlation-ID", UUID.randomUUID().toString())
                            .param("cursor", "0")
                            .param("limit", "10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items").isArray())
                    .andExpect(jsonPath("$.items.length()").value(2))
                    .andExpect(jsonPath("$.limit").value(10))
                    .andExpect(jsonPath("$.total_elements").value(2));
        }

        @Test
        @DisplayName("Comment with missing authorRef returns 400")
        void commentMissingAuthorRef() throws Exception {
            String ticketId = createTicket();

            mockMvc.perform(post("/internal/v1/support/tickets/" + ticketId + "/comments")
                            .header("X-Tenant-ID", TENANT_ID)
                            .header("X-Pod-ID", "national")
                            .header("X-Request-ID", UUID.randomUUID().toString())
                            .header("X-Correlation-ID", UUID.randomUUID().toString())
                            .header("Idempotency-Key", "comment-no-author-" + System.nanoTime())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"body\":\"Missing author\"}"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Comment with missing body returns 400")
        void commentMissingBody() throws Exception {
            String ticketId = createTicket();

            mockMvc.perform(post("/internal/v1/support/tickets/" + ticketId + "/comments")
                            .header("X-Tenant-ID", TENANT_ID)
                            .header("X-Pod-ID", "national")
                            .header("X-Request-ID", UUID.randomUUID().toString())
                            .header("X-Correlation-ID", UUID.randomUUID().toString())
                            .header("Idempotency-Key", "comment-no-body-" + System.nanoTime())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"authorRef\":\"nurse-001\"}"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Comment creates outbox event")
        void commentCreatesOutboxEvent() throws Exception {
            String ticketId = createTicket();
            String correlationId = UUID.randomUUID().toString();

            mockMvc.perform(post("/internal/v1/support/tickets/" + ticketId + "/comments")
                            .header("X-Tenant-ID", TENANT_ID)
                            .header("X-Pod-ID", "national")
                            .header("X-Request-ID", UUID.randomUUID().toString())
                            .header("X-Correlation-ID", correlationId)
                            .header("Idempotency-Key", "comment-outbox-" + System.nanoTime())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"authorRef\":\"nurse-001\",\"body\":\"Outbox comment\"}"))
                    .andExpect(status().isCreated());

            var outboxRows = outboxRepository.findByAggregateIdAndEventType(
                    ticketId, "impilo.support.ticket.comment.added.v1");

            assertThat(outboxRows).hasSize(1);
            var row = outboxRows.get(0);
            assertThat(row.getAggregateType()).isEqualTo("Ticket");
            assertThat(row.getCorrelationId().toString()).isEqualTo(correlationId);
            assertThat(row.getProducer()).isEqualTo("support-service");
            assertThat(row.getPayloadJson()).contains("nurse-001");
            assertThat(row.getPayloadJson()).contains("Outbox comment");
        }
    }

    // ── 2) Escalation Flow ───────────────────────────────────────────────

    @Nested
    @DisplayName("2) Escalation Flow")
    class EscalationFlow {

        @Test
        @DisplayName("POST /tickets/{id}/escalate returns 200")
        void escalateTicket() throws Exception {
            String ticketId = createTicket();

            mockMvc.perform(post("/internal/v1/support/tickets/" + ticketId + "/escalate")
                            .header("X-Tenant-ID", TENANT_ID)
                            .header("X-Pod-ID", "national")
                            .header("X-Request-ID", UUID.randomUUID().toString())
                            .header("X-Correlation-ID", UUID.randomUUID().toString())
                            .header("Idempotency-Key", "escalate-" + System.nanoTime())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"escalatedBy\":\"supervisor-1\",\"targetLevel\":1}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.ticketId").value(ticketId))
                    .andExpect(jsonPath("$.escalationLevel").value(1))
                    .andExpect(jsonPath("$.escalatedBy").value("supervisor-1"))
                    .andExpect(jsonPath("$.escalatedAt").exists());
        }

        @Test
        @DisplayName("Escalation increments level on successive calls")
        void escalationIncrementsLevel() throws Exception {
            String ticketId = createTicket();

            // Escalate to level 1
            mockMvc.perform(post("/internal/v1/support/tickets/" + ticketId + "/escalate")
                            .header("X-Tenant-ID", TENANT_ID)
                            .header("X-Pod-ID", "national")
                            .header("X-Request-ID", UUID.randomUUID().toString())
                            .header("X-Correlation-ID", UUID.randomUUID().toString())
                            .header("Idempotency-Key", "escalate-1-" + System.nanoTime())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"escalatedBy\":\"supervisor-1\",\"targetLevel\":1}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.escalationLevel").value(1));

            // Escalate to level 2
            mockMvc.perform(post("/internal/v1/support/tickets/" + ticketId + "/escalate")
                            .header("X-Tenant-ID", TENANT_ID)
                            .header("X-Pod-ID", "national")
                            .header("X-Request-ID", UUID.randomUUID().toString())
                            .header("X-Correlation-ID", UUID.randomUUID().toString())
                            .header("Idempotency-Key", "escalate-2-" + System.nanoTime())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"escalatedBy\":\"director-1\",\"targetLevel\":2}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.escalationLevel").value(2));
        }

        @Test
        @DisplayName("Escalation level >= 3 sets priority to CRITICAL")
        void escalationLevel3SetsCritical() throws Exception {
            String ticketId = createTicket("Critical Escalation", "Needs critical", "user-1", "INCIDENT", "MEDIUM");

            mockMvc.perform(post("/internal/v1/support/tickets/" + ticketId + "/escalate")
                            .header("X-Tenant-ID", TENANT_ID)
                            .header("X-Pod-ID", "national")
                            .header("X-Request-ID", UUID.randomUUID().toString())
                            .header("X-Correlation-ID", UUID.randomUUID().toString())
                            .header("Idempotency-Key", "escalate-critical-" + System.nanoTime())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"escalatedBy\":\"cto-1\",\"targetLevel\":3}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.escalationLevel").value(3))
                    .andExpect(jsonPath("$.priority").value("CRITICAL"));
        }

        @Test
        @DisplayName("Escalation creates outbox event")
        void escalationCreatesOutboxEvent() throws Exception {
            String ticketId = createTicket();
            String correlationId = UUID.randomUUID().toString();

            mockMvc.perform(post("/internal/v1/support/tickets/" + ticketId + "/escalate")
                            .header("X-Tenant-ID", TENANT_ID)
                            .header("X-Pod-ID", "national")
                            .header("X-Request-ID", UUID.randomUUID().toString())
                            .header("X-Correlation-ID", correlationId)
                            .header("Idempotency-Key", "escalate-outbox-" + System.nanoTime())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"escalatedBy\":\"supervisor-1\",\"targetLevel\":2}"))
                    .andExpect(status().isOk());

            var outboxRows = outboxRepository.findByAggregateIdAndEventType(
                    ticketId, "impilo.support.ticket.escalated.v1");

            assertThat(outboxRows).hasSize(1);
            var row = outboxRows.get(0);
            assertThat(row.getAggregateType()).isEqualTo("Ticket");
            assertThat(row.getCorrelationId().toString()).isEqualTo(correlationId);
            assertThat(row.getProducer()).isEqualTo("support-service");
            assertThat(row.getPayloadJson()).contains("escalation_level");
            assertThat(row.getPayloadJson()).contains("supervisor-1");
        }
    }

    // ── 3) Assignment Flow ───────────────────────────────────────────────

    @Nested
    @DisplayName("3) Assignment Flow")
    class AssignmentFlow {

        @Test
        @DisplayName("POST /tickets/{id}/assign returns 201 with assignment fields")
        void assignTicket() throws Exception {
            String ticketId = createTicket();

            mockMvc.perform(post("/internal/v1/support/tickets/" + ticketId + "/assign")
                            .header("X-Tenant-ID", TENANT_ID)
                            .header("X-Pod-ID", "national")
                            .header("X-Request-ID", UUID.randomUUID().toString())
                            .header("X-Correlation-ID", UUID.randomUUID().toString())
                            .header("Idempotency-Key", "assign-" + System.nanoTime())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"assigneeRef\":\"agent-1\",\"assignedBy\":\"manager-1\"}"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.assignmentId").exists())
                    .andExpect(jsonPath("$.ticketId").value(ticketId))
                    .andExpect(jsonPath("$.assigneeRef").value("agent-1"))
                    .andExpect(jsonPath("$.assignedBy").value("manager-1"))
                    .andExpect(jsonPath("$.assignedAt").exists());
        }

        @Test
        @DisplayName("GET /tickets/{id}/assignments returns assignment history")
        void listAssignments() throws Exception {
            String ticketId = createTicket();

            // Assign twice to create history
            mockMvc.perform(post("/internal/v1/support/tickets/" + ticketId + "/assign")
                            .header("X-Tenant-ID", TENANT_ID)
                            .header("X-Pod-ID", "national")
                            .header("X-Request-ID", UUID.randomUUID().toString())
                            .header("X-Correlation-ID", UUID.randomUUID().toString())
                            .header("Idempotency-Key", "assign-hist-1-" + System.nanoTime())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"assigneeRef\":\"agent-1\",\"assignedBy\":\"manager-1\"}"))
                    .andExpect(status().isCreated());

            mockMvc.perform(post("/internal/v1/support/tickets/" + ticketId + "/assign")
                            .header("X-Tenant-ID", TENANT_ID)
                            .header("X-Pod-ID", "national")
                            .header("X-Request-ID", UUID.randomUUID().toString())
                            .header("X-Correlation-ID", UUID.randomUUID().toString())
                            .header("Idempotency-Key", "assign-hist-2-" + System.nanoTime())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"assigneeRef\":\"agent-2\",\"assignedBy\":\"manager-1\"}"))
                    .andExpect(status().isCreated());

            mockMvc.perform(get("/internal/v1/support/tickets/" + ticketId + "/assignments")
                            .header("X-Tenant-ID", TENANT_ID)
                            .header("X-Pod-ID", "national")
                            .header("X-Request-ID", UUID.randomUUID().toString())
                            .header("X-Correlation-ID", UUID.randomUUID().toString())
                            .param("cursor", "0")
                            .param("limit", "10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items").isArray())
                    .andExpect(jsonPath("$.items.length()").value(2))
                    .andExpect(jsonPath("$.total_elements").value(2));
        }

        @Test
        @DisplayName("Assignment updates ticket's assigneeRef")
        void assignmentUpdatesTicketAssignee() throws Exception {
            String ticketId = createTicket();

            mockMvc.perform(post("/internal/v1/support/tickets/" + ticketId + "/assign")
                            .header("X-Tenant-ID", TENANT_ID)
                            .header("X-Pod-ID", "national")
                            .header("X-Request-ID", UUID.randomUUID().toString())
                            .header("X-Correlation-ID", UUID.randomUUID().toString())
                            .header("Idempotency-Key", "assign-update-" + System.nanoTime())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"assigneeRef\":\"agent-99\",\"assignedBy\":\"manager-1\"}"))
                    .andExpect(status().isCreated());

            // Verify ticket now reflects the assignee
            mockMvc.perform(get("/internal/v1/support/tickets/" + ticketId)
                            .header("X-Tenant-ID", TENANT_ID)
                            .header("X-Pod-ID", "national")
                            .header("X-Request-ID", UUID.randomUUID().toString())
                            .header("X-Correlation-ID", UUID.randomUUID().toString()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.assigneeRef").value("agent-99"));
        }

        @Test
        @DisplayName("Assignment creates outbox event")
        void assignmentCreatesOutboxEvent() throws Exception {
            String ticketId = createTicket();
            String correlationId = UUID.randomUUID().toString();

            mockMvc.perform(post("/internal/v1/support/tickets/" + ticketId + "/assign")
                            .header("X-Tenant-ID", TENANT_ID)
                            .header("X-Pod-ID", "national")
                            .header("X-Request-ID", UUID.randomUUID().toString())
                            .header("X-Correlation-ID", correlationId)
                            .header("Idempotency-Key", "assign-outbox-" + System.nanoTime())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"assigneeRef\":\"agent-1\",\"assignedBy\":\"manager-1\"}"))
                    .andExpect(status().isCreated());

            var outboxRows = outboxRepository.findByAggregateIdAndEventType(
                    ticketId, "impilo.support.ticket.assigned.v1");

            assertThat(outboxRows).hasSize(1);
            var row = outboxRows.get(0);
            assertThat(row.getAggregateType()).isEqualTo("Ticket");
            assertThat(row.getCorrelationId().toString()).isEqualTo(correlationId);
            assertThat(row.getProducer()).isEqualTo("support-service");
            assertThat(row.getPayloadJson()).contains("agent-1");
            assertThat(row.getPayloadJson()).contains("manager-1");
        }
    }

    // ── 4) Message Flow ──────────────────────────────────────────────────

    @Nested
    @DisplayName("4) Message Flow")
    class MessageFlow {

        @Test
        @DisplayName("POST /tickets/{id}/messages returns 201 with message fields")
        void sendMessage() throws Exception {
            String ticketId = createTicket();

            mockMvc.perform(post("/internal/v1/support/tickets/" + ticketId + "/messages")
                            .header("X-Tenant-ID", TENANT_ID)
                            .header("X-Pod-ID", "national")
                            .header("X-Request-ID", UUID.randomUUID().toString())
                            .header("X-Correlation-ID", UUID.randomUUID().toString())
                            .header("Idempotency-Key", "message-" + System.nanoTime())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"senderRef\":\"nurse-001\",\"senderType\":\"AGENT\",\"body\":\"Hello from agent\"}"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.messageId").exists())
                    .andExpect(jsonPath("$.ticketId").value(ticketId))
                    .andExpect(jsonPath("$.senderRef").value("nurse-001"))
                    .andExpect(jsonPath("$.senderType").value("AGENT"))
                    .andExpect(jsonPath("$.body").value("Hello from agent"))
                    .andExpect(jsonPath("$.createdAt").exists());
        }

        @Test
        @DisplayName("GET /tickets/{id}/messages returns paged list")
        void listMessages() throws Exception {
            String ticketId = createTicket();

            // Send three messages
            for (int i = 1; i <= 3; i++) {
                mockMvc.perform(post("/internal/v1/support/tickets/" + ticketId + "/messages")
                                .header("X-Tenant-ID", TENANT_ID)
                                .header("X-Pod-ID", "national")
                                .header("X-Request-ID", UUID.randomUUID().toString())
                                .header("X-Correlation-ID", UUID.randomUUID().toString())
                                .header("Idempotency-Key", "list-msg-" + i + "-" + System.nanoTime())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"senderRef\":\"user-" + i + "\",\"senderType\":\"PATIENT\",\"body\":\"Message " + i + "\"}"))
                        .andExpect(status().isCreated());
            }

            mockMvc.perform(get("/internal/v1/support/tickets/" + ticketId + "/messages")
                            .header("X-Tenant-ID", TENANT_ID)
                            .header("X-Pod-ID", "national")
                            .header("X-Request-ID", UUID.randomUUID().toString())
                            .header("X-Correlation-ID", UUID.randomUUID().toString())
                            .param("cursor", "0")
                            .param("limit", "20"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items").isArray())
                    .andExpect(jsonPath("$.items.length()").value(3))
                    .andExpect(jsonPath("$.limit").value(20))
                    .andExpect(jsonPath("$.total_elements").value(3));
        }

        @Test
        @DisplayName("Message with missing senderRef returns 400")
        void messageMissingSenderRef() throws Exception {
            String ticketId = createTicket();

            mockMvc.perform(post("/internal/v1/support/tickets/" + ticketId + "/messages")
                            .header("X-Tenant-ID", TENANT_ID)
                            .header("X-Pod-ID", "national")
                            .header("X-Request-ID", UUID.randomUUID().toString())
                            .header("X-Correlation-ID", UUID.randomUUID().toString())
                            .header("Idempotency-Key", "msg-no-sender-" + System.nanoTime())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"senderType\":\"AGENT\",\"body\":\"No sender ref\"}"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Message with missing senderType returns 400")
        void messageMissingSenderType() throws Exception {
            String ticketId = createTicket();

            mockMvc.perform(post("/internal/v1/support/tickets/" + ticketId + "/messages")
                            .header("X-Tenant-ID", TENANT_ID)
                            .header("X-Pod-ID", "national")
                            .header("X-Request-ID", UUID.randomUUID().toString())
                            .header("X-Correlation-ID", UUID.randomUUID().toString())
                            .header("Idempotency-Key", "msg-no-type-" + System.nanoTime())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"senderRef\":\"nurse-001\",\"body\":\"No sender type\"}"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Message with missing body returns 400")
        void messageMissingBody() throws Exception {
            String ticketId = createTicket();

            mockMvc.perform(post("/internal/v1/support/tickets/" + ticketId + "/messages")
                            .header("X-Tenant-ID", TENANT_ID)
                            .header("X-Pod-ID", "national")
                            .header("X-Request-ID", UUID.randomUUID().toString())
                            .header("X-Correlation-ID", UUID.randomUUID().toString())
                            .header("Idempotency-Key", "msg-no-body-" + System.nanoTime())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"senderRef\":\"nurse-001\",\"senderType\":\"AGENT\"}"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Message creates outbox event")
        void messageCreatesOutboxEvent() throws Exception {
            String ticketId = createTicket();
            String correlationId = UUID.randomUUID().toString();

            mockMvc.perform(post("/internal/v1/support/tickets/" + ticketId + "/messages")
                            .header("X-Tenant-ID", TENANT_ID)
                            .header("X-Pod-ID", "national")
                            .header("X-Request-ID", UUID.randomUUID().toString())
                            .header("X-Correlation-ID", correlationId)
                            .header("Idempotency-Key", "msg-outbox-" + System.nanoTime())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"senderRef\":\"nurse-001\",\"senderType\":\"AGENT\",\"body\":\"Outbox message\"}"))
                    .andExpect(status().isCreated());

            var outboxRows = outboxRepository.findByAggregateIdAndEventType(
                    ticketId, "impilo.support.message.sent.v1");

            assertThat(outboxRows).hasSize(1);
            var row = outboxRows.get(0);
            assertThat(row.getAggregateType()).isEqualTo("Ticket");
            assertThat(row.getCorrelationId().toString()).isEqualTo(correlationId);
            assertThat(row.getProducer()).isEqualTo("support-service");
            assertThat(row.getPayloadJson()).contains("nurse-001");
            assertThat(row.getPayloadJson()).contains("Outbox message");
        }
    }

    // ── 5) Article Update ────────────────────────────────────────────────

    @Nested
    @DisplayName("5) Article Update")
    class ArticleUpdate {

        @Test
        @DisplayName("PATCH /articles/{id} returns 200 with updated fields")
        void updateArticle() throws Exception {
            String articleId = createArticle();

            mockMvc.perform(patch("/internal/v1/support/articles/" + articleId)
                            .header("X-Tenant-ID", TENANT_ID)
                            .header("X-Pod-ID", "national")
                            .header("X-Request-ID", UUID.randomUUID().toString())
                            .header("X-Correlation-ID", UUID.randomUUID().toString())
                            .header("Idempotency-Key", "update-article-" + System.nanoTime())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"title\":\"Updated Title\",\"body\":\"Updated body content\",\"status\":\"PUBLISHED\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.articleId").value(articleId))
                    .andExpect(jsonPath("$.title").value("Updated Title"))
                    .andExpect(jsonPath("$.body").value("Updated body content"))
                    .andExpect(jsonPath("$.status").value("PUBLISHED"))
                    .andExpect(jsonPath("$.publishedAt").exists());
        }

        @Test
        @DisplayName("Article update changes title, body, and status")
        void updateArticleChangesFields() throws Exception {
            String articleId = createArticle();

            // Update
            mockMvc.perform(patch("/internal/v1/support/articles/" + articleId)
                            .header("X-Tenant-ID", TENANT_ID)
                            .header("X-Pod-ID", "national")
                            .header("X-Request-ID", UUID.randomUUID().toString())
                            .header("X-Correlation-ID", UUID.randomUUID().toString())
                            .header("Idempotency-Key", "update-verify-" + System.nanoTime())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"title\":\"New Title\",\"body\":\"New body\",\"status\":\"PUBLISHED\"}"))
                    .andExpect(status().isOk());

            // Read back to verify
            mockMvc.perform(get("/internal/v1/support/articles/" + articleId)
                            .header("X-Tenant-ID", TENANT_ID)
                            .header("X-Pod-ID", "national")
                            .header("X-Request-ID", UUID.randomUUID().toString())
                            .header("X-Correlation-ID", UUID.randomUUID().toString()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.title").value("New Title"))
                    .andExpect(jsonPath("$.body").value("New body"))
                    .andExpect(jsonPath("$.status").value("PUBLISHED"));
        }

        @Test
        @DisplayName("Article update creates outbox event")
        void articleUpdateCreatesOutboxEvent() throws Exception {
            String articleId = createArticle();
            String correlationId = UUID.randomUUID().toString();

            mockMvc.perform(patch("/internal/v1/support/articles/" + articleId)
                            .header("X-Tenant-ID", TENANT_ID)
                            .header("X-Pod-ID", "national")
                            .header("X-Request-ID", UUID.randomUUID().toString())
                            .header("X-Correlation-ID", correlationId)
                            .header("Idempotency-Key", "article-outbox-" + System.nanoTime())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"title\":\"Outbox Update\"}"))
                    .andExpect(status().isOk());

            var outboxRows = outboxRepository.findByAggregateIdAndEventType(
                    articleId, "impilo.support.article.updated.v1");

            assertThat(outboxRows).hasSize(1);
            var row = outboxRows.get(0);
            assertThat(row.getAggregateType()).isEqualTo("Article");
            assertThat(row.getSubjectType()).isEqualTo("Article");
            assertThat(row.getCorrelationId().toString()).isEqualTo(correlationId);
            assertThat(row.getProducer()).isEqualTo("support-service");
            assertThat(row.getPayloadJson()).contains("Outbox Update");
        }
    }

    // ── 6) Dashboard Stats ───────────────────────────────────────────────

    @Nested
    @DisplayName("6) Dashboard Stats")
    class DashboardStats {

        @Test
        @DisplayName("GET /dashboard/stats returns stats object with all fields")
        void getDashboardStats() throws Exception {
            // Create a ticket so there is at least some data
            createTicket("Dashboard Test", "Stats test", "user-stats", "INCIDENT", "HIGH");

            mockMvc.perform(get("/internal/v1/support/dashboard/stats")
                            .header("X-Tenant-ID", TENANT_ID)
                            .header("X-Pod-ID", "national")
                            .header("X-Request-ID", UUID.randomUUID().toString())
                            .header("X-Correlation-ID", UUID.randomUUID().toString()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.openCount").isNumber())
                    .andExpect(jsonPath("$.inProgressCount").isNumber())
                    .andExpect(jsonPath("$.resolvedCount").isNumber())
                    .andExpect(jsonPath("$.closedCount").isNumber())
                    .andExpect(jsonPath("$.criticalCount").isNumber())
                    .andExpect(jsonPath("$.highCount").isNumber())
                    .andExpect(jsonPath("$.escalatedCount").isNumber())
                    .andExpect(jsonPath("$.avgResolutionHours").isNumber());
        }
    }

    // ── 7) Diagnostics Linkage ───────────────────────────────────────────

    @Nested
    @DisplayName("7) Diagnostics Linkage")
    class DiagnosticsLinkage {

        @Test
        @DisplayName("Ticket created with correlation_id can be retrieved with that correlation_id in response")
        void ticketPreservesCorrelationId() throws Exception {
            String correlationId = UUID.randomUUID().toString();
            String requestId = UUID.randomUUID().toString();

            MvcResult createResult = mockMvc.perform(post("/internal/v1/support/tickets")
                            .header("X-Tenant-ID", TENANT_ID)
                            .header("X-Pod-ID", "national")
                            .header("X-Request-ID", requestId)
                            .header("X-Correlation-ID", correlationId)
                            .header("Idempotency-Key", "diag-corr-" + System.nanoTime())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"title\":\"Diagnostics Test\",\"description\":\"Correlation test\",\"reporterRef\":\"diag-user\"}"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.correlationId").value(correlationId))
                    .andReturn();

            String ticketId = MAPPER.readTree(createResult.getResponse().getContentAsString())
                    .get("ticketId").asText();

            // Retrieve and verify correlation_id persists
            mockMvc.perform(get("/internal/v1/support/tickets/" + ticketId)
                            .header("X-Tenant-ID", TENANT_ID)
                            .header("X-Pod-ID", "national")
                            .header("X-Request-ID", UUID.randomUUID().toString())
                            .header("X-Correlation-ID", UUID.randomUUID().toString()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.correlationId").value(correlationId));
        }

        @Test
        @DisplayName("Ticket created with request_id preserves the linkage")
        void ticketPreservesRequestId() throws Exception {
            String requestId = UUID.randomUUID().toString();

            MvcResult createResult = mockMvc.perform(post("/internal/v1/support/tickets")
                            .header("X-Tenant-ID", TENANT_ID)
                            .header("X-Pod-ID", "national")
                            .header("X-Request-ID", requestId)
                            .header("X-Correlation-ID", UUID.randomUUID().toString())
                            .header("Idempotency-Key", "diag-req-" + System.nanoTime())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"title\":\"Request ID Test\",\"description\":\"Request linkage test\",\"reporterRef\":\"diag-user\"}"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.requestId").value(requestId))
                    .andReturn();

            String ticketId = MAPPER.readTree(createResult.getResponse().getContentAsString())
                    .get("ticketId").asText();

            // Retrieve and verify request_id persists
            mockMvc.perform(get("/internal/v1/support/tickets/" + ticketId)
                            .header("X-Tenant-ID", TENANT_ID)
                            .header("X-Pod-ID", "national")
                            .header("X-Request-ID", UUID.randomUUID().toString())
                            .header("X-Correlation-ID", UUID.randomUUID().toString()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.requestId").value(requestId));
        }
    }

    // ── 8) Ticket Filtering ──────────────────────────────────────────────

    @Nested
    @DisplayName("8) Ticket Filtering")
    class TicketFiltering {

        @Test
        @DisplayName("GET /tickets?category=INCIDENT returns only incidents")
        void filterByCategory() throws Exception {
            // Create an INCIDENT ticket and a SERVICE_REQUEST ticket
            createTicket("Incident Ticket", "An incident", "user-filter", "INCIDENT", "HIGH");
            createTicket("Service Request", "A request", "user-filter", "SERVICE_REQUEST", "LOW");

            MvcResult result = mockMvc.perform(get("/internal/v1/support/tickets")
                            .header("X-Tenant-ID", TENANT_ID)
                            .header("X-Pod-ID", "national")
                            .header("X-Request-ID", UUID.randomUUID().toString())
                            .header("X-Correlation-ID", UUID.randomUUID().toString())
                            .param("category", "INCIDENT")
                            .param("cursor", "0")
                            .param("limit", "50"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items").isArray())
                    .andReturn();

            JsonNode body = MAPPER.readTree(result.getResponse().getContentAsString());
            JsonNode items = body.get("items");
            assertThat(items.size()).isGreaterThanOrEqualTo(1);
            for (JsonNode item : items) {
                assertThat(item.get("category").asText()).isEqualTo("INCIDENT");
            }
        }

        @Test
        @DisplayName("GET /tickets?assigneeRef=agent-1 returns only assigned tickets")
        void filterByAssigneeRef() throws Exception {
            // Create and assign a ticket to agent-1
            String ticketId = createTicket("Assigned Ticket", "For agent-1", "user-filter", null, null);
            mockMvc.perform(post("/internal/v1/support/tickets/" + ticketId + "/assign")
                            .header("X-Tenant-ID", TENANT_ID)
                            .header("X-Pod-ID", "national")
                            .header("X-Request-ID", UUID.randomUUID().toString())
                            .header("X-Correlation-ID", UUID.randomUUID().toString())
                            .header("Idempotency-Key", "filter-assign-" + System.nanoTime())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"assigneeRef\":\"agent-filter-1\",\"assignedBy\":\"manager-1\"}"))
                    .andExpect(status().isCreated());

            // Create another ticket not assigned to agent-filter-1
            createTicket("Unassigned Ticket", "Not for agent-filter-1", "user-filter", null, null);

            MvcResult result = mockMvc.perform(get("/internal/v1/support/tickets")
                            .header("X-Tenant-ID", TENANT_ID)
                            .header("X-Pod-ID", "national")
                            .header("X-Request-ID", UUID.randomUUID().toString())
                            .header("X-Correlation-ID", UUID.randomUUID().toString())
                            .param("assigneeRef", "agent-filter-1")
                            .param("cursor", "0")
                            .param("limit", "50"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items").isArray())
                    .andReturn();

            JsonNode body = MAPPER.readTree(result.getResponse().getContentAsString());
            JsonNode items = body.get("items");
            assertThat(items.size()).isGreaterThanOrEqualTo(1);
            for (JsonNode item : items) {
                assertThat(item.get("assigneeRef").asText()).isEqualTo("agent-filter-1");
            }
        }
    }
}
