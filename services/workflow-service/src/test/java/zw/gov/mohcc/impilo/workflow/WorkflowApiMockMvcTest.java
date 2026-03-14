package zw.gov.mohcc.impilo.workflow;

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
import zw.gov.mohcc.impilo.workflow.repository.OutboxEventRepository;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class WorkflowApiMockMvcTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private OutboxEventRepository outboxRepository;

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String TENANT_ID = UUID.randomUUID().toString();

    private static final String DEF_BODY = """
        {"code":"vitals-capture","name":"Vitals Capture Workflow",
         "description":"Standard vitals capture flow",
         "category":"CLINICAL",
         "steps":[{"name":"triage","type":"TASK"},{"name":"capture","type":"TASK"},{"name":"review","type":"TASK"}]}""";

    // ── A) Missing headers ──

    @Nested
    @DisplayName("A) Missing headers => 400")
    class MissingHeaders {
        @Test
        void missingTenantId() throws Exception {
            MvcResult result = mockMvc.perform(get("/internal/v1/workflows/definitions")
                            .header("X-Pod-ID", "national")
                            .header("X-Request-ID", "req-1")
                            .header("X-Correlation-ID", "corr-1"))
                    .andExpect(status().isBadRequest())
                    .andReturn();
            assertErrorEnvelope(result, "MISSING_REQUIRED_HEADER");
        }
    }

    // ── B) Definition CRUD + publish lifecycle ──

    @Nested
    @DisplayName("B) Definition lifecycle: create -> publish")
    class DefinitionLifecycle {

        @Test
        @DisplayName("Create definition returns 201 DRAFT")
        void createDefinition() throws Exception {
            mockMvc.perform(post("/internal/v1/workflows/definitions")
                            .header("X-Tenant-ID", TENANT_ID)
                            .header("X-Pod-ID", "national")
                            .header("X-Request-ID", UUID.randomUUID().toString())
                            .header("X-Correlation-ID", UUID.randomUUID().toString())
                            .header("Idempotency-Key", "create-def-" + System.nanoTime())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(DEF_BODY))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.definitionId").exists())
                    .andExpect(jsonPath("$.code").value("vitals-capture"))
                    .andExpect(jsonPath("$.status").value("DRAFT"))
                    .andExpect(jsonPath("$.steps").isArray());
        }

        @Test
        @DisplayName("Publish definition transitions to PUBLISHED")
        void publishDefinition() throws Exception {
            String defId = createAndReturnDefId();

            mockMvc.perform(post("/internal/v1/workflows/definitions/" + defId + "/publish")
                            .header("X-Tenant-ID", TENANT_ID)
                            .header("X-Pod-ID", "national")
                            .header("X-Request-ID", UUID.randomUUID().toString())
                            .header("X-Correlation-ID", UUID.randomUUID().toString())
                            .header("Idempotency-Key", "publish-def-" + System.nanoTime()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("PUBLISHED"))
                    .andExpect(jsonPath("$.publishedAt").exists());
        }
    }

    // ── C) Instance transitions ──

    @Nested
    @DisplayName("C) Instance lifecycle: start -> advance -> complete")
    class InstanceTransitions {

        @Test
        @DisplayName("Start instance from published definition")
        void startInstance() throws Exception {
            String defId = createPublishedDef();

            String body = String.format("""
                {"definitionId":"%s","initiatorRef":"nurse-001","subjectRef":"PAT-001",
                 "context":{"facility":"FAC-001"}}""", defId);

            mockMvc.perform(post("/internal/v1/workflows/instances")
                            .header("X-Tenant-ID", TENANT_ID)
                            .header("X-Pod-ID", "national")
                            .header("X-Request-ID", UUID.randomUUID().toString())
                            .header("X-Correlation-ID", UUID.randomUUID().toString())
                            .header("Idempotency-Key", "start-inst-" + System.nanoTime())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.instanceId").exists())
                    .andExpect(jsonPath("$.status").value("RUNNING"))
                    .andExpect(jsonPath("$.currentStep").value("triage"));
        }

        @Test
        @DisplayName("Cannot start instance from DRAFT definition")
        void startFromDraftFails() throws Exception {
            String defId = createAndReturnDefId();

            String body = String.format("""
                {"definitionId":"%s","initiatorRef":"nurse-001"}""", defId);

            mockMvc.perform(post("/internal/v1/workflows/instances")
                            .header("X-Tenant-ID", TENANT_ID)
                            .header("X-Pod-ID", "national")
                            .header("X-Request-ID", UUID.randomUUID().toString())
                            .header("X-Correlation-ID", UUID.randomUUID().toString())
                            .header("Idempotency-Key", "start-draft-" + System.nanoTime())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isUnprocessableEntity());
        }

        @Test
        @DisplayName("Complete instance transitions to COMPLETED")
        void completeInstance() throws Exception {
            String instanceId = createRunningInstance();

            mockMvc.perform(post("/internal/v1/workflows/instances/" + instanceId + "/transition")
                            .header("X-Tenant-ID", TENANT_ID)
                            .header("X-Pod-ID", "national")
                            .header("X-Request-ID", UUID.randomUUID().toString())
                            .header("X-Correlation-ID", UUID.randomUUID().toString())
                            .header("Idempotency-Key", "complete-" + System.nanoTime())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"action\":\"COMPLETE\",\"output\":{\"result\":\"ok\"}}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("COMPLETED"))
                    .andExpect(jsonPath("$.completedAt").exists());
        }

        @Test
        @DisplayName("Cannot complete already completed instance")
        void doubleCompleteFails() throws Exception {
            String instanceId = createRunningInstance();

            // Complete once
            mockMvc.perform(post("/internal/v1/workflows/instances/" + instanceId + "/transition")
                            .header("X-Tenant-ID", TENANT_ID)
                            .header("X-Pod-ID", "national")
                            .header("X-Request-ID", UUID.randomUUID().toString())
                            .header("X-Correlation-ID", UUID.randomUUID().toString())
                            .header("Idempotency-Key", "comp1-" + System.nanoTime())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"action\":\"COMPLETE\"}"))
                    .andExpect(status().isOk());

            // Try again
            mockMvc.perform(post("/internal/v1/workflows/instances/" + instanceId + "/transition")
                            .header("X-Tenant-ID", TENANT_ID)
                            .header("X-Pod-ID", "national")
                            .header("X-Request-ID", UUID.randomUUID().toString())
                            .header("X-Correlation-ID", UUID.randomUUID().toString())
                            .header("Idempotency-Key", "comp2-" + System.nanoTime())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"action\":\"COMPLETE\"}"))
                    .andExpect(status().isUnprocessableEntity());
        }
    }

    // ── D) Snapshot endpoints ──

    @Nested
    @DisplayName("D) Snapshot endpoints")
    class Snapshots {
        @Test
        void workflowSnapshot() throws Exception {
            mockMvc.perform(get("/internal/v1/snapshots/workflows")
                            .header("X-Tenant-ID", TENANT_ID)
                            .header("X-Pod-ID", "national")
                            .header("X-Request-ID", UUID.randomUUID().toString())
                            .header("X-Correlation-ID", UUID.randomUUID().toString()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items").isArray())
                    .andExpect(jsonPath("$.as_of").exists());
        }

        @Test
        void instanceSnapshot() throws Exception {
            mockMvc.perform(get("/internal/v1/snapshots/instances")
                            .header("X-Tenant-ID", TENANT_ID)
                            .header("X-Pod-ID", "national")
                            .header("X-Request-ID", UUID.randomUUID().toString())
                            .header("X-Correlation-ID", UUID.randomUUID().toString()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items").isArray())
                    .andExpect(jsonPath("$.as_of").exists());
        }
    }

    // ── E) Outbox validation ──

    @Nested
    @DisplayName("E) Outbox events on definition creation")
    class OutboxValidation {
        @Test
        void outboxOnCreate() throws Exception {
            String idempotencyKey = "outbox-wf-" + System.nanoTime();

            MvcResult result = mockMvc.perform(post("/internal/v1/workflows/definitions")
                            .header("X-Tenant-ID", TENANT_ID)
                            .header("X-Pod-ID", "national")
                            .header("X-Request-ID", UUID.randomUUID().toString())
                            .header("X-Correlation-ID", UUID.randomUUID().toString())
                            .header("Idempotency-Key", idempotencyKey)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(DEF_BODY))
                    .andExpect(status().isCreated())
                    .andReturn();

            String defId = MAPPER.readTree(result.getResponse().getContentAsString())
                    .get("definitionId").asText();

            var outboxRows = outboxRepository.findByAggregateIdAndEventType(
                    defId, "impilo.workflow.definition.created.v1");
            assertThat(outboxRows).hasSize(1);
            assertThat(outboxRows.get(0).getIdempotencyKey()).isEqualTo(idempotencyKey);
        }
    }

    // ── Helpers ──

    private String createAndReturnDefId() throws Exception {
        MvcResult result = mockMvc.perform(post("/internal/v1/workflows/definitions")
                        .header("X-Tenant-ID", TENANT_ID)
                        .header("X-Pod-ID", "national")
                        .header("X-Request-ID", UUID.randomUUID().toString())
                        .header("X-Correlation-ID", UUID.randomUUID().toString())
                        .header("Idempotency-Key", "helper-def-" + System.nanoTime())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(DEF_BODY))
                .andExpect(status().isCreated())
                .andReturn();
        return MAPPER.readTree(result.getResponse().getContentAsString()).get("definitionId").asText();
    }

    private String createPublishedDef() throws Exception {
        String defId = createAndReturnDefId();
        mockMvc.perform(post("/internal/v1/workflows/definitions/" + defId + "/publish")
                        .header("X-Tenant-ID", TENANT_ID)
                        .header("X-Pod-ID", "national")
                        .header("X-Request-ID", UUID.randomUUID().toString())
                        .header("X-Correlation-ID", UUID.randomUUID().toString())
                        .header("Idempotency-Key", "helper-pub-" + System.nanoTime()))
                .andExpect(status().isOk());
        return defId;
    }

    private String createRunningInstance() throws Exception {
        String defId = createPublishedDef();
        String body = String.format("""
            {"definitionId":"%s","initiatorRef":"nurse-test"}""", defId);
        MvcResult result = mockMvc.perform(post("/internal/v1/workflows/instances")
                        .header("X-Tenant-ID", TENANT_ID)
                        .header("X-Pod-ID", "national")
                        .header("X-Request-ID", UUID.randomUUID().toString())
                        .header("X-Correlation-ID", UUID.randomUUID().toString())
                        .header("Idempotency-Key", "helper-inst-" + System.nanoTime())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();
        return MAPPER.readTree(result.getResponse().getContentAsString()).get("instanceId").asText();
    }

    private void assertErrorEnvelope(MvcResult result, String expectedCode) throws Exception {
        String body = result.getResponse().getContentAsString();
        assertThat(body).isNotBlank();
        JsonNode root = MAPPER.readTree(body);
        assertThat(root.has("error")).as("Response should contain 'error' field: " + body).isTrue();
        assertThat(root.get("error").get("code").asText()).isEqualTo(expectedCode);
    }
}
