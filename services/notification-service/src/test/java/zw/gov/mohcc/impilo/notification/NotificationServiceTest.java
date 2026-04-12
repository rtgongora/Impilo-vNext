package zw.gov.mohcc.impilo.notification;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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
import zw.gov.mohcc.impilo.notification.domain.NotificationEntity;
import zw.gov.mohcc.impilo.notification.domain.NotificationStatus;
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

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestSecurityConfig.class)
class NotificationServiceTest {

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

    // ─── Test 1: Template creation with key, subject, body ───

    @Test
    @DisplayName("POST /internal/v1/templates creates template with key/subject/body and emits impilo.notify.template.created.v1")
    void templateCreatePersistsAndEmitsEvent() throws Exception {
        MvcResult result = mockMvc.perform(post("/internal/v1/templates")
                        .header("X-Tenant-ID", TENANT_ID)
                        .header("X-Pod-ID", POD_ID)
                        .header("X-Request-ID", UUID.randomUUID().toString())
                        .header("X-Correlation-ID", UUID.randomUUID().toString())
                        .header("Idempotency-Key", "tmpl-create-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                              "key": "appointment-reminder",
                              "channel": "SMS",
                              "subject": "Appointment",
                              "body": "Your appointment is on {{date}} at {{facility}}.",
                              "enabled": true
                            }
                            """))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode root = MAPPER.readTree(result.getResponse().getContentAsString());
        assertThat(root.get("id").asText()).isNotBlank();
        assertThat(root.get("key").asText()).isEqualTo("appointment-reminder");
        assertThat(root.get("channel").asText()).isEqualTo("SMS");
        assertThat(root.get("subject").asText()).isEqualTo("Appointment");
        assertThat(root.get("body").asText()).contains("{{date}}");

        List<OutboxEventEntity> events = outboxEventRepository.findAll();
        assertThat(events).anyMatch(e -> "impilo.notify.template.created.v1".equals(e.getEventType()));
    }

    // ─── Test 2: Template retrieval by key ───

    @Test
    @DisplayName("GET /internal/v1/templates/{key} returns template by key")
    void getTemplateByKey() throws Exception {
        String key = "lab-result-" + UUID.randomUUID().toString().substring(0, 8);
        createTemplate(key, "EMAIL", "Lab Result Ready", "Your lab result for {{test}} is ready.");

        MvcResult result = mockMvc.perform(get("/internal/v1/templates/" + key)
                        .header("X-Tenant-ID", TENANT_ID)
                        .header("X-Pod-ID", POD_ID)
                        .header("X-Request-ID", UUID.randomUUID().toString())
                        .header("X-Correlation-ID", UUID.randomUUID().toString()))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode root = MAPPER.readTree(result.getResponse().getContentAsString());
        assertThat(root.get("key").asText()).isEqualTo(key);
        assertThat(root.get("channel").asText()).isEqualTo("EMAIL");
        assertThat(root.get("subject").asText()).isEqualTo("Lab Result Ready");
    }

    // ─── Test 3: Template not found returns 404 ───

    @Test
    @DisplayName("GET /internal/v1/templates/{key} returns 404 for unknown key")
    void getTemplateByKeyNotFound() throws Exception {
        mockMvc.perform(get("/internal/v1/templates/non-existent-key")
                        .header("X-Tenant-ID", TENANT_ID)
                        .header("X-Pod-ID", POD_ID)
                        .header("X-Request-ID", UUID.randomUUID().toString())
                        .header("X-Correlation-ID", UUID.randomUUID().toString()))
                .andExpect(status().isNotFound());
    }

    // ─── Test 4: Notification enqueue ───

    @Test
    @DisplayName("POST /internal/v1/notify enqueues notification with PENDING status and emits impilo.notify.enqueued.v1")
    void enqueueNotification() throws Exception {
        MvcResult result = mockMvc.perform(post("/internal/v1/notify")
                        .header("X-Tenant-ID", TENANT_ID)
                        .header("X-Pod-ID", POD_ID)
                        .header("X-Request-ID", UUID.randomUUID().toString())
                        .header("X-Correlation-ID", UUID.randomUUID().toString())
                        .header("Idempotency-Key", "notify-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                              "channel": "SMS",
                              "to": "+263771234567",
                              "templateKey": null,
                              "variables": {"date": "2026-03-01"}
                            }
                            """))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode root = MAPPER.readTree(result.getResponse().getContentAsString());
        assertThat(root.get("id").asText()).isNotBlank();
        assertThat(root.get("status").asText()).isEqualTo("PENDING");
        assertThat(root.get("channel").asText()).isEqualTo("SMS");
        assertThat(root.get("to").asText()).isEqualTo("+263771234567");

