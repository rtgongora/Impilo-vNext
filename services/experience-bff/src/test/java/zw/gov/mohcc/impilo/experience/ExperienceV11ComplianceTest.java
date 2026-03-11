package zw.gov.mohcc.impilo.experience;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * V1.1 Spec 2.0 compliance behavior tests for experience-bff.
 *
 * Validates:
 *   1. Header enforcement (4 hard-required headers)
 *   2. Idempotency replay + conflict (IDENTITY_CONFLICT)
 *   3. Outbox write fields (tenant_id, pod_id, correlation_id, schema_version, event_type format)
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
class ExperienceV11ComplianceTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("experience_bff_compliance")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String TENANT_ID = "moh-zw";
    private static final String POD_ID = "national";

    @BeforeEach
    void cleanUp() {
        jdbc.execute("DELETE FROM event_outbox");
    }

    // ── 1. Header Enforcement ──────────────────────────────────────

    @Nested
    @DisplayName("Header Enforcement — Experience BFF")
    class HeaderEnforcement {

        @Test
        @DisplayName("Missing X-Request-ID on GET /facilities returns 400 MISSING_REQUIRED_HEADER")
        void missingRequestIdReturns400() throws Exception {
            MvcResult result = mockMvc.perform(get("/internal/v1/facilities")
                            .header("X-Tenant-ID", TENANT_ID)
                            .header("X-Pod-ID", POD_ID)
                            .header("X-Correlation-ID", "corr-1"))
                    .andExpect(status().isBadRequest())
                    .andReturn();

            assertErrorCode(result, "MISSING_REQUIRED_HEADER");
        }

        @Test
        @DisplayName("Missing X-Correlation-ID on POST /reports/generate returns 400 MISSING_REQUIRED_HEADER")
        void missingCorrelationIdReturns400() throws Exception {
            MvcResult result = mockMvc.perform(post("/internal/v1/reports/generate")
                            .header("X-Tenant-ID", TENANT_ID)
                            .header("X-Pod-ID", POD_ID)
                            .header("X-Request-ID", "req-1")
                            .header("Idempotency-Key", "idem-" + UUID.randomUUID())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"report_type\":\"TEST\",\"requested_by\":\"tester\",\"parameters\":{}}"))
                    .andExpect(status().isBadRequest())
                    .andReturn();

            assertErrorCode(result, "MISSING_REQUIRED_HEADER");
        }

        @Test
        @DisplayName("Missing all four headers returns 400 with details.missing array of size 4")
        void missingAllFourHeadersReturns400() throws Exception {
            MvcResult result = mockMvc.perform(get("/internal/v1/facilities"))
                    .andExpect(status().isBadRequest())
                    .andReturn();

            JsonNode error = MAPPER.readTree(result.getResponse().getContentAsString()).get("error");
            assertThat(error.get("code").asText()).isEqualTo("MISSING_REQUIRED_HEADER");
            assertThat(error.get("details").get("missing").size()).isEqualTo(4);
        }
    }

    // ── 2. Idempotency ─────────────────────────────────────────────

    @Nested
    @DisplayName("Idempotency — Experience BFF")
    class Idempotency {

        @Test
        @DisplayName("Same Idempotency-Key + same body replays exact prior response on POST /reports/generate")
        void sameKeyAndBodyReplays() throws Exception {
            String idempotencyKey = "exp-replay-" + UUID.randomUUID();
            String body = MAPPER.writeValueAsString(Map.of(
                    "report_type", "REPLAY_TEST",
                    "requested_by", "tester",
                    "parameters", Map.of("month", "2026-03")
            ));

            MvcResult first = mockMvc.perform(post("/internal/v1/reports/generate")
                            .header("X-Tenant-ID", TENANT_ID)
                            .header("X-Pod-ID", POD_ID)
                            .header("X-Request-ID", UUID.randomUUID().toString())
                            .header("X-Correlation-ID", UUID.randomUUID().toString())
                            .header("Idempotency-Key", idempotencyKey)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isCreated())
                    .andReturn();

            MvcResult second = mockMvc.perform(post("/internal/v1/reports/generate")
                            .header("X-Tenant-ID", TENANT_ID)
                            .header("X-Pod-ID", POD_ID)
                            .header("X-Request-ID", UUID.randomUUID().toString())
                            .header("X-Correlation-ID", UUID.randomUUID().toString())
                            .header("Idempotency-Key", idempotencyKey)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andReturn();

            assertThat(second.getResponse().getStatus()).isEqualTo(first.getResponse().getStatus());
            assertThat(second.getResponse().getContentAsString())
                    .isEqualTo(first.getResponse().getContentAsString());
        }

        @Test
        @DisplayName("Same Idempotency-Key + different body returns 409 IDENTITY_CONFLICT")
        void sameKeyDifferentBodyReturns409() throws Exception {
            String idempotencyKey = "exp-conflict-" + UUID.randomUUID();

            mockMvc.perform(post("/internal/v1/reports/generate")
                            .header("X-Tenant-ID", TENANT_ID)
                            .header("X-Pod-ID", POD_ID)
                            .header("X-Request-ID", UUID.randomUUID().toString())
                            .header("X-Correlation-ID", UUID.randomUUID().toString())
                            .header("Idempotency-Key", idempotencyKey)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"report_type\":\"TYPE_A\",\"requested_by\":\"user-1\",\"parameters\":{}}"))
                    .andReturn();

            MvcResult result = mockMvc.perform(post("/internal/v1/reports/generate")
                            .header("X-Tenant-ID", TENANT_ID)
                            .header("X-Pod-ID", POD_ID)
                            .header("X-Request-ID", UUID.randomUUID().toString())
                            .header("X-Correlation-ID", UUID.randomUUID().toString())
                            .header("Idempotency-Key", idempotencyKey)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"report_type\":\"TYPE_B\",\"requested_by\":\"user-2\",\"parameters\":{}}"))
                    .andExpect(status().isConflict())
                    .andReturn();

            assertErrorCode(result, "IDENTITY_CONFLICT");
        }
    }

    // ── 3. Outbox Write Fields ──────────────────────────────────────

    @Nested
    @DisplayName("Outbox Write Fields — Experience BFF")
    class OutboxFields {

        @Test
        @DisplayName("Outbox event from report generation contains all required fields")
        void outboxEventContainsRequiredFields() throws Exception {
            String correlationId = "corr-outbox-" + UUID.randomUUID();

            mockMvc.perform(post("/internal/v1/reports/generate")
                            .header("X-Tenant-ID", TENANT_ID)
                            .header("X-Pod-ID", POD_ID)
                            .header("X-Request-ID", UUID.randomUUID().toString())
                            .header("X-Correlation-ID", correlationId)
                            .header("Idempotency-Key", "outbox-test-" + UUID.randomUUID())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"report_type\":\"OUTBOX_TEST\",\"requested_by\":\"tester\",\"parameters\":{}}"))
                    .andExpect(status().isCreated());

            List<Map<String, Object>> events = jdbc.queryForList(
                    "SELECT * FROM event_outbox WHERE correlation_id = ?", correlationId);
            assertThat(events).isNotEmpty();

            Map<String, Object> event = events.get(0);
            assertThat(event.get("tenant_id")).isEqualTo(TENANT_ID);
            assertThat(event.get("pod_id")).isEqualTo(POD_ID);
            assertThat(event.get("correlation_id")).isEqualTo(correlationId);
            assertThat((Integer) event.get("schema_version")).isGreaterThanOrEqualTo(1);
            assertThat(event.get("event_type").toString()).matches("impilo\\.experience\\..+\\.v1");
            assertThat(event.get("occurred_at")).isNotNull();
            assertThat(event.get("payload")).isNotNull();
            assertThat(event.get("subject_type")).isNotNull();
            assertThat(event.get("subject_id")).isNotNull();
        }

        @Test
        @DisplayName("Encounter creation writes outbox event with correct event_type")
        void encounterOutboxEventType() throws Exception {
            String correlationId = "corr-enc-outbox-" + UUID.randomUUID();

            mockMvc.perform(post("/internal/v1/encounters")
                            .header("X-Tenant-ID", TENANT_ID)
                            .header("X-Pod-ID", POD_ID)
                            .header("X-Request-ID", UUID.randomUUID().toString())
                            .header("X-Correlation-ID", correlationId)
                            .header("Idempotency-Key", "enc-outbox-" + UUID.randomUUID())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(MAPPER.writeValueAsString(Map.of(
                                    "patient_id", "a1000000-0000-0000-0000-000000000001",
                                    "facility_id", "f1000000-0000-0000-0000-000000000001",
                                    "encounter_type", "OUTPATIENT",
                                    "chief_complaint", "Compliance test"
                            ))))
                    .andExpect(status().isCreated());

            List<Map<String, Object>> events = jdbc.queryForList(
                    "SELECT * FROM event_outbox WHERE correlation_id = ? AND event_type = 'impilo.experience.encounter.created.v1'",
                    correlationId);
            assertThat(events).hasSize(1);
            assertThat((Integer) events.get(0).get("schema_version")).isGreaterThanOrEqualTo(1);
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────

    private void assertErrorCode(MvcResult result, String expectedCode) throws Exception {
        String body = result.getResponse().getContentAsString();
        JsonNode root = MAPPER.readTree(body);
        assertThat(root.has("error")).as("Response must contain 'error' field").isTrue();
        JsonNode error = root.get("error");
        assertThat(error.get("code").asText()).isEqualTo(expectedCode);
        assertThat(error.has("message")).isTrue();
        assertThat(error.has("request_id")).isTrue();
        assertThat(error.has("correlation_id")).isTrue();
        assertThat(error.has("details")).isTrue();
    }
}
