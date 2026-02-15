package zw.gov.mohcc.impilo.forms;

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
import zw.gov.mohcc.impilo.forms.domain.OutboxEventEntity;
import zw.gov.mohcc.impilo.forms.repository.OutboxEventRepository;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FormsServiceTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String TENANT_ID = "moh-zw";
    private static final String POD_ID = "national";

    // ── Helpers ──────────────────────────────────────────────────────

    private static String formBody() {
        return """
                {
                  "code": "vitals-form",
                  "name": "Vital Signs Form",
                  "description": "Standard vitals capture form",
                  "category": "clinical"
                }
                """;
    }

    private static String formBody(String code, String name) {
        return """
                {
                  "code": "%s",
                  "name": "%s",
                  "description": "Test form",
                  "category": "clinical"
                }
                """.formatted(code, name);
    }

    private static String updateBody(String name) {
        return """
                {
                  "name": "%s",
                  "description": "Updated description",
                  "category": "admin"
                }
                """.formatted(name);
    }

    private static String versionBody() {
        return """
                {
                  "contentJson": "{\\"fields\\": [{\\"name\\": \\"systolic\\", \\"type\\": \\"number\\"}, {\\"name\\": \\"diastolic\\", \\"type\\": \\"number\\"}]}"
                }
                """;
    }

    private static String invalidVersionBody() {
        return """
                {
                  "contentJson": "{\\"noFieldsHere\\": true}"
                }
                """;
    }

    private String createForm() throws Exception {
        String code = "form-" + UUID.randomUUID().toString().substring(0, 8);
        MvcResult result = mockMvc.perform(post("/internal/v1/forms")
                        .header("X-Tenant-ID", TENANT_ID)
                        .header("X-Pod-ID", POD_ID)
                        .header("X-Request-ID", UUID.randomUUID().toString())
                        .header("X-Correlation-ID", UUID.randomUUID().toString())
                        .header("Idempotency-Key", "form-create-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(formBody(code, "Test Form")))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode root = MAPPER.readTree(result.getResponse().getContentAsString());
        return root.get("id").asText();
    }

    private String createVersion(String formId) throws Exception {
        MvcResult result = mockMvc.perform(post("/internal/v1/forms/" + formId + "/versions")
                        .header("X-Tenant-ID", TENANT_ID)
                        .header("X-Pod-ID", POD_ID)
                        .header("X-Request-ID", UUID.randomUUID().toString())
                        .header("X-Correlation-ID", UUID.randomUUID().toString())
                        .header("Idempotency-Key", "ver-create-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(versionBody()))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode root = MAPPER.readTree(result.getResponse().getContentAsString());
        return root.get("id").asText();
    }

    // ── Test 1: Create and retrieve a form ──────────────────────────

    @Test
    @DisplayName("POST /internal/v1/forms creates form and GET retrieves it")
    void createAndGetForm() throws Exception {
        String code = "crud-" + UUID.randomUUID().toString().substring(0, 8);
        MvcResult createResult = mockMvc.perform(post("/internal/v1/forms")
                        .header("X-Tenant-ID", TENANT_ID)
                        .header("X-Pod-ID", POD_ID)
                        .header("X-Request-ID", UUID.randomUUID().toString())
                        .header("X-Correlation-ID", UUID.randomUUID().toString())
                        .header("Idempotency-Key", "form-crud-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(formBody(code, "CRUD Test Form")))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode created = MAPPER.readTree(createResult.getResponse().getContentAsString());
        String formId = created.get("id").asText();
        assertThat(formId).isNotBlank();
        assertThat(created.get("code").asText()).isEqualTo(code);
        assertThat(created.get("status").asText()).isEqualTo("DRAFT");

        MvcResult getResult = mockMvc.perform(get("/internal/v1/forms/" + formId)
                        .header("X-Tenant-ID", TENANT_ID)
                        .header("X-Pod-ID", POD_ID)
                        .header("X-Request-ID", UUID.randomUUID().toString())
                        .header("X-Correlation-ID", UUID.randomUUID().toString()))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode fetched = MAPPER.readTree(getResult.getResponse().getContentAsString());
        assertThat(fetched.get("id").asText()).isEqualTo(formId);
        assertThat(fetched.get("name").asText()).isEqualTo("CRUD Test Form");
    }

    // ── Test 2: Update a form ───────────────────────────────────────

    @Test
    @DisplayName("PUT /internal/v1/forms/{id} updates form name and description")
    void updateForm() throws Exception {
        String formId = createForm();

        MvcResult result = mockMvc.perform(put("/internal/v1/forms/" + formId)
                        .header("X-Tenant-ID", TENANT_ID)
                        .header("X-Pod-ID", POD_ID)
                        .header("X-Request-ID", UUID.randomUUID().toString())
                        .header("X-Correlation-ID", UUID.randomUUID().toString())
                        .header("Idempotency-Key", "form-update-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody("Updated Name")))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode updated = MAPPER.readTree(result.getResponse().getContentAsString());
        assertThat(updated.get("name").asText()).isEqualTo("Updated Name");
        assertThat(updated.get("description").asText()).isEqualTo("Updated description");
    }

    // ── Test 3: Create version with valid content ───────────────────

    @Test
    @DisplayName("POST /internal/v1/forms/{id}/versions creates version with valid fields JSON")
    void createVersionWithValidContent() throws Exception {
        String formId = createForm();

        MvcResult result = mockMvc.perform(post("/internal/v1/forms/" + formId + "/versions")
                        .header("X-Tenant-ID", TENANT_ID)
                        .header("X-Pod-ID", POD_ID)
                        .header("X-Request-ID", UUID.randomUUID().toString())
                        .header("X-Correlation-ID", UUID.randomUUID().toString())
                        .header("Idempotency-Key", "ver-valid-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(versionBody()))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode version = MAPPER.readTree(result.getResponse().getContentAsString());
        assertThat(version.get("versionNumber").asInt()).isEqualTo(1);
        assertThat(version.get("formId").asText()).isEqualTo(formId);
    }

    // ── Test 4: Reject version without "fields" array ───────────────

    @Test
    @DisplayName("POST /internal/v1/forms/{id}/versions rejects content without fields array")
    void rejectVersionWithoutFieldsArray() throws Exception {
        String formId = createForm();

        mockMvc.perform(post("/internal/v1/forms/" + formId + "/versions")
                        .header("X-Tenant-ID", TENANT_ID)
                        .header("X-Pod-ID", POD_ID)
                        .header("X-Request-ID", UUID.randomUUID().toString())
                        .header("X-Correlation-ID", UUID.randomUUID().toString())
                        .header("Idempotency-Key", "ver-invalid-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidVersionBody()))
                .andExpect(status().isBadRequest());
    }

    // ── Test 5: Publish latest version transitions form to PUBLISHED ─

    @Test
    @DisplayName("POST /internal/v1/forms/{id}/publish publishes latest version and sets status to PUBLISHED")
    void publishLatestVersion() throws Exception {
        String formId = createForm();
        createVersion(formId);

        MvcResult pubResult = mockMvc.perform(post("/internal/v1/forms/" + formId + "/publish")
                        .header("X-Tenant-ID", TENANT_ID)
                        .header("X-Pod-ID", POD_ID)
                        .header("X-Request-ID", UUID.randomUUID().toString())
                        .header("X-Correlation-ID", UUID.randomUUID().toString())
                        .header("Idempotency-Key", "form-publish-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode pub = MAPPER.readTree(pubResult.getResponse().getContentAsString());
        assertThat(pub.get("formId").asText()).isEqualTo(formId);
        assertThat(pub.get("versionNumber").asInt()).isEqualTo(1);

        // Verify form status is now PUBLISHED
        MvcResult getResult = mockMvc.perform(get("/internal/v1/forms/" + formId)
                        .header("X-Tenant-ID", TENANT_ID)
                        .header("X-Pod-ID", POD_ID)
                        .header("X-Request-ID", UUID.randomUUID().toString())
                        .header("X-Correlation-ID", UUID.randomUUID().toString()))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode form = MAPPER.readTree(getResult.getResponse().getContentAsString());
        assertThat(form.get("status").asText()).isEqualTo("PUBLISHED");
    }

    // ── Test 6: Publish without versions returns 409 ─────────────────

    @Test
    @DisplayName("POST /internal/v1/forms/{id}/publish without versions returns 409")
    void publishWithoutVersionsReturns409() throws Exception {
        String formId = createForm();

        mockMvc.perform(post("/internal/v1/forms/" + formId + "/publish")
                        .header("X-Tenant-ID", TENANT_ID)
                        .header("X-Pod-ID", POD_ID)
                        .header("X-Request-ID", UUID.randomUUID().toString())
                        .header("X-Correlation-ID", UUID.randomUUID().toString())
                        .header("Idempotency-Key", "form-nover-pub-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isConflict());
    }

    // ── Test 7: Outbox events are persisted on form create ──────────

    @Test
    @DisplayName("Form create persists outbox event with type impilo.forms.form.created.v1")
    void formCreatePersistsOutboxEvent() throws Exception {
        long outboxCountBefore = outboxEventRepository.count();

        createForm();

        List<OutboxEventEntity> allEvents = outboxEventRepository.findAll();
        assertThat(allEvents.size()).isGreaterThan((int) outboxCountBefore);

        boolean hasCreatedEvent = allEvents.stream()
                .anyMatch(e -> "impilo.forms.form.created.v1".equals(e.getEventType()));
        assertThat(hasCreatedEvent)
                .as("Outbox should contain 'impilo.forms.form.created.v1'")
                .isTrue();
    }

    // ── Test 8: Outbox events for version create and publish ────────

    @Test
    @DisplayName("Version create and publish persist corresponding outbox events")
    void versionAndPublishOutboxEvents() throws Exception {
        String formId = createForm();
        createVersion(formId);

        mockMvc.perform(post("/internal/v1/forms/" + formId + "/publish")
                        .header("X-Tenant-ID", TENANT_ID)
                        .header("X-Pod-ID", POD_ID)
                        .header("X-Request-ID", UUID.randomUUID().toString())
                        .header("X-Correlation-ID", UUID.randomUUID().toString())
                        .header("Idempotency-Key", "form-pub-outbox-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        List<OutboxEventEntity> allEvents = outboxEventRepository.findAll();

        boolean hasVersionEvent = allEvents.stream()
                .anyMatch(e -> "impilo.forms.version.created.v1".equals(e.getEventType()));
        assertThat(hasVersionEvent)
                .as("Outbox should contain 'impilo.forms.version.created.v1'")
                .isTrue();

        boolean hasPublishEvent = allEvents.stream()
                .anyMatch(e -> "impilo.forms.form.published.v1".equals(e.getEventType()));
        assertThat(hasPublishEvent)
                .as("Outbox should contain 'impilo.forms.form.published.v1'")
                .isTrue();
    }

    // ── Test 9: Version numbers auto-increment ──────────────────────

    @Test
    @DisplayName("Multiple versions auto-increment version numbers")
    void versionNumbersAutoIncrement() throws Exception {
        String formId = createForm();

        // Create first version
        MvcResult v1 = mockMvc.perform(post("/internal/v1/forms/" + formId + "/versions")
                        .header("X-Tenant-ID", TENANT_ID)
                        .header("X-Pod-ID", POD_ID)
                        .header("X-Request-ID", UUID.randomUUID().toString())
                        .header("X-Correlation-ID", UUID.randomUUID().toString())
                        .header("Idempotency-Key", "ver-inc-1-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(versionBody()))
                .andExpect(status().isCreated())
                .andReturn();

        // Create second version
        MvcResult v2 = mockMvc.perform(post("/internal/v1/forms/" + formId + "/versions")
                        .header("X-Tenant-ID", TENANT_ID)
                        .header("X-Pod-ID", POD_ID)
                        .header("X-Request-ID", UUID.randomUUID().toString())
                        .header("X-Correlation-ID", UUID.randomUUID().toString())
                        .header("Idempotency-Key", "ver-inc-2-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(versionBody()))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode ver1 = MAPPER.readTree(v1.getResponse().getContentAsString());
        JsonNode ver2 = MAPPER.readTree(v2.getResponse().getContentAsString());

        assertThat(ver1.get("versionNumber").asInt()).isEqualTo(1);
        assertThat(ver2.get("versionNumber").asInt()).isEqualTo(2);

        // List versions
        MvcResult listResult = mockMvc.perform(get("/internal/v1/forms/" + formId + "/versions")
                        .header("X-Tenant-ID", TENANT_ID)
                        .header("X-Pod-ID", POD_ID)
                        .header("X-Request-ID", UUID.randomUUID().toString())
                        .header("X-Correlation-ID", UUID.randomUUID().toString()))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode versions = MAPPER.readTree(listResult.getResponse().getContentAsString());
        assertThat(versions.isArray()).isTrue();
        assertThat(versions.size()).isEqualTo(2);
    }
}
