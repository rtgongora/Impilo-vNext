package zw.gov.mohcc.impilo.ndr;

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
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import zw.gov.mohcc.impilo.ndr.client.GovernanceClient;
import zw.gov.mohcc.impilo.ndr.client.GovernanceClient.DecisionResult;
import zw.gov.mohcc.impilo.ndr.client.GovernanceClient.GovernanceUnavailableException;
import zw.gov.mohcc.impilo.ndr.domain.BronzeEventEntity;
import zw.gov.mohcc.impilo.ndr.domain.OutboxEventEntity;
import zw.gov.mohcc.impilo.ndr.repository.BronzeEventRepository;
import zw.gov.mohcc.impilo.ndr.repository.GoldEncounterRepository;
import zw.gov.mohcc.impilo.ndr.repository.OutboxEventRepository;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class NdrApiMockMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private BronzeEventRepository bronzeRepository;

    @Autowired
    private GoldEncounterRepository goldEncounterRepository;

    @Autowired
    private OutboxEventRepository outboxRepository;

    @MockBean
    private GovernanceClient governanceClient;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String TENANT_ID = UUID.randomUUID().toString();

    private String validEventBody() {
        return "{" +
                "\"event_id\":\"evt-" + System.nanoTime() + "\"," +
                "\"event_type\":\"impilo.butano.fhir.encounter.created.v1\"," +
                "\"schema_version\":1," +
                "\"occurred_at\":\"2026-03-11T10:00:00Z\"," +
                "\"subject_type\":\"Encounter\"," +
                "\"subject_id\":\"" + UUID.randomUUID() + "\"," +
                "\"producer\":\"pct-service\"," +
                "\"meta\":{\"partition_key\":\"pk-" + System.nanoTime() + "\"}," +
                "\"payload\":{\"patient_ref\":\"patient-123\",\"facility_ref\":\"fac-456\",\"status\":\"IN_PROGRESS\"}" +
                "}";
    }

    // ── A) Missing required headers => 400 ──

    @Nested
    @DisplayName("A) Missing required headers => 400 envelope")
    class MissingHeaders {

        @Test
        @DisplayName("POST /internal/v1/ndr/ingest/events without X-Tenant-ID returns 400")
        void missingTenantIdOnIngest() throws Exception {
            MvcResult result = mockMvc.perform(post("/internal/v1/ndr/ingest/events")
                            .header("X-Pod-ID", "national")
                            .header("X-Request-ID", "req-1")
                            .header("X-Correlation-ID", "corr-1")
                            .header("Idempotency-Key", "idem-1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(validEventBody()))
                    .andExpect(status().isBadRequest())
                    .andReturn();

            assertErrorEnvelope(result, "MISSING_REQUIRED_HEADER");
        }

        @Test
        @DisplayName("GET /internal/v1/ndr/query/bronze without X-Tenant-ID returns 400")
        void missingTenantIdOnBronzeQuery() throws Exception {
            MvcResult result = mockMvc.perform(get("/internal/v1/ndr/query/bronze")
                            .header("X-Pod-ID", "national")
                            .header("X-Request-ID", "req-1")
                            .header("X-Correlation-ID", "corr-1")
                            .header("X-Purpose-Of-Use", "TREATMENT"))
                    .andExpect(status().isBadRequest())
                    .andReturn();

            assertErrorEnvelope(result, "MISSING_REQUIRED_HEADER");
        }
    }

    // ── B) X-Purpose-Of-Use required on query endpoints ──

    @Nested
    @DisplayName("B) X-Purpose-Of-Use header required on query endpoints")
    class PurposeOfUseRequired {

        @Test
        @DisplayName("GET /internal/v1/ndr/query/bronze without X-Purpose-Of-Use returns 400 MISSING_REQUIRED_HEADER")
        void bronzeQueryWithoutPurpose() throws Exception {
            MvcResult result = mockMvc.perform(get("/internal/v1/ndr/query/bronze")
                            .header("X-Tenant-ID", TENANT_ID)
                            .header("X-Pod-ID", "national")
                            .header("X-Request-ID", "req-1")
                            .header("X-Correlation-ID", UUID.randomUUID().toString()))
                    .andExpect(status().isBadRequest())
                    .andReturn();

            assertErrorEnvelope(result, "MISSING_REQUIRED_HEADER");
        }

        @Test
        @DisplayName("GET /internal/v1/ndr/query/gold/encounters without X-Purpose-Of-Use returns 400 MISSING_REQUIRED_HEADER")
        void goldQueryWithoutPurpose() throws Exception {
            MvcResult result = mockMvc.perform(get("/internal/v1/ndr/query/gold/encounters")
                            .header("X-Tenant-ID", TENANT_ID)
                            .header("X-Pod-ID", "national")
                            .header("X-Request-ID", "req-1")
                            .header("X-Correlation-ID", UUID.randomUUID().toString()))
                    .andExpect(status().isBadRequest())
                    .andReturn();

            assertErrorEnvelope(result, "MISSING_REQUIRED_HEADER");
        }
    }

    // ── C) Governance DENY => 403 ──

    @Nested
    @DisplayName("C) Without grant => governance DENY => 403")
    class GovernanceDeny {

        @Test
        @DisplayName("GET /internal/v1/ndr/query/bronze with governance DENY returns 403")
        void bronzeQueryDeny() throws Exception {
            when(governanceClient.decide(anyString(), anyString(), anyString(),
                    anyString(), anyString(), anyString()))
                    .thenReturn(new DecisionResult("DENY", "dgv-v1.0",
                            List.of("NO_ACTIVE_GRANT"), 60));

            MvcResult result = mockMvc.perform(get("/internal/v1/ndr/query/bronze")
                            .header("X-Tenant-ID", TENANT_ID)
                            .header("X-Pod-ID", "national")
                            .header("X-Request-ID", "req-deny-1")
                            .header("X-Correlation-ID", UUID.randomUUID().toString())
                            .header("X-Purpose-Of-Use", "TREATMENT"))
                    .andExpect(status().isForbidden())
                    .andReturn();

            assertErrorEnvelope(result, "GOVERNANCE_DENY");
        }

        @Test
        @DisplayName("GET /internal/v1/ndr/query/gold/encounters with governance DENY returns 403")
        void goldQueryDeny() throws Exception {
            when(governanceClient.decide(anyString(), anyString(), anyString(),
                    anyString(), anyString(), anyString()))
                    .thenReturn(new DecisionResult("DENY", "dgv-v1.0",
                            List.of("NO_ACTIVE_GRANT"), 60));

            MvcResult result = mockMvc.perform(get("/internal/v1/ndr/query/gold/encounters")
                            .header("X-Tenant-ID", TENANT_ID)
                            .header("X-Pod-ID", "national")
                            .header("X-Request-ID", "req-deny-2")
                            .header("X-Correlation-ID", UUID.randomUUID().toString())
                            .header("X-Purpose-Of-Use", "RESEARCH"))
                    .andExpect(status().isForbidden())
                    .andReturn();

            assertErrorEnvelope(result, "GOVERNANCE_DENY");
        }
    }

    // ── D) Governance ALLOW => 200 with data ──

    @Nested
    @DisplayName("D) With grant => governance ALLOW => 200")
    class GovernanceAllow {

        @Test
        @DisplayName("GET /internal/v1/ndr/query/bronze with governance ALLOW returns 200")
        void bronzeQueryAllow() throws Exception {
            when(governanceClient.decide(anyString(), anyString(), anyString(),
                    anyString(), anyString(), anyString()))
                    .thenReturn(new DecisionResult("ALLOW", "dgv-v1.0",
                            List.of("GRANT_ACTIVE"), 60));

            mockMvc.perform(get("/internal/v1/ndr/query/bronze")
                            .header("X-Tenant-ID", TENANT_ID)
                            .header("X-Pod-ID", "national")
                            .header("X-Request-ID", "req-allow-1")
                            .header("X-Correlation-ID", UUID.randomUUID().toString())
                            .header("X-Purpose-Of-Use", "TREATMENT"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items").isArray())
                    .andExpect(jsonPath("$.limit").exists());
        }

        @Test
        @DisplayName("GET /internal/v1/ndr/query/gold/encounters with governance ALLOW returns 200")
        void goldQueryAllow() throws Exception {
            when(governanceClient.decide(anyString(), anyString(), anyString(),
                    anyString(), anyString(), anyString()))
                    .thenReturn(new DecisionResult("ALLOW", "dgv-v1.0",
                            List.of("GRANT_ACTIVE"), 60));

            mockMvc.perform(get("/internal/v1/ndr/query/gold/encounters")
                            .header("X-Tenant-ID", TENANT_ID)
                            .header("X-Pod-ID", "national")
                            .header("X-Request-ID", "req-allow-2")
                            .header("X-Correlation-ID", UUID.randomUUID().toString())
                            .header("X-Purpose-Of-Use", "PUBLIC_HEALTH"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items").isArray())
                    .andExpect(jsonPath("$.limit").exists());
        }
    }

    // ── E) Governance unavailable => 503 ──

    @Nested
    @DisplayName("E) Governance unavailable => 503")
    class GovernanceUnavailable {

        @Test
        @DisplayName("GET /internal/v1/ndr/query/bronze when governance is down returns 503")
        void bronzeQueryGovernanceDown() throws Exception {
            when(governanceClient.decide(anyString(), anyString(), anyString(),
                    anyString(), anyString(), anyString()))
                    .thenThrow(new GovernanceUnavailableException("Connection refused", new RuntimeException()));

            MvcResult result = mockMvc.perform(get("/internal/v1/ndr/query/bronze")
                            .header("X-Tenant-ID", TENANT_ID)
                            .header("X-Pod-ID", "national")
                            .header("X-Request-ID", "req-unavail-1")
                            .header("X-Correlation-ID", UUID.randomUUID().toString())
                            .header("X-Purpose-Of-Use", "TREATMENT"))
                    .andExpect(status().isServiceUnavailable())
                    .andReturn();

            assertErrorEnvelope(result, "GOVERNANCE_UNAVAILABLE");
        }
    }

    // ── F) Bronze ingest + outbox verification ──

    @Nested
    @DisplayName("F) Bronze ingest stores event and writes outbox")
    class BronzeIngest {

        @Test
        @DisplayName("POST /internal/v1/ndr/ingest/events stores bronze and outbox rows")
        void ingestStoresBronzeAndOutbox() throws Exception {
            String eventBody = validEventBody();
            String correlationId = UUID.randomUUID().toString();
            String idempotencyKey = "ingest-test-" + System.nanoTime();

            MvcResult result = mockMvc.perform(post("/internal/v1/ndr/ingest/events")
                            .header("X-Tenant-ID", TENANT_ID)
                            .header("X-Pod-ID", "national")
                            .header("X-Request-ID", "req-ingest-1")
                            .header("X-Correlation-ID", correlationId)
                            .header("Idempotency-Key", idempotencyKey)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(eventBody))
                    .andExpect(status().isAccepted())
                    .andExpect(jsonPath("$.receipt_id").exists())
                    .andExpect(jsonPath("$.event_id").exists())
                    .andExpect(jsonPath("$.stored_at").exists())
                    .andExpect(jsonPath("$.dedupe").value("NEW"))
                    .andReturn();

            JsonNode body = MAPPER.readTree(result.getResponse().getContentAsString());
            String receiptId = body.get("receipt_id").asText();

            // Verify bronze row
            BronzeEventEntity bronze = bronzeRepository.findById(UUID.fromString(receiptId)).orElse(null);
            assertThat(bronze).isNotNull();
            assertThat(bronze.getTenantId()).isEqualTo(UUID.fromString(TENANT_ID));
            assertThat(bronze.getEnvelopeJson()).isEqualTo(eventBody);

            // Verify outbox row
            List<OutboxEventEntity> outboxRows = outboxRepository.findAll();
            OutboxEventEntity outbox = outboxRows.stream()
                    .filter(o -> o.getAggregateId().equals(receiptId))
                    .findFirst().orElse(null);
            assertThat(outbox).isNotNull();
            assertThat(outbox.getEventType()).isEqualTo("impilo.ndr.bronze.received.v1");
            assertThat(outbox.getProducer()).isEqualTo("ndr-service");
        }

        @Test
        @DisplayName("POST ingest with same event_id returns REPLAY")
        void ingestIdempotentReplay() throws Exception {
            String eventBody = validEventBody();
            String idempotencyKey = "replay-" + System.nanoTime();

            // First ingest
            MvcResult first = mockMvc.perform(post("/internal/v1/ndr/ingest/events")
                            .header("X-Tenant-ID", TENANT_ID)
                            .header("X-Pod-ID", "national")
                            .header("X-Request-ID", "req-replay-1")
                            .header("X-Correlation-ID", UUID.randomUUID().toString())
                            .header("Idempotency-Key", idempotencyKey)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(eventBody))
                    .andExpect(status().isAccepted())
                    .andExpect(jsonPath("$.dedupe").value("NEW"))
                    .andReturn();

            // Second ingest with same idempotency key
            MvcResult second = mockMvc.perform(post("/internal/v1/ndr/ingest/events")
                            .header("X-Tenant-ID", TENANT_ID)
                            .header("X-Pod-ID", "national")
                            .header("X-Request-ID", "req-replay-2")
                            .header("X-Correlation-ID", UUID.randomUUID().toString())
                            .header("Idempotency-Key", idempotencyKey)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(eventBody))
                    .andExpect(status().isAccepted())
                    .andExpect(jsonPath("$.dedupe").value("REPLAY"))
                    .andReturn();

            // Same receipt_id
            JsonNode firstBody = MAPPER.readTree(first.getResponse().getContentAsString());
            JsonNode secondBody = MAPPER.readTree(second.getResponse().getContentAsString());
            assertThat(secondBody.get("receipt_id").asText())
                    .isEqualTo(firstBody.get("receipt_id").asText());
        }
    }

    // ── G) Gold build endpoint ──

    @Nested
    @DisplayName("G) Gold build materializes encounters from bronze")
    class GoldBuild {

        @Test
        @DisplayName("POST /internal/v1/ndr/build/gold/encounters returns 202 with build stats")
        void goldBuildReturns202() throws Exception {
            mockMvc.perform(post("/internal/v1/ndr/build/gold/encounters")
                            .header("X-Tenant-ID", TENANT_ID)
                            .header("X-Pod-ID", "national")
                            .header("X-Request-ID", "req-build-1")
                            .header("X-Correlation-ID", UUID.randomUUID().toString())
                            .header("Idempotency-Key", "build-" + System.nanoTime())
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isAccepted())
                    .andExpect(jsonPath("$.status").value("COMPLETED"))
                    .andExpect(jsonPath("$.build_id").exists())
                    .andExpect(jsonPath("$.encounters_created").exists())
                    .andExpect(jsonPath("$.encounters_updated").exists())
                    .andExpect(jsonPath("$.built_at").exists());
        }
    }

    // ── H) Missing Idempotency-Key on POST => 400 ──

    @Nested
    @DisplayName("H) Missing Idempotency-Key on POST => 400")
    class MissingIdempotencyKey {

        @Test
        @DisplayName("POST /internal/v1/ndr/ingest/events without Idempotency-Key returns 400")
        void missingIdempotencyKeyOnIngest() throws Exception {
            MvcResult result = mockMvc.perform(post("/internal/v1/ndr/ingest/events")
                            .header("X-Tenant-ID", TENANT_ID)
                            .header("X-Pod-ID", "national")
                            .header("X-Request-ID", "req-1")
                            .header("X-Correlation-ID", UUID.randomUUID().toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(validEventBody()))
                    .andExpect(status().isBadRequest())
                    .andReturn();

            assertErrorEnvelope(result, "IDEMPOTENCY_KEY_REQUIRED");
        }

        @Test
        @DisplayName("POST /internal/v1/ndr/build/gold/encounters without Idempotency-Key returns 400")
        void missingIdempotencyKeyOnBuild() throws Exception {
            MvcResult result = mockMvc.perform(post("/internal/v1/ndr/build/gold/encounters")
                            .header("X-Tenant-ID", TENANT_ID)
                            .header("X-Pod-ID", "national")
                            .header("X-Request-ID", "req-1")
                            .header("X-Correlation-ID", UUID.randomUUID().toString())
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isBadRequest())
                    .andReturn();

            assertErrorEnvelope(result, "IDEMPOTENCY_KEY_REQUIRED");
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
