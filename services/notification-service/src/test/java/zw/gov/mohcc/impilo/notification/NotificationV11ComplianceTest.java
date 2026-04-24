package zw.gov.mohcc.impilo.notification;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import zw.gov.mohcc.impilo.notification.config.TestSecurityConfig;
import zw.gov.mohcc.impilo.notification.domain.OutboxEventEntity;
import zw.gov.mohcc.impilo.notification.repository.NotificationRepository;
import zw.gov.mohcc.impilo.notification.repository.OutboxEventRepository;
import zw.gov.mohcc.impilo.notification.repository.TemplateRepository;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * V1.1 Spec 2.0 compliance behavior tests for notification-service.
 *
 * Validates:
 *   1. Header enforcement (4 hard-required headers)
 *   2. Idempotency replay + conflict (IDENTITY_CONFLICT)
 *   3. Outbox write fields (tenant_id, pod_id, correlation_id, schema_version, event_type format)
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestSecurityConfig.class)
class NotificationV11ComplianceTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private TemplateRepository templateRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String TENANT_ID = "moh-zw";
    private static final String POD_ID = "national";

    @BeforeEach
    void cleanUp() {
        notificationRepository.deleteAll();
        outboxEventRepository.deleteAll();
        templateRepository.deleteAll();
    }

    // ── 1. Header Enforcement ──────────────────────────────────────

    @Nested
    @DisplayName("Header Enforcement — Notification Service")
    class HeaderEnforcement {

        @Test
        @DisplayName("Missing X-Request-ID on POST /notify returns 400 MISSING_REQUIRED_HEADER")
        void missingRequestIdOnNotifyReturns400() throws Exception {
            MvcResult result = mockMvc.perform(post("/internal/v1/notify")
                            .header("X-Tenant-ID", TENANT_ID)
                            .header("X-Pod-ID", POD_ID)
                            .header("X-Correlation-ID", "corr-1")
                            .header("Idempotency-Key", "idem-" + UUID.randomUUID())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"channel\":\"SMS\",\"to\":\"+263771234567\"}"))
                    .andExpect(status().isBadRequest())
                    .andReturn();

            assertErrorCode(result, "MISSING_REQUIRED_HEADER");
        }

        @Test
        @DisplayName("Missing X-Correlation-ID on GET /templates returns 400 MISSING_REQUIRED_HEADER")
        void missingCorrelationIdOnTemplatesReturns400() throws Exception {
            MvcResult result = mockMvc.perform(get("/internal/v1/templates/test-key")
                            .header("X-Tenant-ID", TENANT_ID)
                            .header("X-Pod-ID", POD_ID)
                            .header("X-Request-ID", "req-1"))
                    .andExpect(status().isBadRequest())
                    .andReturn();

            assertErrorCode(result, "MISSING_REQUIRED_HEADER");
        }

        @Test
        @DisplayName("Missing all four headers returns 400 with details.missing listing all four")
        void missingAllFourHeadersReturns400() throws Exception {
            MvcResult result = mockMvc.perform(post("/internal/v1/notify")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"channel\":\"SMS\",\"to\":\"+263771234567\"}"))
                    .andExpect(status().isBadRequest())
                    .andReturn();

            JsonNode error = MAPPER.readTree(result.getResponse().getContentAsString()).get("error");
            assertThat(error.get("code").asText()).isEqualTo("MISSING_REQUIRED_HEADER");
            assertThat(error.get("details").get("missing").size()).isEqualTo(4);
        }
    }

    // ── 2. Idempotency ─────────────────────────────────────────────

    @Nested
    @DisplayName("Idempotency — Notification Service")
    class Idempotency {

        @Test
        @DisplayName("Same Idempotency-Key + same body replays exact prior response on POST /templates")
        void sameKeyAndBodyReplays() throws Exception {
            String idempotencyKey = "ns-replay-" + UUID.randomUUID();
            String uniqueKey = "replay-tmpl-" + UUID.randomUUID().toString().substring(0, 8);
            String body = """
                    {"key": "%s", "channel": "SMS", "body": "Test body", "enabled": true}
                    """.formatted(uniqueKey);

            MvcResult first = mockMvc.perform(post("/internal/v1/templates")
                            .header("X-Tenant-ID", TENANT_ID)
                            .header("X-Pod-ID", POD_ID)
                            .header("X-Request-ID", UUID.randomUUID().toString())
                            .header("X-Correlation-ID", UUID.randomUUID().toString())
                            .header("Idempotency-Key", idempotencyKey)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isCreated())
                    .andReturn();

            MvcResult second = mockMvc.perform(post("/internal/v1/templates")
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
            String idempotencyKey = "ns-conflict-" + UUID.randomUUID();

            mockMvc.perform(post("/internal/v1/templates")
                            .header("X-Tenant-ID", TENANT_ID)
                            .header("X-Pod-ID", POD_ID)
                            .header("X-Request-ID", UUID.randomUUID().toString())
                            .header("X-Correlation-ID", UUID.randomUUID().toString())
                            .header("Idempotency-Key", idempotencyKey)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"key\":\"first-key\",\"channel\":\"SMS\",\"body\":\"first\",\"enabled\":true}"))
                    .andReturn();

            MvcResult result = mockMvc.perform(post("/internal/v1/templates")
                            .header("X-Tenant-ID", TENANT_ID)
                            .header("X-Pod-ID", POD_ID)
                            .header("X-Request-ID", UUID.randomUUID().toString())
                            .header("X-Correlation-ID", UUID.randomUUID().toString())
                            .header("Idempotency-Key", idempotencyKey)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"key\":\"second-key\",\"channel\":\"EMAIL\",\"body\":\"different\",\"enabled\":true}"))
                    .andExpect(status().isConflict())
                    .andReturn();

            assertErrorCode(result, "IDENTITY_CONFLICT");
        }
    }

    // ── 3. Outbox Write Fields ──────────────────────────────────────

    @Nested
    @DisplayName("Outbox Write Fields — Notification Service")
    class OutboxFields {

        @Test
        @DisplayName("Outbox event from template creation contains all required fields")
        void outboxEventFromTemplateContainsRequiredFields() throws Exception {
            String correlationId = "corr-outbox-" + UUID.randomUUID();
            String uniqueKey = "outbox-tmpl-" + UUID.randomUUID().toString().substring(0, 8);

            mockMvc.perform(post("/internal/v1/templates")
                            .header("X-Tenant-ID", TENANT_ID)
                            .header("X-Pod-ID", POD_ID)
                            .header("X-Request-ID", UUID.randomUUID().toString())
                            .header("X-Correlation-ID", correlationId)
                            .header("Idempotency-Key", "outbox-tmpl-" + UUID.randomUUID())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                {"key": "%s", "channel": "SMS", "body": "Test {{var}}", "enabled": true}
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
            assertThat(matchingEvent.getEventType()).matches("impilo\\.notify\\..+\\.v1");
            assertThat(matchingEvent.getOccurredAt()).isNotNull();
            assertThat(matchingEvent.getPayloadJson()).isNotBlank();
        }

        @Test
        @DisplayName("Outbox event from notify enqueue uses fully-qualified versioned event type")
        void outboxEventFromNotifyUsesVersionedEventType() throws Exception {
            String correlationId = "corr-notify-" + UUID.randomUUID();

            mockMvc.perform(post("/internal/v1/notify")
                            .header("X-Tenant-ID", TENANT_ID)
                            .header("X-Pod-ID", POD_ID)
                            .header("X-Request-ID", UUID.randomUUID().toString())
                            .header("X-Correlation-ID", correlationId)
                            .header("Idempotency-Key", "outbox-notify-" + UUID.randomUUID())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"channel\":\"SMS\",\"to\":\"+263771234567\",\"variables\":null}"))
                    .andExpect(status().isCreated());

            OutboxEventEntity event = outboxEventRepository.findAll().stream()
                    .filter(e -> correlationId.equals(e.getCorrelationId()))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("No outbox event for notify"));

            assertThat(event.getEventType()).isEqualTo("impilo.notify.notification.enqueued.v1");
            assertThat(event.getSchemaVersion()).isGreaterThanOrEqualTo(1);
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