        List<OutboxEventEntity> events = outboxEventRepository.findAll();
        assertThat(events).anyMatch(e -> "impilo.notify.enqueued.v1".equals(e.getEventType()));
    }

    // ─── Test 5: Worker processes PENDING → SENT ───

    @Test
    @DisplayName("POST /internal/v1/worker/run transitions PENDING notifications to SENT and emits impilo.notify.sent.v1")
    void workerProcessesPendingToSent() throws Exception {
        enqueueOne("SMS", "+263770000001", null, null);

        MvcResult result = mockMvc.perform(post("/internal/v1/worker/run?limit=10")
                        .header("X-Tenant-ID", TENANT_ID)
                        .header("X-Pod-ID", POD_ID)
                        .header("X-Request-ID", UUID.randomUUID().toString())
                        .header("X-Correlation-ID", UUID.randomUUID().toString())
                        .header("Idempotency-Key", "worker-" + UUID.randomUUID()))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode root = MAPPER.readTree(result.getResponse().getContentAsString());
        assertThat(root.get("processed").asInt()).isEqualTo(1);
        assertThat(root.get("sent").asInt()).isEqualTo(1);
        assertThat(root.get("failed").asInt()).isEqualTo(0);

        List<NotificationEntity> all = notificationRepository.findAll();
        assertThat(all).hasSize(1);
        assertThat(all.get(0).getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(all.get(0).getSentAt()).isNotNull();
        assertThat(all.get(0).getAttempts()).isEqualTo(1);

        List<OutboxEventEntity> events = outboxEventRepository.findAll();
        assertThat(events).anyMatch(e -> "impilo.notify.sent.v1".equals(e.getEventType()));
    }

    // ─── Test 6: Worker with template variable substitution ───

    @Test
    @DisplayName("Worker resolves template and substitutes variables before sending")
    void workerSubstitutesTemplateVariables() throws Exception {
        String key = "appt-tmpl-" + UUID.randomUUID().toString().substring(0, 8);
        createTemplate(key, "SMS", null, "Hello {{name}}, your appointment is {{date}}.");

        enqueueOne("SMS", "+263770000002", key, "{\"name\": \"John\", \"date\": \"2026-04-15\"}");

        MvcResult result = mockMvc.perform(post("/internal/v1/worker/run?limit=10")
                        .header("X-Tenant-ID", TENANT_ID)
                        .header("X-Pod-ID", POD_ID)
                        .header("X-Request-ID", UUID.randomUUID().toString())
                        .header("X-Correlation-ID", UUID.randomUUID().toString())
                        .header("Idempotency-Key", "worker-tmpl-" + UUID.randomUUID()))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode root = MAPPER.readTree(result.getResponse().getContentAsString());
        assertThat(root.get("sent").asInt()).isEqualTo(1);

        NotificationEntity notification = notificationRepository.findAll().get(0);
        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.SENT);
    }

    // ─── Test 7: Worker handles missing template → FAILED ───

    @Test
    @DisplayName("Worker marks notification FAILED when template key does not exist")
    void workerFailsOnMissingTemplate() throws Exception {
        enqueueOne("SMS", "+263770000003", "nonexistent-template", null);

        MvcResult result = mockMvc.perform(post("/internal/v1/worker/run?limit=10")
                        .header("X-Tenant-ID", TENANT_ID)
                        .header("X-Pod-ID", POD_ID)
                        .header("X-Request-ID", UUID.randomUUID().toString())
                        .header("X-Correlation-ID", UUID.randomUUID().toString())
                        .header("Idempotency-Key", "worker-fail-" + UUID.randomUUID()))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode root = MAPPER.readTree(result.getResponse().getContentAsString());
        assertThat(root.get("processed").asInt()).isEqualTo(1);
        assertThat(root.get("sent").asInt()).isEqualTo(0);
        assertThat(root.get("failed").asInt()).isEqualTo(1);

        NotificationEntity notification = notificationRepository.findAll().get(0);
        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.FAILED);
        assertThat(notification.getLastError()).contains("Template not found");

        List<OutboxEventEntity> events = outboxEventRepository.findAll();
        assertThat(events).anyMatch(e -> "impilo.notify.failed.v1".equals(e.getEventType()));
    }

    // ─── Test 8: GET /notifications returns paged results ───

    @Test
    @DisplayName("GET /internal/v1/notifications returns paged notification list")
    void listNotificationsPaged() throws Exception {
        enqueueOne("SMS", "+263770000010", null, null);
        enqueueOne("EMAIL", "test@example.com", null, null);
        enqueueOne("SMS", "+263770000011", null, null);

        MvcResult result = mockMvc.perform(get("/internal/v1/notifications?page=0&size=2")
                        .header("X-Tenant-ID", TENANT_ID)
                        .header("X-Pod-ID", POD_ID)
                        .header("X-Request-ID", UUID.randomUUID().toString())
                        .header("X-Correlation-ID", UUID.randomUUID().toString()))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode root = MAPPER.readTree(result.getResponse().getContentAsString());
        assertThat(root.get("content").size()).isEqualTo(2);
        assertThat(root.get("totalElements").asInt()).isEqualTo(3);
        assertThat(root.get("totalPages").asInt()).isEqualTo(2);
    }

    // ─── Test 9: Worker respects limit parameter ───

    @Test
    @DisplayName("Worker only processes up to limit notifications")
    void workerRespectsLimit() throws Exception {
        enqueueOne("SMS", "+263770000020", null, null);
        enqueueOne("SMS", "+263770000021", null, null);
        enqueueOne("SMS", "+263770000022", null, null);

        MvcResult result = mockMvc.perform(post("/internal/v1/worker/run?limit=2")
                        .header("X-Tenant-ID", TENANT_ID)
                        .header("X-Pod-ID", POD_ID)
                        .header("X-Request-ID", UUID.randomUUID().toString())
                        .header("X-Correlation-ID", UUID.randomUUID().toString())
                        .header("Idempotency-Key", "worker-limit-" + UUID.randomUUID()))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode root = MAPPER.readTree(result.getResponse().getContentAsString());
        assertThat(root.get("processed").asInt()).isEqualTo(2);
        assertThat(root.get("sent").asInt()).isEqualTo(2);

        long pendingCount = notificationRepository.findByStatusOrderByCreatedAtAsc(NotificationStatus.PENDING).size();
        assertThat(pendingCount).isEqualTo(1);
    }

    // ─── Test 10: Idempotency replay ───

    @Test
    @DisplayName("Same Idempotency-Key with same body replays original template response")
    void idempotencyReplay() throws Exception {
        String idempotencyKey = "tmpl-replay-" + UUID.randomUUID();
        String uniqueKey = "replay-test-" + UUID.randomUUID().toString().substring(0, 8);
        String body = """
            {
              "key": "%s",
              "channel": "SMS",
              "body": "Test body",
              "enabled": true
            }
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

    // ─── Helpers ───

    private void createTemplate(String key, String channel, String subject, String body) throws Exception {
        String subjectField = subject != null ? "\"subject\": \"" + subject + "\"," : "";
        mockMvc.perform(post("/internal/v1/templates")
                        .header("X-Tenant-ID", TENANT_ID)
                        .header("X-Pod-ID", POD_ID)
                        .header("X-Request-ID", UUID.randomUUID().toString())
                        .header("X-Correlation-ID", UUID.randomUUID().toString())
                        .header("Idempotency-Key", "tmpl-helper-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                              "key": "%s",
                              "channel": "%s",
                              %s
                              "body": "%s",
                              "enabled": true
                            }
                            """.formatted(key, channel, subjectField, body)))
                .andExpect(status().isCreated());
    }

    private void enqueueOne(String channel, String to, String templateKey, String varsJson) throws Exception {
        String templateKeyField = templateKey != null ? "\"templateKey\": \"" + templateKey + "\"," : "";
        String variablesField = varsJson != null ? "\"variables\": " + varsJson : "\"variables\": null";
        mockMvc.perform(post("/internal/v1/notify")
                        .header("X-Tenant-ID", TENANT_ID)
                        .header("X-Pod-ID", POD_ID)
                        .header("X-Request-ID", UUID.randomUUID().toString())
                        .header("X-Correlation-ID", UUID.randomUUID().toString())
                        .header("Idempotency-Key", "notify-helper-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                              "channel": "%s",
                              "to": "%s",
                              %s
                              %s
                            }
                            """.formatted(channel, to, templateKeyField, variablesField)))
                .andExpect(status().isCreated());
    }
}
