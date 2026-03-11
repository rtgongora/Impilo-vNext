package zw.gov.mohcc.impilo.rules;

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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import zw.gov.mohcc.impilo.rules.domain.OutboxEventEntity;
import zw.gov.mohcc.impilo.rules.repository.EvaluationAuditRepository;
import zw.gov.mohcc.impilo.rules.repository.OutboxEventRepository;
import zw.gov.mohcc.impilo.rules.repository.RuleActivationRepository;
import zw.gov.mohcc.impilo.rules.repository.RuleVersionRepository;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * V1.1 Spec 2.0 compliance behavior tests for rules-service.
 *
 * Validates:
 *   1. Header enforcement (4 hard-required headers)
 *   2. Idempotency replay + conflict (IDENTITY_CONFLICT)
 *   3. Outbox write fields (tenant_id, pod_id, correlation_id, schema_version, event_type format)
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RulesV11ComplianceTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private RuleVersionRepository versionRepository;

    @Autowired
    private RuleActivationRepository activationRepository;

    @Autowired
    private EvaluationAuditRepository auditRepository;

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String TENANT_ID = "moh-zw";
    private static final String POD_ID = "national";

    @BeforeEach
    void cleanUp() {
        auditRepository.deleteAll();
        activationRepository.deleteAll();
        versionRepository.deleteAll();
        outboxEventRepository.deleteAll();
    }

    // ── 1. Header Enforcement ──────────────────────────────────────

    @Nested
    @DisplayName("Header Enforcement — Rules Service")
    class HeaderEnforcement {

        @Test
        @DisplayName("Missing X-Request-ID on GET /rules returns 400 MISSING_REQUIRED_HEADER")
        void missingRequestIdReturns400() throws Exception {
            MvcResult result = mockMvc.perform(get("/internal/v1/rules")
                            .header("X-Tenant-ID", TENANT_ID)
                            .header("X-Pod-ID", POD_ID)
                            .header("X-Correlation-ID", "corr-1"))
                    .andExpect(status().isBadRequest())
                    .andReturn();

            assertErrorCode(result, "MISSING_REQUIRED_HEADER");
        }

        @Test
        @DisplayName("Missing X-Correlation-ID on POST /rules returns 400 MISSING_REQUIRED_HEADER")
        void missingCorrelationIdReturns400() throws Exception {
            MvcResult result = mockMvc.perform(post("/internal/v1/rules")
                            .header("X-Tenant-ID", TENANT_ID)
                            .header("X-Pod-ID", POD_ID)
                            .header("X-Request-ID", "req-1")
                            .header("Idempotency-Key", "idem-" + UUID.randomUUID())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"key\":\"test\",\"name\":\"Test Rule\"}"))
                    .andExpect(status().isBadRequest())
                    .andReturn();

            assertErrorCode(result, "MISSING_REQUIRED_HEADER");
        }

        @Test
        @DisplayName("Missing all four headers on POST /rules returns 400 with details.missing array of size 4")
        void missingAllFourHeadersReturns400() throws Exception {
            MvcResult result = mockMvc.perform(post("/internal/v1/rules")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"key\":\"test\",\"name\":\"Test Rule\"}"))
                    .andExpect(status().isBadRequest())
                    .andReturn();

            JsonNode error = MAPPER.readTree(result.getResponse().getContentAsString()).get("error");
            assertThat(error.get("code").asText()).isEqualTo("MISSING_REQUIRED_HEADER");
            assertThat(error.get("details").get("missing").size()).isEqualTo(4);
        }
    }

    // ── 2. Idempotency ─────────────────────────────────────────────

    @Nested
    @DisplayName("Idempotency — Rules Service")
    class Idempotency {

        @Test
        @DisplayName("Same Idempotency-Key + same body replays exact prior response on POST /rules")
        void sameKeyAndBodyReplays() throws Exception {
            String idempotencyKey = "rs-replay-" + UUID.randomUUID();
            String uniqueKey = "replay-rule-" + UUID.randomUUID().toString().substring(0, 8);
            String body = """
                    {"key": "%s", "name": "Replay Rule"}
                    """.formatted(uniqueKey);

            MvcResult first = mockMvc.perform(post("/internal/v1/rules")
                            .header("X-Tenant-ID", TENANT_ID)
                            .header("X-Pod-ID", POD_ID)
                            .header("X-Request-ID", UUID.randomUUID().toString())
                            .header("X-Correlation-ID", UUID.randomUUID().toString())
                            .header("Idempotency-Key", idempotencyKey)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isCreated())
                    .andReturn();

            MvcResult second = mockMvc.perform(post("/internal/v1/rules")
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
            String idempotencyKey = "rs-conflict-" + UUID.randomUUID();

            mockMvc.perform(post("/internal/v1/rules")
                            .header("X-Tenant-ID", TENANT_ID)
                            .header("X-Pod-ID", POD_ID)
                            .header("X-Request-ID", UUID.randomUUID().toString())
                            .header("X-Correlation-ID", UUID.randomUUID().toString())
                            .header("Idempotency-Key", idempotencyKey)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"key\":\"conflict-a\",\"name\":\"Rule A\"}"))
                    .andReturn();

            MvcResult result = mockMvc.perform(post("/internal/v1/rules")
                            .header("X-Tenant-ID", TENANT_ID)
                            .header("X-Pod-ID", POD_ID)
                            .header("X-Request-ID", UUID.randomUUID().toString())
                            .header("X-Correlation-ID", UUID.randomUUID().toString())
                            .header("Idempotency-Key", idempotencyKey)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"key\":\"conflict-b\",\"name\":\"Rule B\"}"))
                    .andExpect(status().isConflict())
                    .andReturn();

            assertErrorCode(result, "IDENTITY_CONFLICT");
        }
    }

    // ── 3. Outbox Write Fields ──────────────────────────────────────

    @Nested
    @DisplayName("Outbox Write Fields — Rules Service")
    class OutboxFields {

        @Test
        @DisplayName("Outbox event from rule creation contains all required fields: tenant_id, pod_id, correlation_id, schema_version >= 1, fully-qualified event_type")
        void outboxEventContainsRequiredFields() throws Exception {
            String correlationId = "corr-outbox-" + UUID.randomUUID();
            String uniqueKey = "outbox-rule-" + UUID.randomUUID().toString().substring(0, 8);

            mockMvc.perform(post("/internal/v1/rules")
                            .header("X-Tenant-ID", TENANT_ID)
                            .header("X-Pod-ID", POD_ID)
                            .header("X-Request-ID", UUID.randomUUID().toString())
                            .header("X-Correlation-ID", correlationId)
                            .header("Idempotency-Key", "outbox-rule-" + UUID.randomUUID())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                {"key": "%s", "name": "Outbox Rule"}
                                """.formatted(uniqueKey)))
                    .andExpect(status().isCreated());

            List<OutboxEventEntity> events = outboxEventRepository.findAll();
            OutboxEventEntity matchingEvent = events.stream()
                    .filter(e -> correlationId.equals(e.getCorrelationId()))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("No outbox event with correlation_id=" + correlationId));

            assertThat(matchingEvent.getTenantId()).isEqualTo(TENANT_ID);
            assertThat(matchingEvent.getPodId()).isEqualTo(POD_ID);
            assertThat(matchingEvent.getCorrelationId()).isEqualTo(correlationId);
            assertThat(matchingEvent.getSchemaVersion()).isGreaterThanOrEqualTo(1);
            assertThat(matchingEvent.getEventType()).matches("impilo\\.rules\\..+\\.v1");
            assertThat(matchingEvent.getOccurredAt()).isNotNull();
            assertThat(matchingEvent.getPayloadJson()).isNotBlank();
        }

        @Test
        @DisplayName("Rule lifecycle emits fully-qualified versioned event types in outbox")
        void lifecycleEmitsVersionedEventTypes() throws Exception {
            String correlationId = "corr-lifecycle-" + UUID.randomUUID();
            String key = "lifecycle-" + UUID.randomUUID().toString().substring(0, 8);

            // Create rule
            mockMvc.perform(post("/internal/v1/rules")
                            .header("X-Tenant-ID", TENANT_ID)
                            .header("X-Pod-ID", POD_ID)
                            .header("X-Request-ID", UUID.randomUUID().toString())
                            .header("X-Correlation-ID", correlationId)
                            .header("Idempotency-Key", "lifecycle-create-" + UUID.randomUUID())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"key\":\"%s\",\"name\":\"Lifecycle Rule\"}".formatted(key)))
                    .andExpect(status().isCreated());

            // Create version
            mockMvc.perform(post("/internal/v1/rules/" + key + "/versions")
                            .header("X-Tenant-ID", TENANT_ID)
                            .header("X-Pod-ID", POD_ID)
                            .header("X-Request-ID", UUID.randomUUID().toString())
                            .header("X-Correlation-ID", correlationId)
                            .header("Idempotency-Key", "lifecycle-ver-" + UUID.randomUUID())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"dslText\":\"facts.x > 0\"}"))
                    .andExpect(status().isCreated());

            List<OutboxEventEntity> events = outboxEventRepository.findAll();
            List<String> eventTypes = events.stream()
                    .filter(e -> correlationId.equals(e.getCorrelationId()))
                    .map(OutboxEventEntity::getEventType)
                    .toList();

            assertThat(eventTypes).contains(
                    "impilo.rules.rule.created.v1",
                    "impilo.rules.version.created.v1"
            );

            // Verify all events have schema_version >= 1
            events.stream()
                    .filter(e -> correlationId.equals(e.getCorrelationId()))
                    .forEach(e -> assertThat(e.getSchemaVersion())
                            .as("schema_version for %s", e.getEventType())
                            .isGreaterThanOrEqualTo(1));
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
