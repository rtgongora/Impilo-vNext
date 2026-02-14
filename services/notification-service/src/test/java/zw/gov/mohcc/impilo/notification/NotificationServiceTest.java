package zw.gov.mohcc.impilo.notification;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import zw.gov.mohcc.impilo.notification.domain.OutboxEventEntity;
import zw.gov.mohcc.impilo.notification.repository.OutboxEventRepository;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class NotificationServiceTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String TENANT_ID = "moh-zw";
    private static final String POD_ID = "national";

    private static String templateBody() {
        return """
                {
                  "channel": "SMS",
                  "name": "appointment-reminder",
                  "content": "Your appointment is on {{date}}.",
                  "enabled": true
                }
                """;
    }

    private static String templateBody(String channel, String name) {
        return """
                {
                  "channel": "%s",
                  "name": "%s",
                  "content": "Template content for %s",
                  "enabled": true
                }
                """.formatted(channel, name, name);
    }

    private static String notifyBody() {
        return """
                {
                  "channel": "SMS",
                  "to": "+263771234567",
                  "templateId": null,
                  "variables": {"date": "2026-03-01"}
                }
                """;
    }

    @Test
    @DisplayName("POST /internal/v1/templates persists template and returns 201 with id")
    void templateUpsertPersistsRow() throws Exception {
        MvcResult result = mockMvc.perform(post("/internal/v1/templates")
                        .header("X-Tenant-ID", TENANT_ID)
                        .header("X-Pod-ID", POD_ID)
                        .header("X-Request-ID", UUID.randomUUID().toString())
                        .header("X-Correlation-ID", UUID.randomUUID().toString())
                        .header("Idempotency-Key", "tmpl-upsert-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(templateBody()))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode root = MAPPER.readTree(result.getResponse().getContentAsString());
        assertThat(root.has("id")).isTrue();
        assertThat(root.get("id").asText()).isNotBlank();
        assertThat(root.get("channel").asText()).isEqualTo("SMS");
        assertThat(root.get("name").asText()).isEqualTo("appointment-reminder");
    }

    @Test
    @DisplayName("GET /internal/v1/templates returns all templates for tenant")
    void listTemplatesReturnsExpectedSize() throws Exception {
        String correlationId = UUID.randomUUID().toString();

        mockMvc.perform(post("/internal/v1/templates")
                        .header("X-Tenant-ID", TENANT_ID)
                        .header("X-Pod-ID", POD_ID)
                        .header("X-Request-ID", UUID.randomUUID().toString())
                        .header("X-Correlation-ID", correlationId)
                        .header("Idempotency-Key", "tmpl-list-1-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(templateBody("EMAIL", "welcome-email")))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/internal/v1/templates")
                        .header("X-Tenant-ID", TENANT_ID)
                        .header("X-Pod-ID", POD_ID)
                        .header("X-Request-ID", UUID.randomUUID().toString())
                        .header("X-Correlation-ID", correlationId)
                        .header("Idempotency-Key", "tmpl-list-2-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(templateBody("PUSH", "lab-result-ready")))
                .andExpect(status().isCreated());

        MvcResult listResult = mockMvc.perform(get("/internal/v1/templates")
                        .header("X-Tenant-ID", TENANT_ID)
                        .header("X-Pod-ID", POD_ID)
                        .header("X-Request-ID", UUID.randomUUID().toString())
                        .header("X-Correlation-ID", correlationId))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode array = MAPPER.readTree(listResult.getResponse().getContentAsString());
        assertThat(array.isArray()).isTrue();
        assertThat(array.size()).isGreaterThanOrEqualTo(2);
    }

    @Test
    @DisplayName("POST /internal/v1/notify returns 201 and persists outbox event")
    void notifySendPersistsOutboxRow() throws Exception {
        long outboxCountBefore = outboxEventRepository.count();

        mockMvc.perform(post("/internal/v1/notify")
                        .header("X-Tenant-ID", TENANT_ID)
                        .header("X-Pod-ID", POD_ID)
                        .header("X-Request-ID", UUID.randomUUID().toString())
                        .header("X-Correlation-ID", UUID.randomUUID().toString())
                        .header("Idempotency-Key", "notify-test-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(notifyBody()))
                .andExpect(status().isCreated());

        List<OutboxEventEntity> allOutboxEvents = outboxEventRepository.findAll();
        assertThat(allOutboxEvents.size()).isGreaterThan((int) outboxCountBefore);

        boolean hasSendEvent = allOutboxEvents.stream()
                .anyMatch(e -> "impilo.notify.send.requested.v1".equals(e.getEventType()));
        assertThat(hasSendEvent)
                .as("Outbox should contain 'impilo.notify.send.requested.v1'")
                .isTrue();
    }

    @Test
    @DisplayName("Same Idempotency-Key with same body replays original response")
    void idempotencyReplayReturnsSameId() throws Exception {
        String idempotencyKey = "tmpl-replay-" + UUID.randomUUID();
        String body = templateBody();

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
    @DisplayName("Same Idempotency-Key with different body returns 409 IDENTITY_CONFLICT")
    void idempotencyConflictReturns409() throws Exception {
        String idempotencyKey = "tmpl-conflict-" + UUID.randomUUID();

        mockMvc.perform(post("/internal/v1/templates")
                        .header("X-Tenant-ID", TENANT_ID)
                        .header("X-Pod-ID", POD_ID)
                        .header("X-Request-ID", UUID.randomUUID().toString())
                        .header("X-Correlation-ID", UUID.randomUUID().toString())
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(templateBody("SMS", "first-template")))
                .andExpect(status().isCreated());

        MvcResult conflict = mockMvc.perform(post("/internal/v1/templates")
                        .header("X-Tenant-ID", TENANT_ID)
                        .header("X-Pod-ID", POD_ID)
                        .header("X-Request-ID", UUID.randomUUID().toString())
                        .header("X-Correlation-ID", UUID.randomUUID().toString())
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(templateBody("EMAIL", "different-template")))
                .andExpect(status().isConflict())
                .andReturn();

        JsonNode root = MAPPER.readTree(conflict.getResponse().getContentAsString());
        assertThat(root.has("error")).isTrue();
        assertThat(root.get("error").get("code").asText()).isEqualTo("IDENTITY_CONFLICT");
    }
}
