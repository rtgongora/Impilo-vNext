package zw.gov.mohcc.impilo.connectorfhir;

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
import zw.gov.mohcc.impilo.connectorfhir.repository.OutboxEventRepository;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class ConnectorFhirApiMockMvcTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private OutboxEventRepository outboxRepository;

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String TENANT_ID = UUID.randomUUID().toString();

    // ── A) Missing headers ──

    @Nested
    @DisplayName("A) Missing headers => 400")
    class MissingHeaders {
        @Test
        void missingTenantId() throws Exception {
            MvcResult result = mockMvc.perform(get("/internal/v1/fhir/destinations")
                            .header("X-Pod-ID", "national")
                            .header("X-Request-ID", "req-1")
                            .header("X-Correlation-ID", "corr-1"))
                    .andExpect(status().isBadRequest())
                    .andReturn();
            assertErrorEnvelope(result, "MISSING_REQUIRED_HEADER");
        }
    }

    // ── B) Relay bundle with audit ──

    @Nested
    @DisplayName("B) Relay FHIR bundle with audit record")
    class RelayBundle {

        @Test
        @DisplayName("Relay bundle to default destination creates audit record")
        void relayToDefault() throws Exception {
            String body = """
                {"resourceType":"Patient","entries":[{"resourceType":"Patient","id":"pat-001"}]}""";

            MvcResult result = mockMvc.perform(post("/internal/v1/fhir/relay")
                            .header("X-Tenant-ID", TENANT_ID)
                            .header("X-Pod-ID", "national")
                            .header("X-Request-ID", UUID.randomUUID().toString())
                            .header("X-Correlation-ID", UUID.randomUUID().toString())
                            .header("Idempotency-Key", "relay-" + System.nanoTime())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.auditId").exists())
                    .andExpect(jsonPath("$.decision").value("ACCEPTED"))
                    .andExpect(jsonPath("$.outcome").value("RELAYED"))
                    .andExpect(jsonPath("$.resourceType").value("Patient"))
                    .andExpect(jsonPath("$.resourceCount").value(1))
                    .andReturn();

            JsonNode responseBody = MAPPER.readTree(result.getResponse().getContentAsString());
            assertThat(responseBody.get("destinationUrl").asText()).isNotBlank();
        }

        @Test
        @DisplayName("Relay to non-existent destination returns 404")
        void relayToMissingDestination() throws Exception {
            String body = String.format("""
                {"resourceType":"Patient","entries":[{"id":"p1"}],"destinationId":"%s"}""",
                    UUID.randomUUID());

            mockMvc.perform(post("/internal/v1/fhir/relay")
                            .header("X-Tenant-ID", TENANT_ID)
                            .header("X-Pod-ID", "national")
                            .header("X-Request-ID", UUID.randomUUID().toString())
                            .header("X-Correlation-ID", UUID.randomUUID().toString())
                            .header("Idempotency-Key", "relay-bad-" + System.nanoTime())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isNotFound());
        }
    }

    // ── C) Destination management ──

    @Nested
    @DisplayName("C) Destination CRUD")
    class Destinations {

        @Test
        @DisplayName("Create destination returns 201")
        void createDestination() throws Exception {
            mockMvc.perform(post("/internal/v1/fhir/destinations")
                            .header("X-Tenant-ID", TENANT_ID)
                            .header("X-Pod-ID", "national")
                            .header("X-Request-ID", UUID.randomUUID().toString())
                            .header("X-Correlation-ID", UUID.randomUUID().toString())
                            .header("Idempotency-Key", "dest-" + System.nanoTime())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"HAPI FHIR\",\"endpointUrl\":\"http://localhost:8090/fhir\",\"resourceTypes\":\"Patient,Observation\"}"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.destinationId").exists())
                    .andExpect(jsonPath("$.name").value("HAPI FHIR"))
                    .andExpect(jsonPath("$.enabled").value(true));
        }

        @Test
        @DisplayName("List destinations returns array")
        void listDestinations() throws Exception {
            mockMvc.perform(get("/internal/v1/fhir/destinations")
                            .header("X-Tenant-ID", TENANT_ID)
                            .header("X-Pod-ID", "national")
                            .header("X-Request-ID", UUID.randomUUID().toString())
                            .header("X-Correlation-ID", UUID.randomUUID().toString()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items").isArray());
        }
    }

    // ── D) Outbox events on relay ──

    @Nested
    @DisplayName("D) Outbox events on relay")
    class OutboxValidation {

        @Test
        @DisplayName("Relay produces relay.accepted.v1 outbox event")
        void outboxOnRelay() throws Exception {
            String idempotencyKey = "outbox-relay-" + System.nanoTime();

            MvcResult result = mockMvc.perform(post("/internal/v1/fhir/relay")
                            .header("X-Tenant-ID", TENANT_ID)
                            .header("X-Pod-ID", "national")
                            .header("X-Request-ID", UUID.randomUUID().toString())
                            .header("X-Correlation-ID", UUID.randomUUID().toString())
                            .header("Idempotency-Key", idempotencyKey)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"resourceType\":\"Observation\",\"entries\":[{\"id\":\"obs-1\"},{\"id\":\"obs-2\"}]}"))
                    .andExpect(status().isCreated())
                    .andReturn();

            String auditId = MAPPER.readTree(result.getResponse().getContentAsString())
                    .get("auditId").asText();

            var outboxRows = outboxRepository.findByAggregateIdAndEventType(
                    auditId, "impilo.integration.relay.accepted.v1");
            assertThat(outboxRows).hasSize(1);
            assertThat(outboxRows.get(0).getIdempotencyKey()).isEqualTo(idempotencyKey);
            assertThat(outboxRows.get(0).getAggregateType()).isEqualTo("FhirRelay");
        }
    }

    // ── E) Audit log ──

    @Nested
    @DisplayName("E) Audit log endpoint")
    class AuditLog {
        @Test
        void listAuditLog() throws Exception {
            mockMvc.perform(get("/internal/v1/fhir/audit")
                            .header("X-Tenant-ID", TENANT_ID)
                            .header("X-Pod-ID", "national")
                            .header("X-Request-ID", UUID.randomUUID().toString())
                            .header("X-Correlation-ID", UUID.randomUUID().toString()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items").isArray())
                    .andExpect(jsonPath("$.limit").value(50));
        }
    }

    // ── Helpers ──

    private void assertErrorEnvelope(MvcResult result, String expectedCode) throws Exception {
        String body = result.getResponse().getContentAsString();
        assertThat(body).isNotBlank();
        JsonNode root = MAPPER.readTree(body);
        assertThat(root.has("error")).as("Response should contain 'error' field: " + body).isTrue();
        assertThat(root.get("error").get("code").asText()).isEqualTo(expectedCode);
    }
}
