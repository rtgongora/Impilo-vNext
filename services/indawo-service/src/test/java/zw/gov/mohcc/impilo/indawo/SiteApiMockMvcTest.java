package zw.gov.mohcc.impilo.indawo;

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
import zw.gov.mohcc.impilo.indawo.domain.OutboxEventEntity;
import zw.gov.mohcc.impilo.indawo.repository.OutboxEventRepository;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class SiteApiMockMvcTest {

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
        @DisplayName("GET /internal/v1/sites without X-Tenant-ID returns 400")
        void missingTenantIdOnGet() throws Exception {
            MvcResult result = mockMvc.perform(get("/internal/v1/sites")
                            .header("X-Pod-ID", "national")
                            .header("X-Request-ID", "req-1")
                            .header("X-Correlation-ID", "corr-1"))
                    .andExpect(status().isBadRequest())
                    .andReturn();

            assertErrorEnvelope(result, "MISSING_REQUIRED_HEADER");
        }

        @Test
        @DisplayName("GET /internal/v1/sites without X-Pod-ID returns 400")
        void missingPodIdOnGet() throws Exception {
            MvcResult result = mockMvc.perform(get("/internal/v1/sites")
                            .header("X-Tenant-ID", TENANT_ID)
                            .header("X-Request-ID", "req-1")
                            .header("X-Correlation-ID", "corr-1"))
                    .andExpect(status().isBadRequest())
                    .andReturn();

            assertErrorEnvelope(result, "MISSING_REQUIRED_HEADER");
        }

        @Test
        @DisplayName("PUT without any headers returns 400")
        void missingAllHeaders() throws Exception {
            UUID siteId = UUID.randomUUID();
            MvcResult result = mockMvc.perform(put("/internal/v1/sites/" + siteId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"Test\",\"type\":\"CLINIC\"}"))
                    .andExpect(status().isBadRequest())
                    .andReturn();

            assertErrorEnvelope(result, "MISSING_REQUIRED_HEADER");
        }
    }

    // ── B) Idempotency replay on PUT ──

    @Nested
    @DisplayName("B) Idempotency replay on PUT => same response, version stable, single outbox row")
    class IdempotencyReplay {

        @Test
        @DisplayName("PUT with same Idempotency-Key replays original response")
        void idempotentReplay() throws Exception {
            UUID siteId = UUID.randomUUID();
            String idempotencyKey = "idem-" + System.nanoTime();
            String body = "{\"name\":\"Site Alpha\",\"type\":\"CLINIC\"}";

            // First request — creates
            MvcResult first = mockMvc.perform(put("/internal/v1/sites/" + siteId)
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

            // Second request — same key, same body → replay
            MvcResult second = mockMvc.perform(put("/internal/v1/sites/" + siteId)
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

            // Version should remain stable (1, not incremented)
            JsonNode firstJson = MAPPER.readTree(firstBody);
            JsonNode secondJson = MAPPER.readTree(second.getResponse().getContentAsString());
            assertThat(secondJson.get("version").asInt()).isEqualTo(firstJson.get("version").asInt());

            // Only one outbox row for this site
            long outboxCount = outboxRepository.countByAggregateId(siteId.toString());
            assertThat(outboxCount).isEqualTo(1);
        }

        @Test
        @DisplayName("PUT without Idempotency-Key returns 400")
        void missingIdempotencyKeyOnPut() throws Exception {
            UUID siteId = UUID.randomUUID();
            MvcResult result = mockMvc.perform(put("/internal/v1/sites/" + siteId)
                            .header("X-Tenant-ID", TENANT_ID)
                            .header("X-Pod-ID", "national")
                            .header("X-Request-ID", "req-1")
                            .header("X-Correlation-ID", UUID.randomUUID().toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"Test\",\"type\":\"CLINIC\"}"))
                    .andExpect(status().isBadRequest())
                    .andReturn();

            assertErrorEnvelope(result, "IDEMPOTENCY_KEY_REQUIRED");
        }
    }

    // ── C) Snapshot endpoint ──

    @Nested
    @DisplayName("C) Snapshot endpoint returns paged items with as_of semantics")
    class SnapshotEndpoint {

        @Test
        @DisplayName("Snapshot returns paged response with as_of field")
        void snapshotReturnsPaged() throws Exception {
            // Create a site first
            UUID siteId = UUID.randomUUID();
            mockMvc.perform(put("/internal/v1/sites/" + siteId)
                            .header("X-Tenant-ID", TENANT_ID)
                            .header("X-Pod-ID", "national")
                            .header("X-Request-ID", "req-snap-1")
                            .header("X-Correlation-ID", UUID.randomUUID().toString())
                            .header("Idempotency-Key", "snap-create-" + System.nanoTime())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"Snapshot Site\",\"type\":\"HOSPITAL\"}"))
                    .andExpect(status().isCreated());

            // Query snapshot
            MvcResult result = mockMvc.perform(get("/internal/v1/snapshots/sites")
                            .header("X-Tenant-ID", TENANT_ID)
                            .header("X-Pod-ID", "national")
                            .header("X-Request-ID", "req-snap-2")
                            .header("X-Correlation-ID", UUID.randomUUID().toString())
                            .param("cursor", "0")
                            .param("limit", "10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items").isArray())
                    .andExpect(jsonPath("$.as_of").exists())
                    .andExpect(jsonPath("$.limit").value(10))
                    .andReturn();

            JsonNode body = MAPPER.readTree(result.getResponse().getContentAsString());
            assertThat(body.get("as_of")).isNotNull();
            assertThat(body.get("items").isArray()).isTrue();
        }

        @Test
        @DisplayName("Snapshot with as_of parameter filters correctly")
        void snapshotWithAsOfParam() throws Exception {
            mockMvc.perform(get("/internal/v1/snapshots/sites")
                            .header("X-Tenant-ID", TENANT_ID)
                            .header("X-Pod-ID", "national")
                            .header("X-Request-ID", "req-snap-3")
                            .header("X-Correlation-ID", UUID.randomUUID().toString())
                            .param("as_of", "2099-12-31T23:59:59Z")
                            .param("limit", "5"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.as_of").value("2099-12-31T23:59:59Z"))
                    .andExpect(jsonPath("$.items").isArray());
        }
    }

    // ── D) Status summary endpoint ───────────────────────────────────

    @Nested
    @DisplayName("D) Status-summary endpoint returns lightweight view")
    class StatusSummaryEndpoint {

        @Test
        @DisplayName("GET /internal/v1/sites/{id}/status-summary returns status fields")
        void statusSummaryReturns() throws Exception {
            UUID siteId = UUID.randomUUID();
            mockMvc.perform(put("/internal/v1/sites/" + siteId)
                            .header("X-Tenant-ID", TENANT_ID)
                            .header("X-Pod-ID", "national")
                            .header("X-Request-ID", "req-ss-1")
                            .header("X-Correlation-ID", UUID.randomUUID().toString())
                            .header("Idempotency-Key", "ss-create-" + System.nanoTime())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"Status Site\",\"type\":\"HOSPITAL\"}"))
                    .andExpect(status().isCreated());

            mockMvc.perform(get("/internal/v1/sites/" + siteId + "/status-summary")
                            .header("X-Tenant-ID", TENANT_ID)
                            .header("X-Pod-ID", "national")
                            .header("X-Request-ID", "req-ss-2")
                            .header("X-Correlation-ID", UUID.randomUUID().toString()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.siteId").value(siteId.toString()))
                    .andExpect(jsonPath("$.status").exists())
                    .andExpect(jsonPath("$.regulatoryStatus").exists())
                    .andExpect(jsonPath("$.operationalStatus").exists())
                    .andExpect(jsonPath("$.active").exists());
        }
    }

    // ── D) Outbox row has v1.1 context columns ──

    @Nested
    @DisplayName("D) Outbox row has v1.1 context columns + EventEnvelope schema_version>=1 + meta.partition_key")
    class OutboxValidation {

        @Test
        @DisplayName("Created site produces outbox row with correct v1.1 fields")
        void outboxRowHasV11Fields() throws Exception {
            UUID siteId = UUID.randomUUID();
            String correlationId = UUID.randomUUID().toString();
            String idempotencyKey = "outbox-test-" + System.nanoTime();

            mockMvc.perform(put("/internal/v1/sites/" + siteId)
                            .header("X-Tenant-ID", TENANT_ID)
                            .header("X-Pod-ID", "national")
                            .header("X-Request-ID", "req-outbox-1")
                            .header("X-Correlation-ID", correlationId)
                            .header("Idempotency-Key", idempotencyKey)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"Outbox Site\",\"type\":\"WAREHOUSE\"}"))
                    .andExpect(status().isCreated());

            List<OutboxEventEntity> outboxRows = outboxRepository
                    .findByAggregateIdAndEventType(siteId.toString(), "impilo.indawo.site.created.v1");

            assertThat(outboxRows).hasSize(1);
            OutboxEventEntity row = outboxRows.get(0);

            // v1.1 context columns
            assertThat(row.getEventId()).isNotNull();
            assertThat(row.getAggregateType()).isEqualTo("Site");
            assertThat(row.getAggregateId()).isEqualTo(siteId.toString());
            assertThat(row.getEventType()).isEqualTo("impilo.indawo.site.created.v1");
            assertThat(row.getCorrelationId()).isNotNull();
            assertThat(row.getCorrelationId().toString()).isEqualTo(correlationId);
            assertThat(row.getIdempotencyKey()).isEqualTo(idempotencyKey);
            assertThat(row.getProducer()).isEqualTo("indawo-service");
            assertThat(row.getTenantId()).isNotNull();
            assertThat(row.getPodId()).isEqualTo("national");
            assertThat(row.getSubjectId()).isEqualTo(siteId.toString());
            assertThat(row.getSubjectType()).isEqualTo("Site");
            assertThat(row.getOccurredAt()).isNotNull();

            // schema_version >= 1
            int schemaVersion = Integer.parseInt(row.getSchemaVersion());
            assertThat(schemaVersion).isGreaterThanOrEqualTo(1);

            // meta.partition_key = site_id
            assertThat(row.getPartitionKey()).isEqualTo(siteId.toString());

            // Payload contains delta
            JsonNode payload = MAPPER.readTree(row.getPayloadJson());
            assertThat(payload.get("op").asText()).isEqualTo("CREATE");
            assertThat(payload.get("after")).isNotNull();
            assertThat(payload.get("changed_fields").isArray()).isTrue();
        }

        @Test
        @DisplayName("Updated site produces outbox row with UPDATE delta")
        void outboxRowOnUpdate() throws Exception {
            UUID siteId = UUID.randomUUID();

            // Create first
            mockMvc.perform(put("/internal/v1/sites/" + siteId)
                            .header("X-Tenant-ID", TENANT_ID)
                            .header("X-Pod-ID", "national")
                            .header("X-Request-ID", "req-upd-1")
                            .header("X-Correlation-ID", UUID.randomUUID().toString())
                            .header("Idempotency-Key", "create-" + System.nanoTime())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"Original Name\",\"type\":\"CLINIC\"}"))
                    .andExpect(status().isCreated());

            // Update
            mockMvc.perform(put("/internal/v1/sites/" + siteId)
                            .header("X-Tenant-ID", TENANT_ID)
                            .header("X-Pod-ID", "national")
                            .header("X-Request-ID", "req-upd-2")
                            .header("X-Correlation-ID", UUID.randomUUID().toString())
                            .header("Idempotency-Key", "update-" + System.nanoTime())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"Updated Name\",\"type\":\"CLINIC\"}"))
                    .andExpect(status().isOk());

            List<OutboxEventEntity> updateRows = outboxRepository
                    .findByAggregateIdAndEventType(siteId.toString(), "impilo.indawo.site.updated.v1");

            assertThat(updateRows).hasSize(1);
            OutboxEventEntity row = updateRows.get(0);

            JsonNode payload = MAPPER.readTree(row.getPayloadJson());
            assertThat(payload.get("op").asText()).isEqualTo("UPDATE");
            assertThat(payload.get("before")).isNotNull();
            assertThat(payload.get("after")).isNotNull();
            assertThat(payload.get("partition_key")).isNull(); // partition_key is on entity, not in payload
            assertThat(row.getPartitionKey()).isEqualTo(siteId.toString());
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
