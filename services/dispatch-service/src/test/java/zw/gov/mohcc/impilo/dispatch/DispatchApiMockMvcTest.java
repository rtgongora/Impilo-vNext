package zw.gov.mohcc.impilo.dispatch;

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
import zw.gov.mohcc.impilo.dispatch.domain.OutboxEventEntity;
import zw.gov.mohcc.impilo.dispatch.repository.OutboxEventRepository;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class DispatchApiMockMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OutboxEventRepository outboxRepository;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String TENANT_ID = UUID.randomUUID().toString();

    // ── A) Missing required headers => 400 envelope ──

    @Nested
    @DisplayName("A) Missing required headers => 400 envelope")
    class MissingHeaders {

        @Test
        @DisplayName("GET /internal/v1/dispatch/jobs without X-Tenant-ID returns 400")
        void missingTenantIdOnGet() throws Exception {
            MvcResult result = mockMvc.perform(get("/internal/v1/dispatch/jobs")
                            .header("X-Pod-ID", "national")
                            .header("X-Request-ID", "req-1")
                            .header("X-Correlation-ID", "corr-1"))
                    .andExpect(status().isBadRequest())
                    .andReturn();

            assertErrorEnvelope(result, "MISSING_REQUIRED_HEADER");
        }

        @Test
        @DisplayName("POST without any headers returns 400")
        void missingAllHeaders() throws Exception {
            MvcResult result = mockMvc.perform(post("/internal/v1/dispatch/jobs")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"facilityRef\":\"FAC-001\"}"))
                    .andExpect(status().isBadRequest())
                    .andReturn();

            assertErrorEnvelope(result, "MISSING_REQUIRED_HEADER");
        }
    }

    // ── B) Missing Idempotency-Key => 400 IDEMPOTENCY_KEY_REQUIRED ──

    @Nested
    @DisplayName("B) Missing Idempotency-Key => 400 IDEMPOTENCY_KEY_REQUIRED")
    class MissingIdempotencyKey {

        @Test
        @DisplayName("POST /internal/v1/dispatch/jobs without Idempotency-Key returns 400")
        void missingIdempotencyKeyOnCreate() throws Exception {
            MvcResult result = mockMvc.perform(post("/internal/v1/dispatch/jobs")
                            .header("X-Tenant-ID", TENANT_ID)
                            .header("X-Pod-ID", "national")
                            .header("X-Request-ID", "req-1")
                            .header("X-Correlation-ID", UUID.randomUUID().toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"facilityRef\":\"FAC-001\"}"))
                    .andExpect(status().isBadRequest())
                    .andReturn();

            assertErrorEnvelope(result, "IDEMPOTENCY_KEY_REQUIRED");
        }
    }

    // ── C) Replay create => same job_id, single outbox row ──

    @Nested
    @DisplayName("C) Replay create => same job_id, single outbox row")
    class IdempotencyReplay {

        @Test
        @DisplayName("POST with same Idempotency-Key replays original response with same job_id")
        void idempotentReplayCreate() throws Exception {
            String idempotencyKey = "idem-create-" + System.nanoTime();
            String body = "{\"facilityRef\":\"FAC-REPLAY\"}";

            // First request — creates
            MvcResult first = mockMvc.perform(post("/internal/v1/dispatch/jobs")
                            .header("X-Tenant-ID", TENANT_ID)
                            .header("X-Pod-ID", "national")
                            .header("X-Request-ID", "req-1")
                            .header("X-Correlation-ID", UUID.randomUUID().toString())
                            .header("Idempotency-Key", idempotencyKey)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isCreated())
                    .andReturn();

            String firstBody = first.getResponse().getContentAsString();
            JsonNode firstJson = MAPPER.readTree(firstBody);
            String jobId = firstJson.get("jobId").asText();

            // Second request — same key, same body → replay
            MvcResult second = mockMvc.perform(post("/internal/v1/dispatch/jobs")
                            .header("X-Tenant-ID", TENANT_ID)
                            .header("X-Pod-ID", "national")
                            .header("X-Request-ID", "req-2")
                            .header("X-Correlation-ID", UUID.randomUUID().toString())
                            .header("Idempotency-Key", idempotencyKey)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andReturn();

            assertThat(second.getResponse().getStatus()).isEqualTo(201);
            assertThat(second.getResponse().getContentAsString()).isEqualTo(firstBody);

            // Same job_id
            JsonNode secondJson = MAPPER.readTree(second.getResponse().getContentAsString());
            assertThat(secondJson.get("jobId").asText()).isEqualTo(jobId);

            // Single outbox row
            long outboxCount = outboxRepository.countByAggregateId(jobId);
            assertThat(outboxCount).isEqualTo(1);
        }
    }

    // ── D) Conflict: same key different body => 409 IDENTITY_CONFLICT ──

    @Nested
    @DisplayName("D) Conflict: same Idempotency-Key different body => 409 IDENTITY_CONFLICT")
    class IdempotencyConflict {

        @Test
        @DisplayName("POST same key with different body returns 409")
        void conflictOnDifferentBody() throws Exception {
            String idempotencyKey = "idem-conflict-" + System.nanoTime();

            // First request
            mockMvc.perform(post("/internal/v1/dispatch/jobs")
                            .header("X-Tenant-ID", TENANT_ID)
                            .header("X-Pod-ID", "national")
                            .header("X-Request-ID", "req-1")
                            .header("X-Correlation-ID", UUID.randomUUID().toString())
                            .header("Idempotency-Key", idempotencyKey)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"facilityRef\":\"FAC-AAA\"}"))
                    .andExpect(status().isCreated());

            // Second request — same key, DIFFERENT body
            MvcResult conflict = mockMvc.perform(post("/internal/v1/dispatch/jobs")
                            .header("X-Tenant-ID", TENANT_ID)
                            .header("X-Pod-ID", "national")
                            .header("X-Request-ID", "req-2")
                            .header("X-Correlation-ID", UUID.randomUUID().toString())
                            .header("Idempotency-Key", idempotencyKey)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"facilityRef\":\"FAC-BBB\"}"))
                    .andExpect(status().isConflict())
                    .andReturn();

            assertErrorEnvelope(conflict, "IDENTITY_CONFLICT");
        }
    }

    // ── E) Outbox includes v1.1 context columns and EventEnvelope partition_key==job_id ──

    @Nested
    @DisplayName("E) Outbox includes v1.1 context columns + meta.partition_key==job_id")
    class OutboxValidation {

        @Test
        @DisplayName("Created job produces outbox row with correct v1.1 fields")
        void outboxRowHasV11Fields() throws Exception {
            String correlationId = UUID.randomUUID().toString();
            String idempotencyKey = "outbox-test-" + System.nanoTime();

            MvcResult result = mockMvc.perform(post("/internal/v1/dispatch/jobs")
                            .header("X-Tenant-ID", TENANT_ID)
                            .header("X-Pod-ID", "national")
                            .header("X-Request-ID", "req-outbox-1")
                            .header("X-Correlation-ID", correlationId)
                            .header("Idempotency-Key", idempotencyKey)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"facilityRef\":\"FAC-OBX\"}"))
                    .andExpect(status().isCreated())
                    .andReturn();

            JsonNode respJson = MAPPER.readTree(result.getResponse().getContentAsString());
            String jobId = respJson.get("jobId").asText();

            List<OutboxEventEntity> outboxRows = outboxRepository
                    .findByAggregateIdAndEventType(jobId, "impilo.dispatch.job.created.v1");

            assertThat(outboxRows).hasSize(1);
            OutboxEventEntity row = outboxRows.get(0);

            assertThat(row.getEventId()).isNotNull();
            assertThat(row.getAggregateType()).isEqualTo("DispatchJob");
            assertThat(row.getAggregateId()).isEqualTo(jobId);
            assertThat(row.getEventType()).isEqualTo("impilo.dispatch.job.created.v1");
            assertThat(row.getCorrelationId()).isNotNull();
            assertThat(row.getCorrelationId().toString()).isEqualTo(correlationId);
            assertThat(row.getIdempotencyKey()).isEqualTo(idempotencyKey);
            assertThat(row.getProducer()).isEqualTo("dispatch-service");
            assertThat(row.getTenantId()).isNotNull();
            assertThat(row.getPodId()).isEqualTo("national");
            assertThat(row.getSubjectId()).isEqualTo(jobId);
            assertThat(row.getSubjectType()).isEqualTo("DispatchJob");
            assertThat(row.getOccurredAt()).isNotNull();

            // schema_version >= 1
            int schemaVersion = Integer.parseInt(row.getSchemaVersion());
            assertThat(schemaVersion).isGreaterThanOrEqualTo(1);

            // partition_key == job_id
            assertThat(row.getPartitionKey()).isEqualTo(jobId);

            // Payload contains job state
            JsonNode payload = MAPPER.readTree(row.getPayloadJson());
            assertThat(payload.get("job_id").asText()).isEqualTo(jobId);
            assertThat(payload.get("facility_ref").asText()).isEqualTo("FAC-OBX");
            assertThat(payload.get("status").asText()).isEqualTo("NEW");
        }

        @Test
        @DisplayName("Assigned job produces outbox row with assigned event type")
        void outboxRowOnAssign() throws Exception {
            // Create job first
            String createKey = "assign-create-" + System.nanoTime();
            MvcResult createResult = mockMvc.perform(post("/internal/v1/dispatch/jobs")
                            .header("X-Tenant-ID", TENANT_ID)
                            .header("X-Pod-ID", "national")
                            .header("X-Request-ID", "req-a-1")
                            .header("X-Correlation-ID", UUID.randomUUID().toString())
                            .header("Idempotency-Key", createKey)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"facilityRef\":\"FAC-ASSIGN\"}"))
                    .andExpect(status().isCreated())
                    .andReturn();

            String jobId = MAPPER.readTree(createResult.getResponse().getContentAsString())
                    .get("jobId").asText();

            // Assign
            String assignKey = "assign-key-" + System.nanoTime();
            mockMvc.perform(post("/internal/v1/dispatch/jobs/" + jobId + "/assign")
                            .header("X-Tenant-ID", TENANT_ID)
                            .header("X-Pod-ID", "national")
                            .header("X-Request-ID", "req-a-2")
                            .header("X-Correlation-ID", UUID.randomUUID().toString())
                            .header("Idempotency-Key", assignKey)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"agentRef\":\"AGENT-001\",\"vehicleRef\":\"VEH-001\"}"))
                    .andExpect(status().isOk());

            List<OutboxEventEntity> assignRows = outboxRepository
                    .findByAggregateIdAndEventType(jobId, "impilo.dispatch.job.assigned.v1");

            assertThat(assignRows).hasSize(1);
            OutboxEventEntity row = assignRows.get(0);
            assertThat(row.getPartitionKey()).isEqualTo(jobId);

            JsonNode payload = MAPPER.readTree(row.getPayloadJson());
            assertThat(payload.get("assigned_agent_ref").asText()).isEqualTo("AGENT-001");
            assertThat(payload.get("assigned_vehicle_ref").asText()).isEqualTo("VEH-001");
        }

        @Test
        @DisplayName("Status update produces outbox row with status.updated event type")
        void outboxRowOnStatusUpdate() throws Exception {
            // Create + assign first
            String createKey = "status-create-" + System.nanoTime();
            MvcResult createResult = mockMvc.perform(post("/internal/v1/dispatch/jobs")
                            .header("X-Tenant-ID", TENANT_ID)
                            .header("X-Pod-ID", "national")
                            .header("X-Request-ID", "req-s-1")
                            .header("X-Correlation-ID", UUID.randomUUID().toString())
                            .header("Idempotency-Key", createKey)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"facilityRef\":\"FAC-STATUS\"}"))
                    .andExpect(status().isCreated())
                    .andReturn();

            String jobId = MAPPER.readTree(createResult.getResponse().getContentAsString())
                    .get("jobId").asText();

            // Assign (NEW -> ASSIGNED)
            mockMvc.perform(post("/internal/v1/dispatch/jobs/" + jobId + "/assign")
                            .header("X-Tenant-ID", TENANT_ID)
                            .header("X-Pod-ID", "national")
                            .header("X-Request-ID", "req-s-2")
                            .header("X-Correlation-ID", UUID.randomUUID().toString())
                            .header("Idempotency-Key", "assign-" + System.nanoTime())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"agentRef\":\"AGENT-002\"}"))
                    .andExpect(status().isOk());

            // Status update (ASSIGNED -> IN_PROGRESS)
            String statusKey = "status-key-" + System.nanoTime();
            mockMvc.perform(post("/internal/v1/dispatch/jobs/" + jobId + "/status")
                            .header("X-Tenant-ID", TENANT_ID)
                            .header("X-Pod-ID", "national")
                            .header("X-Request-ID", "req-s-3")
                            .header("X-Correlation-ID", UUID.randomUUID().toString())
                            .header("Idempotency-Key", statusKey)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"status\":\"IN_PROGRESS\"}"))
                    .andExpect(status().isOk());

            List<OutboxEventEntity> statusRows = outboxRepository
                    .findByAggregateIdAndEventType(jobId, "impilo.dispatch.job.status.updated.v1");

            assertThat(statusRows).hasSize(1);
            OutboxEventEntity row = statusRows.get(0);
            assertThat(row.getPartitionKey()).isEqualTo(jobId);

            JsonNode payload = MAPPER.readTree(row.getPayloadJson());
            assertThat(payload.get("previous_status").asText()).isEqualTo("ASSIGNED");
            assertThat(payload.get("new_status").asText()).isEqualTo("IN_PROGRESS");
        }
    }

    // ── F) Invalid status transition => 422 ──

    @Nested
    @DisplayName("F) Invalid status transition => 422 INVALID_STATUS_TRANSITION")
    class StatusTransitions {

        @Test
        @DisplayName("NEW -> COMPLETED is invalid and returns 422")
        void invalidTransitionReturns422() throws Exception {
            // Create job
            String createKey = "trans-create-" + System.nanoTime();
            MvcResult createResult = mockMvc.perform(post("/internal/v1/dispatch/jobs")
                            .header("X-Tenant-ID", TENANT_ID)
                            .header("X-Pod-ID", "national")
                            .header("X-Request-ID", "req-t-1")
                            .header("X-Correlation-ID", UUID.randomUUID().toString())
                            .header("Idempotency-Key", createKey)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"facilityRef\":\"FAC-TRANS\"}"))
                    .andExpect(status().isCreated())
                    .andReturn();

            String jobId = MAPPER.readTree(createResult.getResponse().getContentAsString())
                    .get("jobId").asText();

            // Try invalid transition: NEW -> COMPLETED (skipping ASSIGNED, IN_PROGRESS)
            MvcResult result = mockMvc.perform(post("/internal/v1/dispatch/jobs/" + jobId + "/status")
                            .header("X-Tenant-ID", TENANT_ID)
                            .header("X-Pod-ID", "national")
                            .header("X-Request-ID", "req-t-2")
                            .header("X-Correlation-ID", UUID.randomUUID().toString())
                            .header("Idempotency-Key", "trans-key-" + System.nanoTime())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"status\":\"COMPLETED\"}"))
                    .andExpect(status().isUnprocessableEntity())
                    .andReturn();

            assertErrorEnvelope(result, "INVALID_STATUS_TRANSITION");
        }
    }

    // ── G) Snapshot endpoint returns paged jobs ──

    @Nested
    @DisplayName("G) Snapshot endpoint returns paged jobs with as_of semantics")
    class SnapshotEndpoint {

        @Test
        @DisplayName("GET /internal/v1/snapshots/jobs returns paged response with as_of")
        void snapshotReturnsPaged() throws Exception {
            // Create a job first
            mockMvc.perform(post("/internal/v1/dispatch/jobs")
                            .header("X-Tenant-ID", TENANT_ID)
                            .header("X-Pod-ID", "national")
                            .header("X-Request-ID", "req-snap-1")
                            .header("X-Correlation-ID", UUID.randomUUID().toString())
                            .header("Idempotency-Key", "snap-create-" + System.nanoTime())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"facilityRef\":\"FAC-SNAP\"}"))
                    .andExpect(status().isCreated());

            // Query snapshot
            mockMvc.perform(get("/internal/v1/snapshots/jobs")
                            .header("X-Tenant-ID", TENANT_ID)
                            .header("X-Pod-ID", "national")
                            .header("X-Request-ID", "req-snap-2")
                            .header("X-Correlation-ID", UUID.randomUUID().toString())
                            .param("cursor", "0")
                            .param("limit", "10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items").isArray())
                    .andExpect(jsonPath("$.asOf").exists())
                    .andExpect(jsonPath("$.limit").value(10));
        }

        @Test
        @DisplayName("Snapshot with future as_of returns all jobs")
        void snapshotWithFutureAsOf() throws Exception {
            mockMvc.perform(get("/internal/v1/snapshots/jobs")
                            .header("X-Tenant-ID", TENANT_ID)
                            .header("X-Pod-ID", "national")
                            .header("X-Request-ID", "req-snap-3")
                            .header("X-Correlation-ID", UUID.randomUUID().toString())
                            .param("as_of", "2099-12-31T23:59:59Z")
                            .param("limit", "5"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items").isArray());
        }
    }

    // ── Helpers ──

    private void assertErrorEnvelope(MvcResult result, String expectedCode) throws Exception {
        String body = result.getResponse().getContentAsString();
        assertThat(body).isNotBlank();

        JsonNode root = MAPPER.readTree(body);
        assertThat(root.has("error")).as("Response should contain 'error' field: " + body).isTrue();

        JsonNode error = root.get("error");
        assertThat(error.get("code").asText()).isEqualTo(expectedCode);
        assertThat(error.has("message")).isTrue();
        assertThat(error.has("request_id")).isTrue();
        assertThat(error.has("correlation_id")).isTrue();
    }
}
