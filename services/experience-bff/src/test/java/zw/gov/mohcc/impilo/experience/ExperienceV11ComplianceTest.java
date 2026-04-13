package zw.gov.mohcc.impilo.experience;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

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
@ActiveProfiles("test")
@ExtendWith(DockerOrExternalPostgresCondition.class)
class ExperienceV11ComplianceTest {

    private static final ExperienceBffTestRedisSupport REDIS = ExperienceBffTestRedisSupport.fromEnvironment();

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        REDIS.configure(registry);
    }

    @AfterAll
    static void stopRedis() {
        REDIS.stop();
    }

    @Autowired
    private MockMvc mockMvc;

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String TENANT_ID = "moh-zw";
    private static final String POD_ID = "national";

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
    @org.junit.jupiter.api.Disabled("BFF proxy: local PostgreSQL event_outbox removed; sovereign services emit outbox events")
    class OutboxFields {

        @Test
        @DisplayName("Outbox event from report generation contains all required fields")
        void outboxEventContainsRequiredFields() {
            // Obsolete: see nested class @Disabled
        }

        @Test
        @DisplayName("Encounter creation writes outbox event with correct event_type")
        void encounterOutboxEventType() {
            // Obsolete: see nested class @Disabled
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
