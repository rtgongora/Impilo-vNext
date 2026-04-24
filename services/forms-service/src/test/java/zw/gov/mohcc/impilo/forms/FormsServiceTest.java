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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

    /** Minimal JSON Schema (draft-07) embedded in request JSON. */
    private static final String SCHEMA_V1 =
            "{\\\"type\\\":\\\"object\\\",\\\"properties\\\":{\\\"systolic\\\":{\\\"type\\\":\\\"number\\\"},"
                    + "\\\"diastolic\\\":{\\\"type\\\":\\\"number\\\"}}}";

    private static final String SCHEMA_V2 =
            "{\\\"type\\\":\\\"object\\\",\\\"properties\\\":{\\\"pulse\\\":{\\\"type\\\":\\\"integer\\\"}}}";

    private static String formBody(String formKey, String name) {
        return """
                {
                  "formKey": "%s",
                  "name": "%s",
                  "description": "Test form",
                  "schemaJson": "%s"
                }
                """
                .formatted(formKey, name, SCHEMA_V1);
    }

    private static String versionBody(String escapedSchemaJson) {
        return """
                {
                  "schemaJson": "%s",
                  "changelog": "test version"
                }
                """
                .formatted(escapedSchemaJson);
    }

    private String createForm() throws Exception {
        String formKey = "form-" + UUID.randomUUID().toString().substring(0, 8);
        MvcResult result = mockMvc.perform(post("/internal/v1/forms")
                        .header("X-Tenant-ID", TENANT_ID)
                        .header("X-Pod-ID", POD_ID)
                        .header("X-Request-ID", UUID.randomUUID().toString())
                        .header("X-Correlation-ID", UUID.randomUUID().toString())
                        .header("Idempotency-Key", "form-create-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(formBody(formKey, "Test Form")))
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
                        .content(versionBody(SCHEMA_V2)))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode root = MAPPER.readTree(result.getResponse().getContentAsString());
        return root.get("id").asText();
    }

    @Test
    @DisplayName("POST /internal/v1/forms creates form and GET retrieves it")
    void createAndGetForm() throws Exception {
        String formKey = "crud-" + UUID.randomUUID().toString().substring(0, 8);
        MvcResult createResult = mockMvc.perform(post("/internal/v1/forms")
                        .header("X-Tenant-ID", TENANT_ID)
                        .header("X-Pod-ID", POD_ID)
                        .header("X-Request-ID", UUID.randomUUID().toString())
                        .header("X-Correlation-ID", UUID.randomUUID().toString())
                        .header("Idempotency-Key", "form-crud-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(formBody(formKey, "CRUD Test Form")))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode created = MAPPER.readTree(createResult.getResponse().getContentAsString());
        String formId = created.get("id").asText();
        assertThat(formId).isNotBlank();
        assertThat(created.get("formKey").asText()).isEqualTo(formKey);
        assertThat(created.get("status").asText()).isEqualTo("DRAFT");
        assertThat(created.get("currentVersion").asInt()).isEqualTo(1);

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

    @Test
    @DisplayName("GET /internal/v1/forms lists schemas for tenant")
    void listSchemasIncludesCreated() throws Exception {
        String formKey = "list-" + UUID.randomUUID().toString().substring(0, 8);
        MvcResult createResult = mockMvc.perform(post("/internal/v1/forms")
                        .header("X-Tenant-ID", TENANT_ID)
                        .header("X-Pod-ID", POD_ID)
                        .header("X-Request-ID", UUID.randomUUID().toString())
                        .header("X-Correlation-ID", UUID.randomUUID().toString())
                        .header("Idempotency-Key", "form-list-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(formBody(formKey, "List Test Form")))
                .andExpect(status().isCreated())
                .andReturn();
        String formId = MAPPER.readTree(createResult.getResponse().getContentAsString()).get("id").asText();

        MvcResult listResult = mockMvc.perform(get("/internal/v1/forms")
                        .header("X-Tenant-ID", TENANT_ID)
                        .header("X-Pod-ID", POD_ID)
                        .header("X-Request-ID", UUID.randomUUID().toString())
                        .header("X-Correlation-ID", UUID.randomUUID().toString()))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode arr = MAPPER.readTree(listResult.getResponse().getContentAsString());
        assertThat(arr.isArray()).isTrue();
        boolean found = false;
        for (JsonNode n : arr) {
            if (formId.equals(n.get("id").asText()) && formKey.equals(n.get("formKey").asText())) {
                found = true;
                break;
            }
        }
        assertThat(found).isTrue();
    }

    @Test
    @DisplayName("POST /internal/v1/forms/{id}/versions creates additional schema version")
    void createVersionWithValidContent() throws Exception {
        String formId = createForm();

        MvcResult result = mockMvc.perform(post("/internal/v1/forms/" + formId + "/versions")
                        .header("X-Tenant-ID", TENANT_ID)
                        .header("X-Pod-ID", POD_ID)
                        .header("X-Request-ID", UUID.randomUUID().toString())
                        .header("X-Correlation-ID", UUID.randomUUID().toString())
                        .header("Idempotency-Key", "ver-valid-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(versionBody(SCHEMA_V2)))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode version = MAPPER.readTree(result.getResponse().getContentAsString());
        assertThat(version.get("version").asInt()).isEqualTo(2);
        assertThat(version.get("formSchemaId").asText()).isEqualTo(formId);
    }

    @Test
    @DisplayName("POST /internal/v1/forms/{id}/versions rejects blank schemaJson")
    void rejectVersionWithBlankSchemaJson() throws Exception {
        String formId = createForm();

        mockMvc.perform(post("/internal/v1/forms/" + formId + "/versions")
                        .header("X-Tenant-ID", TENANT_ID)
                        .header("X-Pod-ID", POD_ID)
                        .header("X-Request-ID", UUID.randomUUID().toString())
                        .header("X-Correlation-ID", UUID.randomUUID().toString())
                        .header("Idempotency-Key", "ver-invalid-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"schemaJson": "", "changelog": "x"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /internal/v1/forms/{id}/publish publishes schema and sets status to PUBLISHED")
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
        assertThat(pub.get("id").asText()).isEqualTo(formId);
        assertThat(pub.get("currentVersion").asInt()).isEqualTo(2);
        assertThat(pub.get("status").asText()).isEqualTo("PUBLISHED");

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

    @Test
    @DisplayName("POST /internal/v1/forms/{id}/publish when not DRAFT fails")
    void publishWhenNotDraftFails() throws Exception {
        String formId = createForm();

        mockMvc.perform(post("/internal/v1/forms/" + formId + "/publish")
                        .header("X-Tenant-ID", TENANT_ID)
                        .header("X-Pod-ID", POD_ID)
                        .header("X-Request-ID", UUID.randomUUID().toString())
                        .header("X-Correlation-ID", UUID.randomUUID().toString())
                        .header("Idempotency-Key", "form-pub-1-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        assertThatThrownBy(() -> mockMvc.perform(post("/internal/v1/forms/" + formId + "/publish")
                        .header("X-Tenant-ID", TENANT_ID)
                        .header("X-Pod-ID", POD_ID)
                        .header("X-Request-ID", UUID.randomUUID().toString())
                        .header("X-Correlation-ID", UUID.randomUUID().toString())
                        .header("Idempotency-Key", "form-pub-2-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON))
                .andReturn())
                .hasRootCauseInstanceOf(IllegalStateException.class)
                .rootCause()
                .hasMessageContaining("Cannot publish schema in status PUBLISHED");
    }

    @Test
    @DisplayName("Form create persists outbox event impilo.forms.schema.created.v1")
    void formCreatePersistsOutboxEvent() throws Exception {
        long outboxCountBefore = outboxEventRepository.count();

        createForm();

        List<OutboxEventEntity> allEvents = outboxEventRepository.findAll();
        assertThat(allEvents.size()).isGreaterThan((int) outboxCountBefore);

        boolean hasCreatedEvent = allEvents.stream()
                .anyMatch(e -> "impilo.forms.schema.created.v1".equals(e.getEventType()));
        assertThat(hasCreatedEvent)
                .as("Outbox should contain schema created event")
                .isTrue();
    }

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
                .anyMatch(e -> "impilo.forms.schema.version_created.v1".equals(e.getEventType()));
        assertThat(hasVersionEvent).as("Outbox should contain version created event").isTrue();

        boolean hasPublishEvent = allEvents.stream()
                .anyMatch(e -> "impilo.forms.schema.published.v1".equals(e.getEventType()));
        assertThat(hasPublishEvent).as("Outbox should contain schema published event").isTrue();
    }

    @Test
    @DisplayName("Multiple versions auto-increment version numbers")
    void versionNumbersAutoIncrement() throws Exception {
        String formId = createForm();

        MvcResult v1 = mockMvc.perform(post("/internal/v1/forms/" + formId + "/versions")
                        .header("X-Tenant-ID", TENANT_ID)
                        .header("X-Pod-ID", POD_ID)
                        .header("X-Request-ID", UUID.randomUUID().toString())
                        .header("X-Correlation-ID", UUID.randomUUID().toString())
                        .header("Idempotency-Key", "ver-inc-1-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(versionBody(SCHEMA_V2)))
                .andExpect(status().isCreated())
                .andReturn();

        MvcResult v2 = mockMvc.perform(post("/internal/v1/forms/" + formId + "/versions")
                        .header("X-Tenant-ID", TENANT_ID)
                        .header("X-Pod-ID", POD_ID)
                        .header("X-Request-ID", UUID.randomUUID().toString())
                        .header("X-Correlation-ID", UUID.randomUUID().toString())
                        .header("Idempotency-Key", "ver-inc-2-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(versionBody(SCHEMA_V2)))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode ver1 = MAPPER.readTree(v1.getResponse().getContentAsString());
        JsonNode ver2 = MAPPER.readTree(v2.getResponse().getContentAsString());

        assertThat(ver1.get("version").asInt()).isEqualTo(2);
        assertThat(ver2.get("version").asInt()).isEqualTo(3);

        MvcResult getResult = mockMvc.perform(get("/internal/v1/forms/" + formId)
                        .header("X-Tenant-ID", TENANT_ID)
                        .header("X-Pod-ID", POD_ID)
                        .header("X-Request-ID", UUID.randomUUID().toString())
                        .header("X-Correlation-ID", UUID.randomUUID().toString()))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(MAPPER.readTree(getResult.getResponse().getContentAsString()).get("currentVersion").asInt())
                .isEqualTo(3);
    }
}
