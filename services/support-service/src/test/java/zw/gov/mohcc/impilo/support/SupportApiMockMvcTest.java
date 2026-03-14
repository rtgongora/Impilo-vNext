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
public class SupportApiMockMvcTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private OutboxEventRepository outboxRepository;

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String TENANT_ID = UUID.randomUUID().toString();

    // ── A) Missing required headers => 400 envelope ──

    @Nested
    @DisplayName("A) Missing required headers => 400 envelope")
    class MissingHeaders {

        @Test
        @DisplayName("POST /internal/v1/support/tickets without X-Tenant-ID returns 400")
        void missingTenantId() throws Exception {
            MvcResult result = mockMvc.perform(post("/internal/v1/support/tickets")
                            .header("X-Pod-ID", "national")
                            .header("X-Request-ID", "req-1")
                            .header("X-Correlation-ID", "corr-1")
                            .header("Idempotency-Key", "idem-1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"title\":\"Test\",\"description\":\"Desc\",\"reporterRef\":\"user-1\"}"))
                    .andExpect(status().isBadRequest())
                    .andReturn();

            assertErrorEnvelope(result, "MISSING_REQUIRED_HEADER");
        }

        @Test
        @DisplayName("GET /internal/v1/support/tickets without headers returns 400")
        void missingAllHeaders() throws Exception {
            MvcResult result = mockMvc.perform(get("/internal/v1/support/tickets"))
                    .andExpect(status().isBadRequest())
                    .andReturn();

            assertErrorEnvelope(result, "MISSING_REQUIRED_HEADER");
        }
    }

    // ── B) Ticket CRUD lifecycle ──

    @Nested
    @DisplayName("B) Ticket CRUD lifecycle")
    class TicketCrud {

        @Test
        @DisplayName("Create ticket returns 201 with ticket fields")
        void createTicket() throws Exception {
            MvcResult result = mockMvc.perform(post("/internal/v1/support/tickets")
                            .header("X-Tenant-ID", TENANT_ID)
                            .header("X-Pod-ID", "national")
                            .header("X-Request-ID", UUID.randomUUID().toString())
                            .header("X-Correlation-ID", UUID.randomUUID().toString())
                            .header("Idempotency-Key", "create-ticket-" + System.nanoTime())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"title\":\"System Down\",\"description\":\"EHR not responding\",\"reporterRef\":\"nurse-001\",\"category\":\"INCIDENT\",\"priority\":\"HIGH\",\"facilityRef\":\"FAC-001\"}"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.ticketId").exists())
                    .andExpect(jsonPath("$.title").value("System Down"))
                    .andExpect(jsonPath("$.status").value("OPEN"))
                    .andExpect(jsonPath("$.priority").value("HIGH"))
                    .andReturn();

            JsonNode body = MAPPER.readTree(result.getResponse().getContentAsString());
            assertThat(body.get("ticketId").asText()).isNotBlank();
        }

        @Test
        @DisplayName("Update ticket status to RESOLVED sets resolvedAt")
        void updateTicketToResolved() throws Exception {
            // Create ticket
            MvcResult createResult = mockMvc.perform(post("/internal/v1/support/tickets")
                            .header("X-Tenant-ID", TENANT_ID)
                            .header("X-Pod-ID", "national")
                            .header("X-Request-ID", UUID.randomUUID().toString())
                            .header("X-Correlation-ID", UUID.randomUUID().toString())
                            .header("Idempotency-Key", "resolve-create-" + System.nanoTime())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"title\":\"Bug\",\"description\":\"Minor bug\",\"reporterRef\":\"dev-001\"}"))
                    .andExpect(status().isCreated())
                    .andReturn();

            String ticketId = MAPPER.readTree(createResult.getResponse().getContentAsString())
                    .get("ticketId").asText();

            // Update to RESOLVED
            MvcResult updateResult = mockMvc.perform(patch("/internal/v1/support/tickets/" + ticketId)
                            .header("X-Tenant-ID", TENANT_ID)
                            .header("X-Pod-ID", "national")
                            .header("X-Request-ID", UUID.randomUUID().toString())
                            .header("X-Correlation-ID", UUID.randomUUID().toString())
                            .header("Idempotency-Key", "resolve-update-" + System.nanoTime())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"status\":\"RESOLVED\",\"resolution\":\"Fixed the bug\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("RESOLVED"))
                    .andExpect(jsonPath("$.resolution").value("Fixed the bug"))
                    .andExpect(jsonPath("$.resolvedAt").exists())
                    .andReturn();
        }

        @Test
        @DisplayName("GET non-existent ticket returns 404")
        void getTicketNotFound() throws Exception {
            mockMvc.perform(get("/internal/v1/support/tickets/" + UUID.randomUUID())
                            .header("X-Tenant-ID", TENANT_ID)
                            .header("X-Pod-ID", "national")
                            .header("X-Request-ID", UUID.randomUUID().toString())
                            .header("X-Correlation-ID", UUID.randomUUID().toString()))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("List tickets returns paged response")
        void listTickets() throws Exception {
            mockMvc.perform(get("/internal/v1/support/tickets")
                            .header("X-Tenant-ID", TENANT_ID)
                            .header("X-Pod-ID", "national")
                            .header("X-Request-ID", UUID.randomUUID().toString())
                            .header("X-Correlation-ID", UUID.randomUUID().toString())
                            .param("cursor", "0")
                            .param("limit", "10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items").isArray())
                    .andExpect(jsonPath("$.limit").value(10));
        }
    }

    // ── C) Outbox events on ticket creation ──

    @Nested
    @DisplayName("C) Outbox events on ticket creation")
    class OutboxValidation {

        @Test
        @DisplayName("Created ticket produces ticket.created.v1 outbox row")
        void outboxOnCreate() throws Exception {
            String correlationId = UUID.randomUUID().toString();
            String idempotencyKey = "outbox-create-" + System.nanoTime();

            MvcResult result = mockMvc.perform(post("/internal/v1/support/tickets")
                            .header("X-Tenant-ID", TENANT_ID)
                            .header("X-Pod-ID", "national")
                            .header("X-Request-ID", UUID.randomUUID().toString())
                            .header("X-Correlation-ID", correlationId)
                            .header("Idempotency-Key", idempotencyKey)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"title\":\"Outbox Test\",\"description\":\"Testing outbox\",\"reporterRef\":\"test-user\"}"))
                    .andExpect(status().isCreated())
                    .andReturn();

            String ticketId = MAPPER.readTree(result.getResponse().getContentAsString())
                    .get("ticketId").asText();

            var outboxRows = outboxRepository.findByAggregateIdAndEventType(
                    ticketId, "impilo.support.ticket.created.v1");

            assertThat(outboxRows).hasSize(1);
            var row = outboxRows.get(0);
            assertThat(row.getAggregateType()).isEqualTo("Ticket");
            assertThat(row.getCorrelationId().toString()).isEqualTo(correlationId);
            assertThat(row.getIdempotencyKey()).isEqualTo(idempotencyKey);
            assertThat(row.getTenantId()).isNotNull();
            assertThat(row.getOccurredAt()).isNotNull();
        }
    }

    // ── D) Snapshot endpoint ──

    @Nested
    @DisplayName("D) Snapshot endpoint returns items with as_of semantics")
    class SnapshotEndpoint {

        @Test
        @DisplayName("Ticket snapshot returns paged response")
        void ticketSnapshot() throws Exception {
            mockMvc.perform(get("/internal/v1/snapshots/tickets")
                            .header("X-Tenant-ID", TENANT_ID)
                            .header("X-Pod-ID", "national")
                            .header("X-Request-ID", UUID.randomUUID().toString())
                            .header("X-Correlation-ID", UUID.randomUUID().toString())
                            .param("cursor", "0")
                            .param("limit", "10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items").isArray())
                    .andExpect(jsonPath("$.as_of").exists());
        }
    }

    // ── Helpers ──

    private void assertErrorEnvelope(MvcResult result, String expectedCode) throws Exception {
        String body = result.getResponse().getContentAsString();
        assertThat(body).isNotBlank();
        JsonNode root = MAPPER.readTree(body);
        assertThat(root.has("error")).as("Response should contain 'error' field: " + body).isTrue();
        JsonNode error = root.get("error");
        assertThat(error.get("code").asText()).isEqualTo(expectedCode);
        assertThat(error.has("message")).isTrue();
        assertThat(error.has("request_id")).isTrue();
        assertThat(error.has("correlation_id")).isTrue();
    }
}
