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
import zw.gov.mohcc.impilo.notification.repository.DeliveryReceiptRepository;
import zw.gov.mohcc.impilo.notification.repository.NotificationRepository;
import zw.gov.mohcc.impilo.notification.repository.OutboxEventRepository;
import zw.gov.mohcc.impilo.notification.repository.TemplateRepository;
import zw.gov.mohcc.impilo.notification.repository.TemplateVersionRepository;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for Notification Service extensions — template lifecycle
 * (publish/retire), template versioning, and delivery receipts.
 *
 * Uses H2 in-memory database via the "test" profile with create-drop DDL
 * and Flyway disabled.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestSecurityConfig.class)
class NotificationExtensionsTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TemplateRepository templateRepository;

    @Autowired
    private TemplateVersionRepository templateVersionRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private DeliveryReceiptRepository deliveryReceiptRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String TENANT_ID = "moh-zw";
    private static final String POD_ID = "national";

    @BeforeEach
    void cleanUp() {
        deliveryReceiptRepository.deleteAll();
        notificationRepository.deleteAll();
        templateVersionRepository.deleteAll();
        outboxEventRepository.deleteAll();
        templateRepository.deleteAll();
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private String createTemplateAndGetId(String key, String channel, String subject, String body) throws Exception {
        String subjectField = subject != null ? "\"subject\": \"" + subject + "\"," : "";

        MvcResult result = mockMvc.perform(post("/internal/v1/templates")
                        .header("X-Tenant-ID", TENANT_ID)
                        .header("X-Pod-ID", POD_ID)
                        .header("X-Request-ID", UUID.randomUUID().toString())
                        .header("X-Correlation-ID", UUID.randomUUID().toString())
                        .header("Idempotency-Key", "tmpl-ext-" + UUID.randomUUID())
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
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode root = MAPPER.readTree(result.getResponse().getContentAsString());
        return root.get("id").asText();
    }

    private String enqueueNotificationAndGetId(String channel, String to) throws Exception {
        MvcResult result = mockMvc.perform(post("/internal/v1/notify")
                        .header("X-Tenant-ID", TENANT_ID)
                        .header("X-Pod-ID", POD_ID)
                        .header("X-Request-ID", UUID.randomUUID().toString())
                        .header("X-Correlation-ID", UUID.randomUUID().toString())
                        .header("Idempotency-Key", "notify-ext-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                              "channel": "%s",
                              "to": "%s",
                              "templateKey": null,
                              "variables": null
                            }
                            """.formatted(channel, to)))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode root = MAPPER.readTree(result.getResponse().getContentAsString());
        return root.get("id").asText();
    }

    private void recordReceipt(String notificationId, String channel, String recipientAddr,
                                String status, String failureReason) throws Exception {
        String failField = failureReason != null
                ? "\"failureReason\": \"" + failureReason + "\""
                : "\"failureReason\": null";

        String body = """
                {
                  "notificationId": "%s",
                  "channel": "%s",
                  "recipientAddr": "%s",
                  "status": "%s",
                  %s
                }
                """.formatted(notificationId, channel, recipientAddr, status, failField);

        mockMvc.perform(post("/internal/v1/delivery-receipts")
                        .header("X-Tenant-ID", TENANT_ID)
                        .header("X-Pod-ID", POD_ID)
                        .header("X-Request-ID", UUID.randomUUID().toString())
                        .header("X-Correlation-ID", UUID.randomUUID().toString())
                        .header("Idempotency-Key", "dr-helper-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());
    }

    // ── Test 1: Create template then publish it ─────────────────────────

    @Test
    @DisplayName("POST /internal/v1/templates/{id}/publish transitions template to PUBLISHED status")
    void publishTemplate() throws Exception {
        String key = "pub-test-" + UUID.randomUUID().toString().substring(0, 8);
        String templateId = createTemplateAndGetId(key, "SMS", null,
                "Your code is {{code}}.");

        // Publish the template
        MvcResult result = mockMvc.perform(post("/internal/v1/templates/" + templateId + "/publish")
                        .header("X-Tenant-ID", TENANT_ID)
                        .header("X-Pod-ID", POD_ID)
                        .header("X-Request-ID", UUID.randomUUID().toString())
                        .header("X-Correlation-ID", UUID.randomUUID().toString())
                        .header("Idempotency-Key", "tmpl-publish-" + UUID.randomUUID()))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode root = MAPPER.readTree(result.getResponse().getContentAsString());
        assertThat(root.get("id").asText()).isEqualTo(templateId);
        assertThat(root.get("key").asText()).isEqualTo(key);
        assertThat(root.get("status").asText()).isEqualTo("PUBLISHED");
    }

    // ── Test 2: Publish then retire lifecycle ───────────────────────────

    @Test
    @DisplayName("Template lifecycle: DRAFT -> PUBLISHED -> RETIRED")
    void publishThenRetireLifecycle() throws Exception {
        String key = "lifecycle-" + UUID.randomUUID().toString().substring(0, 8);
        String templateId = createTemplateAndGetId(key, "EMAIL", "Test Subject",
                "Lifecycle test body.");

        // Verify initial status is DRAFT
        MvcResult getResult = mockMvc.perform(get("/internal/v1/templates/" + key)
                        .header("X-Tenant-ID", TENANT_ID)
                        .header("X-Pod-ID", POD_ID)
                        .header("X-Request-ID", UUID.randomUUID().toString())
                        .header("X-Correlation-ID", UUID.randomUUID().toString()))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode draft = MAPPER.readTree(getResult.getResponse().getContentAsString());
        assertThat(draft.get("status").asText()).isEqualTo("DRAFT");

        // Publish
        MvcResult pubResult = mockMvc.perform(post("/internal/v1/templates/" + templateId + "/publish")
                        .header("X-Tenant-ID", TENANT_ID)
                        .header("X-Pod-ID", POD_ID)
                        .header("X-Request-ID", UUID.randomUUID().toString())
                        .header("X-Correlation-ID", UUID.randomUUID().toString())
                        .header("Idempotency-Key", "tmpl-pub-life-" + UUID.randomUUID()))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode published = MAPPER.readTree(pubResult.getResponse().getContentAsString());
        assertThat(published.get("status").asText()).isEqualTo("PUBLISHED");

        // Retire
        MvcResult retResult = mockMvc.perform(post("/internal/v1/templates/" + templateId + "/retire")
                        .header("X-Tenant-ID", TENANT_ID)
                        .header("X-Pod-ID", POD_ID)
                        .header("X-Request-ID", UUID.randomUUID().toString())
                        .header("X-Correlation-ID", UUID.randomUUID().toString())
                        .header("Idempotency-Key", "tmpl-retire-" + UUID.randomUUID()))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode retired = MAPPER.readTree(retResult.getResponse().getContentAsString());
        assertThat(retired.get("status").asText()).isEqualTo("RETIRED");
        assertThat(retired.get("id").asText()).isEqualTo(templateId);
    }

    // ── Test 3: Create template version ──────────────────────────────────

    @Test
    @DisplayName("POST /internal/v1/templates/{id}/versions creates a new version with incremented number")
    void createTemplateVersion() throws Exception {
        String key = "version-test-" + UUID.randomUUID().toString().substring(0, 8);
        String templateId = createTemplateAndGetId(key, "SMS", null,
                "Version 1 body.");

        // Create a new version
        String versionBody = """
                {
                  "content": "Version 2 body with {{name}} placeholder.",
                  "subject": "Updated Subject",
                  "changelog": "Added name placeholder support"
                }
                """;

        MvcResult result = mockMvc.perform(post("/internal/v1/templates/" + templateId + "/versions")
                        .header("X-Tenant-ID", TENANT_ID)
                        .header("X-Pod-ID", POD_ID)
                        .header("X-Request-ID", UUID.randomUUID().toString())
                        .header("X-Correlation-ID", UUID.randomUUID().toString())
                        .header("Idempotency-Key", "ver-create-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(versionBody))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode root = MAPPER.readTree(result.getResponse().getContentAsString());
        assertThat(root.get("id").asText()).isNotBlank();
        assertThat(root.get("templateId").asText()).isEqualTo(templateId);
        assertThat(root.get("content").asText()).contains("Version 2 body");
        assertThat(root.get("subject").asText()).isEqualTo("Updated Subject");
        assertThat(root.get("changelog").asText()).isEqualTo("Added name placeholder support");
        assertThat(root.get("version").asInt()).isGreaterThanOrEqualTo(2);
    }

    // ── Test 4: List template versions returns history ───────────────────

    @Test
    @DisplayName("GET /internal/v1/templates/{id}/versions returns version history")
    void listTemplateVersions() throws Exception {
        String key = "ver-list-" + UUID.randomUUID().toString().substring(0, 8);
        String templateId = createTemplateAndGetId(key, "EMAIL", "Subject",
                "Initial body.");

        // Create two additional versions
        for (int i = 2; i <= 3; i++) {
            String versionBody = """
                    {
                      "content": "Version %d content.",
                      "changelog": "Change for version %d"
                    }
                    """.formatted(i, i);

            mockMvc.perform(post("/internal/v1/templates/" + templateId + "/versions")
                            .header("X-Tenant-ID", TENANT_ID)
                            .header("X-Pod-ID", POD_ID)
                            .header("X-Request-ID", UUID.randomUUID().toString())
                            .header("X-Correlation-ID", UUID.randomUUID().toString())
                            .header("Idempotency-Key", "ver-list-" + i + "-" + UUID.randomUUID())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(versionBody))
                    .andExpect(status().isCreated());
        }

        MvcResult result = mockMvc.perform(get("/internal/v1/templates/" + templateId + "/versions")
                        .header("X-Tenant-ID", TENANT_ID)
                        .header("X-Pod-ID", POD_ID)
                        .header("X-Request-ID", UUID.randomUUID().toString())
                        .header("X-Correlation-ID", UUID.randomUUID().toString()))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode root = MAPPER.readTree(result.getResponse().getContentAsString());
        assertThat(root.isArray()).isTrue();
        // Should have at least 3 versions (initial + 2 created)
        assertThat(root.size()).isGreaterThanOrEqualTo(3);
    }

    // ── Test 5: Record delivery receipt ──────────────────────────────────

    @Test
    @DisplayName("POST /internal/v1/delivery-receipts records a delivery receipt and returns 201")
    void recordDeliveryReceipt() throws Exception {
        String notificationId = enqueueNotificationAndGetId("SMS", "+263771234567");

        String body = """
                {
                  "notificationId": "%s",
                  "channel": "SMS",
                  "recipientAddr": "+263771234567",
                  "status": "DELIVERED",
                  "providerRef": "PROV-REF-001"
                }
                """.formatted(notificationId);

        MvcResult result = mockMvc.perform(post("/internal/v1/delivery-receipts")
                        .header("X-Tenant-ID", TENANT_ID)
                        .header("X-Pod-ID", POD_ID)
                        .header("X-Request-ID", UUID.randomUUID().toString())
                        .header("X-Correlation-ID", UUID.randomUUID().toString())
                        .header("Idempotency-Key", "dr-create-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode root = MAPPER.readTree(result.getResponse().getContentAsString());
        assertThat(root.get("id").asText()).isNotBlank();
        assertThat(root.get("notificationId").asText()).isEqualTo(notificationId);
        assertThat(root.get("channel").asText()).isEqualTo("SMS");
        assertThat(root.get("recipientAddr").asText()).isEqualTo("+263771234567");
        assertThat(root.get("status").asText()).isEqualTo("DELIVERED");
        assertThat(root.get("providerRef").asText()).isEqualTo("PROV-REF-001");
        assertThat(root.get("createdAt").asText()).isNotBlank();
    }

    // ── Test 6: Record delivery receipt with failure reason ──────────────

    @Test
    @DisplayName("POST /internal/v1/delivery-receipts with failure reason stores the error details")
    void recordDeliveryReceiptWithFailure() throws Exception {
        String notificationId = enqueueNotificationAndGetId("EMAIL", "user@example.com");

        String body = """
                {
                  "notificationId": "%s",
                  "channel": "EMAIL",
                  "recipientAddr": "user@example.com",
                  "status": "FAILED",
                  "failureReason": "Mailbox full"
                }
                """.formatted(notificationId);

        MvcResult result = mockMvc.perform(post("/internal/v1/delivery-receipts")
                        .header("X-Tenant-ID", TENANT_ID)
                        .header("X-Pod-ID", POD_ID)
                        .header("X-Request-ID", UUID.randomUUID().toString())
                        .header("X-Correlation-ID", UUID.randomUUID().toString())
                        .header("Idempotency-Key", "dr-fail-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode root = MAPPER.readTree(result.getResponse().getContentAsString());
        assertThat(root.get("status").asText()).isEqualTo("FAILED");
        assertThat(root.get("failureReason").asText()).isEqualTo("Mailbox full");
    }

    // ── Test 7: List delivery receipts by status ─────────────────────────

    @Test
    @DisplayName("GET /internal/v1/delivery-receipts?status=DELIVERED returns paged receipts filtered by status")
    void listDeliveryReceiptsByStatus() throws Exception {
        // Create notifications and receipts
        String notifId1 = enqueueNotificationAndGetId("SMS", "+263770001111");
        String notifId2 = enqueueNotificationAndGetId("SMS", "+263770002222");
        String notifId3 = enqueueNotificationAndGetId("SMS", "+263770003333");

        // Record two DELIVERED and one FAILED receipt
        recordReceipt(notifId1, "SMS", "+263770001111", "DELIVERED", null);
        recordReceipt(notifId2, "SMS", "+263770002222", "DELIVERED", null);
        recordReceipt(notifId3, "SMS", "+263770003333", "FAILED", "Number unreachable");

        // List DELIVERED receipts
        MvcResult result = mockMvc.perform(get("/internal/v1/delivery-receipts")
                        .param("status", "DELIVERED")
                        .param("page", "0")
                        .param("size", "10")
                        .header("X-Tenant-ID", TENANT_ID)
                        .header("X-Pod-ID", POD_ID)
                        .header("X-Request-ID", UUID.randomUUID().toString())
                        .header("X-Correlation-ID", UUID.randomUUID().toString()))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode root = MAPPER.readTree(result.getResponse().getContentAsString());
        assertThat(root.get("content").isArray()).isTrue();
        assertThat(root.get("content").size()).isEqualTo(2);
        assertThat(root.get("totalElements").asLong()).isEqualTo(2);

        // All results should be DELIVERED
        for (JsonNode receipt : root.get("content")) {
            assertThat(receipt.get("status").asText()).isEqualTo("DELIVERED");
        }
    }

    // ── Test 8: Get delivery receipts by notification ID ─────────────────

    @Test
    @DisplayName("GET /internal/v1/delivery-receipts?notificationId=X returns receipts for that notification")
    void getDeliveryReceiptsByNotificationId() throws Exception {
        String notifId = enqueueNotificationAndGetId("SMS", "+263770009999");

        // Record multiple receipts for the same notification
        recordReceipt(notifId, "SMS", "+263770009999", "PENDING", null);
        recordReceipt(notifId, "SMS", "+263770009999", "DELIVERED", null);

        MvcResult result = mockMvc.perform(get("/internal/v1/delivery-receipts")
                        .param("notificationId", notifId)
                        .header("X-Tenant-ID", TENANT_ID)
                        .header("X-Pod-ID", POD_ID)
                        .header("X-Request-ID", UUID.randomUUID().toString())
                        .header("X-Correlation-ID", UUID.randomUUID().toString()))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode root = MAPPER.readTree(result.getResponse().getContentAsString());
        assertThat(root.isArray()).isTrue();
        assertThat(root.size()).isEqualTo(2);

        // Both should reference the same notification
        for (JsonNode receipt : root) {
            assertThat(receipt.get("notificationId").asText()).isEqualTo(notifId);
        }
    }

    // ── Test 9: Template currentVersion increments with new versions ─────

    @Test
    @DisplayName("Creating a new template version increments the template's currentVersion")
    void templateCurrentVersionIncrements() throws Exception {
        String key = "cur-ver-" + UUID.randomUUID().toString().substring(0, 8);
        String templateId = createTemplateAndGetId(key, "SMS", null,
                "Initial body.");

        // Check initial currentVersion
        MvcResult initialGet = mockMvc.perform(get("/internal/v1/templates/" + key)
                        .header("X-Tenant-ID", TENANT_ID)
                        .header("X-Pod-ID", POD_ID)
                        .header("X-Request-ID", UUID.randomUUID().toString())
                        .header("X-Correlation-ID", UUID.randomUUID().toString()))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode initial = MAPPER.readTree(initialGet.getResponse().getContentAsString());
        int initialVersion = initial.get("currentVersion").asInt();

        // Create a new version
        String versionBody = """
                {
                  "content": "Updated body for version check.",
                  "changelog": "Testing version increment"
                }
                """;

        mockMvc.perform(post("/internal/v1/templates/" + templateId + "/versions")
                        .header("X-Tenant-ID", TENANT_ID)
                        .header("X-Pod-ID", POD_ID)
                        .header("X-Request-ID", UUID.randomUUID().toString())
                        .header("X-Correlation-ID", UUID.randomUUID().toString())
                        .header("Idempotency-Key", "ver-inc-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(versionBody))
                .andExpect(status().isCreated());

        // Check currentVersion has incremented
        MvcResult afterGet = mockMvc.perform(get("/internal/v1/templates/" + key)
                        .header("X-Tenant-ID", TENANT_ID)
                        .header("X-Pod-ID", POD_ID)
                        .header("X-Request-ID", UUID.randomUUID().toString())
                        .header("X-Correlation-ID", UUID.randomUUID().toString()))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode after = MAPPER.readTree(afterGet.getResponse().getContentAsString());
        assertThat(after.get("currentVersion").asInt()).isGreaterThan(initialVersion);
    }
}
