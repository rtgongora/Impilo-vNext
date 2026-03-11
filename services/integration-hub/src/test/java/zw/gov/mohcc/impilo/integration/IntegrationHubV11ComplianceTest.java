package zw.gov.mohcc.impilo.integration;

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
import zw.gov.mohcc.impilo.integration.domain.OutboxEventEntity;
import zw.gov.mohcc.impilo.integration.repository.OutboxEventRepository;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * V1.1 Spec 2.0 compliance behavior tests for integration-hub.
 *
 * Validates:
 *   1. Header enforcement (4 hard-required headers)
 *   2. Idempotency replay + conflict (IDENTITY_CONFLICT)
 *   3. Outbox write fields (tenant_id, pod_id, correlation_id, schema_version, event_type format)
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class IntegrationHubV11ComplianceTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String TENANT_ID = "moh-zw";
    private static final String POD_ID = "national";

    // ── 1. Header Enforcement ──────────────────────────────────────

    @Nested
    @DisplayName("Header Enforcement — Integration Hub")
    class HeaderEnforcement {

        @Test
        @DisplayName("Missing X-Request-ID on GET /routes returns 400 MISSING_REQUIRED_HEADER")
        void missingRequestIdReturns400() throws Exception {
            MvcResult result = mockMvc.perform(get("/internal/v1/routes")
                            .header("X-Tenant-ID", TENANT_ID)
                            .header("X-Pod-ID", POD_ID)
                            .header("X-Correlation-ID", "corr-1"))
                    .andExpect(status().isBadRequest())
                    .andReturn();

            assertErrorCode(result, "MISSING_REQUIRED_HEADER");
        }

        @Test
        @DisplayName("Missing X-Correlation-ID on GET /routes returns 400 MISSING_REQUIRED_HEADER")
        void missingCorrelationIdReturns400() throws Exception {
            MvcResult result = mockMvc.perform(get("/internal/v1/routes")
                            .header("X-Tenant-ID", TENANT_ID)
                            .header("X-Pod-ID", POD_ID)
                            .header("X-Request-ID", "req-1"))
                    .andExpect(status().isBadRequest())
                    .andReturn();

            assertErrorCode(result, "MISSING_REQUIRED_HEADER");
        }

        @Test
        @DisplayName("Missing all four headers on POST /dispatch returns 400 with details.missing array of size 4")
        void missingAllFourHeadersReturns400WithDetailsMissing() throws Exception {
            MvcResult result = mockMvc.perform(post("/internal/v1/dispatch")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"method\":\"GET\",\"path\":\"/test\"}"))
                    .andExpect(status().isBadRequest())
                    .andReturn();

            JsonNode error = MAPPER.readTree(result.getResponse().getContentAsString()).get("error");
            assertThat(error.get("code").asText()).isEqualTo("MISSING_REQUIRED_HEADER");
            assertThat(error.get("details").get("missing").size()).isEqualTo(4);
        }
    }

    // ── 2. Idempotency ─────────────────────────────────────────────

    @Nested
    @DisplayName("Idempotency — Integration Hub")
    class Idempotency {

        @Test
        @DisplayName("Same Idempotency-Key + same body replays exact prior response on POST /routes")
        void sameKeyAndBodyReplays() throws Exception {
            String idempotencyKey = "ih-replay-" + UUID.randomUUID();
            String body = """
                    {
                      "matchMethod": "POST",
                      "matchPathRegex": "/replay-test/.*",
                      "targetUrl": "http://target:8080/replay",
                      "enabled": true
                    }
                    """;

            MvcResult first = mockMvc.perform(post("/internal/v1/routes")
                            .header("X-Tenant-ID", TENANT_ID)
                            .header("X-Pod-ID", POD_ID)
                            .header("X-Request-ID", UUID.randomUUID().toString())
                            .header("X-Correlation-ID", UUID.randomUUID().toString())
                            .header("Idempotency-Key", idempotencyKey)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isCreated())
                    .andReturn();

            MvcResult second = mockMvc.perform(post("/internal/v1/routes")
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
            String idempotencyKey = "ih-conflict-" + UUID.randomUUID();

            mockMvc.perform(post("/internal/v1/routes")
                            .header("X-Tenant-ID", TENANT_ID)
                            .header("X-Pod-ID", POD_ID)
                            .header("X-Request-ID", UUID.randomUUID().toString())
                            .header("X-Correlation-ID", UUID.randomUUID().toString())
                            .header("Idempotency-Key", idempotencyKey)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"matchMethod\":\"GET\",\"matchPathRegex\":\"/first/.*\",\"targetUrl\":\"http://a:8080/a\",\"enabled\":true}"))
                    .andReturn();

            MvcResult result = mockMvc.perform(post("/internal/v1/routes")
                            .header("X-Tenant-ID", TENANT_ID)
                            .header("X-Pod-ID", POD_ID)
                            .header("X-Request-ID", UUID.randomUUID().toString())
                            .header("X-Correlation-ID", UUID.randomUUID().toString())
                            .header("Idempotency-Key", idempotencyKey)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"matchMethod\":\"PUT\",\"matchPathRegex\":\"/second/.*\",\"targetUrl\":\"http://b:8080/b\",\"enabled\":true}"))
                    .andExpect(status().isConflict())
                    .andReturn();

            assertErrorCode(result, "IDENTITY_CONFLICT");
        }
    }

    // ── 3. Outbox Write Fields ──────────────────────────────────────

    @Nested
    @DisplayName("Outbox Write Fields — Integration Hub")
    class OutboxFields {

        @Test
        @DisplayName("Outbox event from dispatch contains all required fields: tenant_id, pod_id, correlation_id, schema_version >= 1, fully-qualified event_type")
        void outboxEventContainsRequiredFields() throws Exception {
            // Create a route first
            mockMvc.perform(post("/internal/v1/routes")
                            .header("X-Tenant-ID", TENANT_ID)
                            .header("X-Pod-ID", POD_ID)
                            .header("X-Request-ID", UUID.randomUUID().toString())
                            .header("X-Correlation-ID", UUID.randomUUID().toString())
                            .header("Idempotency-Key", "outbox-route-" + UUID.randomUUID())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"matchMethod\":\"*\",\"matchPathRegex\":\"/outbox-fields/.*\",\"targetUrl\":\"http://t:8080/t\",\"enabled\":true}"))
                    .andExpect(status().isCreated());

            String correlationId = "corr-outbox-" + UUID.randomUUID();

            // Dispatch to trigger outbox write
            mockMvc.perform(post("/internal/v1/dispatch")
                            .header("X-Tenant-ID", TENANT_ID)
                            .header("X-Pod-ID", POD_ID)
                            .header("X-Request-ID", UUID.randomUUID().toString())
                            .header("X-Correlation-ID", correlationId)
                            .header("Idempotency-Key", "outbox-dispatch-" + UUID.randomUUID())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"method\":\"GET\",\"path\":\"/outbox-fields/check\"}"))
                    .andExpect(status().isAccepted());

            List<OutboxEventEntity> events = outboxEventRepository.findAll();
            OutboxEventEntity matchingEvent = events.stream()
                    .filter(e -> correlationId.equals(e.getCorrelationId()))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("No outbox event with correlation_id=" + correlationId));

            assertThat(matchingEvent.getTenantId()).isEqualTo(TENANT_ID);
            assertThat(matchingEvent.getPodId()).isEqualTo(POD_ID);
            assertThat(matchingEvent.getCorrelationId()).isEqualTo(correlationId);
            assertThat(matchingEvent.getSchemaVersion()).isGreaterThanOrEqualTo(1);
            assertThat(matchingEvent.getEventType()).matches("impilo\\.integration\\..+\\.v1");
            assertThat(matchingEvent.getOccurredAt()).isNotNull();
            assertThat(matchingEvent.getPayloadJson()).isNotBlank();
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
