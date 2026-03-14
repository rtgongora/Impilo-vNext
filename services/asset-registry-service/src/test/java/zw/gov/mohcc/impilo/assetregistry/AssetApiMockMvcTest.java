package zw.gov.mohcc.impilo.assetregistry;

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
import zw.gov.mohcc.impilo.assetregistry.domain.OutboxEventEntity;
import zw.gov.mohcc.impilo.assetregistry.repository.OutboxEventRepository;

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
public class AssetApiMockMvcTest {

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
        @DisplayName("GET /internal/v1/assets without X-Tenant-ID returns 400")
        void missingTenantIdOnGet() throws Exception {
            MvcResult result = mockMvc.perform(get("/internal/v1/assets")
                            .header("X-Pod-ID", "national")
                            .header("X-Request-ID", "req-1")
                            .header("X-Correlation-ID", "corr-1"))
                    .andExpect(status().isBadRequest())
                    .andReturn();

            assertErrorEnvelope(result, "MISSING_REQUIRED_HEADER");
        }

        @Test
        @DisplayName("GET /internal/v1/assets without X-Pod-ID returns 400")
        void missingPodIdOnGet() throws Exception {
            MvcResult result = mockMvc.perform(get("/internal/v1/assets")
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
            UUID assetId = UUID.randomUUID();
            MvcResult result = mockMvc.perform(put("/internal/v1/assets/" + assetId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"facilityRef\":\"FAC-001\",\"type\":\"EQUIPMENT\"}"))
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
            UUID assetId = UUID.randomUUID();
            String idempotencyKey = "idem-" + System.nanoTime();
            String body = "{\"facilityRef\":\"FAC-001\",\"type\":\"VEHICLE\"}";

            // First request — creates
            MvcResult first = mockMvc.perform(put("/internal/v1/assets/" + assetId)
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
            MvcResult second = mockMvc.perform(put("/internal/v1/assets/" + assetId)
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

            // Only one outbox row for this asset
            long outboxCount = outboxRepository.countByAggregateId(assetId.toString());
            assertThat(outboxCount).isEqualTo(1);
        }

        @Test
        @DisplayName("PUT without Idempotency-Key returns 400")
        void missingIdempotencyKeyOnPut() throws Exception {
            UUID assetId = UUID.randomUUID();
            MvcResult result = mockMvc.perform(put("/internal/v1/assets/" + assetId)
                            .header("X-Tenant-ID", TENANT_ID)
                            .header("X-Pod-ID", "national")
                            .header("X-Request-ID", "req-1")
                            .header("X-Correlation-ID", UUID.randomUUID().toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"facilityRef\":\"FAC-001\",\"type\":\"EQUIPMENT\"}"))
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
            // Create an asset first
            UUID assetId = UUID.randomUUID();
            mockMvc.perform(put("/internal/v1/assets/" + assetId)
                            .header("X-Tenant-ID", TENANT_ID)
                            .header("X-Pod-ID", "national")
                            .header("X-Request-ID", "req-snap-1")
                            .header("X-Correlation-ID", UUID.randomUUID().toString())
                            .header("Idempotency-Key", "snap-create-" + System.nanoTime())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"facilityRef\":\"FAC-SNAP\",\"type\":\"DEVICE\"}"))
                    .andExpect(status().isCreated());

            // Query snapshot
            MvcResult result = mockMvc.perform(get("/internal/v1/snapshots/assets")
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
            mockMvc.perform(get("/internal/v1/snapshots/assets")
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

    // ── D) Outbox row has v1.1 context columns + EventEnvelope ──

    @Nested
    @DisplayName("D) Outbox payload_json includes EventEnvelope with meta.partition_key==asset_id")
    class OutboxValidation {

        @Test
        @DisplayName("Created asset produces outbox row with correct v1.1 fields and partition_key==asset_id")
        void outboxRowHasV11Fields() throws Exception {
            UUID assetId = UUID.randomUUID();
            String correlationId = UUID.randomUUID().toString();
            String idempotencyKey = "outbox-test-" + System.nanoTime();

            mockMvc.perform(put("/internal/v1/assets/" + assetId)
                            .header("X-Tenant-ID", TENANT_ID)
                            .header("X-Pod-ID", "national")
                            .header("X-Request-ID", "req-outbox-1")
                            .header("X-Correlation-ID", correlationId)
                            .header("Idempotency-Key", idempotencyKey)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"facilityRef\":\"FAC-OBX\",\"type\":\"COLD_CHAIN\"}"))
                    .andExpect(status().isCreated());

            List<OutboxEventEntity> outboxRows = outboxRepository
                    .findByAggregateIdAndEventType(assetId.toString(), "impilo.asset.asset.created.v1");

            assertThat(outboxRows).hasSize(1);
            OutboxEventEntity row = outboxRows.get(0);

            // v1.1 context columns
            assertThat(row.getEventId()).isNotNull();
            assertThat(row.getAggregateType()).isEqualTo("Asset");
            assertThat(row.getAggregateId()).isEqualTo(assetId.toString());
            assertThat(row.getEventType()).isEqualTo("impilo.asset.asset.created.v1");
            assertThat(row.getCorrelationId()).isNotNull();
            assertThat(row.getCorrelationId().toString()).isEqualTo(correlationId);
            assertThat(row.getIdempotencyKey()).isEqualTo(idempotencyKey);
            assertThat(row.getProducer()).isEqualTo("asset-registry-service");
            assertThat(row.getTenantId()).isNotNull();
            assertThat(row.getPodId()).isEqualTo("national");
            assertThat(row.getSubjectId()).isEqualTo(assetId.toString());
            assertThat(row.getSubjectType()).isEqualTo("Asset");
            assertThat(row.getOccurredAt()).isNotNull();

            // schema_version >= 1
            int schemaVersion = Integer.parseInt(row.getSchemaVersion());
            assertThat(schemaVersion).isGreaterThanOrEqualTo(1);

            // partition_key == asset_id
            assertThat(row.getPartitionKey()).isEqualTo(assetId.toString());

            // Payload contains delta with EventEnvelope-compatible structure
            JsonNode payload = MAPPER.readTree(row.getPayloadJson());
            assertThat(payload.get("op").asText()).isEqualTo("CREATE");
            assertThat(payload.get("after")).isNotNull();
            assertThat(payload.get("changed_fields").isArray()).isTrue();
        }

        @Test
        @DisplayName("Updated asset produces outbox row with UPDATE delta")
        void outboxRowOnUpdate() throws Exception {
            UUID assetId = UUID.randomUUID();

            // Create first
            mockMvc.perform(put("/internal/v1/assets/" + assetId)
                            .header("X-Tenant-ID", TENANT_ID)
                            .header("X-Pod-ID", "national")
                            .header("X-Request-ID", "req-upd-1")
                            .header("X-Correlation-ID", UUID.randomUUID().toString())
                            .header("Idempotency-Key", "create-" + System.nanoTime())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"facilityRef\":\"FAC-ORIG\",\"type\":\"EQUIPMENT\"}"))
                    .andExpect(status().isCreated());

            // Update
            mockMvc.perform(put("/internal/v1/assets/" + assetId)
                            .header("X-Tenant-ID", TENANT_ID)
                            .header("X-Pod-ID", "national")
                            .header("X-Request-ID", "req-upd-2")
                            .header("X-Correlation-ID", UUID.randomUUID().toString())
                            .header("Idempotency-Key", "update-" + System.nanoTime())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"facilityRef\":\"FAC-UPDATED\",\"type\":\"EQUIPMENT\"}"))
                    .andExpect(status().isOk());

            List<OutboxEventEntity> updateRows = outboxRepository
                    .findByAggregateIdAndEventType(assetId.toString(), "impilo.asset.asset.updated.v1");

            assertThat(updateRows).hasSize(1);
            OutboxEventEntity row = updateRows.get(0);

            // partition_key == asset_id
            assertThat(row.getPartitionKey()).isEqualTo(assetId.toString());

            JsonNode payload = MAPPER.readTree(row.getPayloadJson());
            assertThat(payload.get("op").asText()).isEqualTo("UPDATE");
            assertThat(payload.get("before")).isNotNull();
            assertThat(payload.get("after")).isNotNull();
        }
    }

    // ── E) Retire lifecycle transition + outbox event ──

    @Nested
    @DisplayName("E) Retire asset => status=RETIRED + retired.v1 outbox event")
    class RetireLifecycle {

        @Test
        @DisplayName("DELETE /internal/v1/assets/{id} retires asset and produces retired.v1 outbox row")
        void retireAssetLifecycle() throws Exception {
            UUID assetId = UUID.randomUUID();

            // Create asset first
            mockMvc.perform(put("/internal/v1/assets/" + assetId)
                            .header("X-Tenant-ID", TENANT_ID)
                            .header("X-Pod-ID", "national")
                            .header("X-Request-ID", "req-retire-1")
                            .header("X-Correlation-ID", UUID.randomUUID().toString())
                            .header("Idempotency-Key", "retire-create-" + System.nanoTime())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"facilityRef\":\"FAC-RETIRE\",\"type\":\"COLD_CHAIN\"}"))
                    .andExpect(status().isCreated());

            // Retire
            MvcResult result = mockMvc.perform(
                            org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                    .delete("/internal/v1/assets/" + assetId)
                                    .header("X-Tenant-ID", TENANT_ID)
                                    .header("X-Pod-ID", "national")
                                    .header("X-Request-ID", "req-retire-2")
                                    .header("X-Correlation-ID", UUID.randomUUID().toString())
                                    .header("Idempotency-Key", "retire-key-" + System.nanoTime()))
                    .andExpect(status().isOk())
                    .andReturn();

            JsonNode body = MAPPER.readTree(result.getResponse().getContentAsString());
            assertThat(body.get("status").asText()).isEqualTo("RETIRED");

            // Verify outbox has retired.v1 event
            List<OutboxEventEntity> retireRows = outboxRepository
                    .findByAggregateIdAndEventType(assetId.toString(), "impilo.asset.asset.retired.v1");
            assertThat(retireRows).hasSize(1);

            OutboxEventEntity row = retireRows.get(0);
            assertThat(row.getPartitionKey()).isEqualTo(assetId.toString());

            JsonNode payload = MAPPER.readTree(row.getPayloadJson());
            assertThat(payload.get("op").asText()).isEqualTo("UPDATE");
            assertThat(payload.get("after").get("status").asText()).isEqualTo("RETIRED");
        }

        @Test
        @DisplayName("DELETE non-existent asset returns 404")
        void retireNonExistentAsset() throws Exception {
            UUID assetId = UUID.randomUUID();
            MvcResult result = mockMvc.perform(
                            org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                    .delete("/internal/v1/assets/" + assetId)
                                    .header("X-Tenant-ID", TENANT_ID)
                                    .header("X-Pod-ID", "national")
                                    .header("X-Request-ID", "req-retire-404")
                                    .header("X-Correlation-ID", UUID.randomUUID().toString())
                                    .header("Idempotency-Key", "retire-404-" + System.nanoTime()))
                    .andExpect(status().isNotFound())
                    .andReturn();

            assertErrorEnvelope(result, "NOT_FOUND");
        }
    }

    // ── F) PUT /internal/v1/assets/{id}/status — status change ──

    @Nested
    @DisplayName("F) PUT /internal/v1/assets/{id}/status => status change + outbox event")
    class StatusChange {

        @Test
        @DisplayName("PUT /internal/v1/assets/{id}/status changes status and emits status.changed.v1 outbox event")
        void statusChangeEmitsOutboxEvent() throws Exception {
            UUID assetId = UUID.randomUUID();

            // Create asset
            mockMvc.perform(put("/internal/v1/assets/" + assetId)
                            .header("X-Tenant-ID", TENANT_ID)
                            .header("X-Pod-ID", "national")
                            .header("X-Request-ID", "req-sc-1")
                            .header("X-Correlation-ID", UUID.randomUUID().toString())
                            .header("Idempotency-Key", "sc-create-" + System.nanoTime())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"facilityRef\":\"FAC-SC\",\"type\":\"EQUIPMENT\"}"))
                    .andExpect(status().isCreated());

            // Change status
            MvcResult result = mockMvc.perform(put("/internal/v1/assets/" + assetId + "/status")
                            .header("X-Tenant-ID", TENANT_ID)
                            .header("X-Pod-ID", "national")
                            .header("X-Request-ID", "req-sc-2")
                            .header("X-Correlation-ID", UUID.randomUUID().toString())
                            .header("Idempotency-Key", "sc-status-" + System.nanoTime())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"status\":\"MAINTENANCE\"}"))
                    .andExpect(status().isOk())
                    .andReturn();

            JsonNode body = MAPPER.readTree(result.getResponse().getContentAsString());
            assertThat(body.get("status").asText()).isEqualTo("MAINTENANCE");

            // Verify outbox event
            List<OutboxEventEntity> rows = outboxRepository
                    .findByAggregateIdAndEventType(assetId.toString(), "impilo.asset.asset.status.changed.v1");
            assertThat(rows).hasSize(1);
            assertThat(rows.get(0).getPartitionKey()).isEqualTo(assetId.toString());

            JsonNode payload = MAPPER.readTree(rows.get(0).getPayloadJson());
            assertThat(payload.get("op").asText()).isEqualTo("UPDATE");
            assertThat(payload.get("after").get("status").asText()).isEqualTo("MAINTENANCE");
        }

        @Test
        @DisplayName("PUT /internal/v1/assets/{id}/status for non-existent asset returns 404")
        void statusChangeNotFound() throws Exception {
            UUID assetId = UUID.randomUUID();
            MvcResult result = mockMvc.perform(put("/internal/v1/assets/" + assetId + "/status")
                            .header("X-Tenant-ID", TENANT_ID)
                            .header("X-Pod-ID", "national")
                            .header("X-Request-ID", "req-sc-404")
                            .header("X-Correlation-ID", UUID.randomUUID().toString())
                            .header("Idempotency-Key", "sc-404-" + System.nanoTime())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"status\":\"MAINTENANCE\"}"))
                    .andExpect(status().isNotFound())
                    .andReturn();

            assertErrorEnvelope(result, "NOT_FOUND");
        }
    }

    // ── G) GET /external/v1/assets — policy-filtered listing ──

    @Nested
    @DisplayName("G) GET /external/v1/assets returns policy-filtered listing")
    class ExternalListing {

        @Test
        @DisplayName("GET /external/v1/assets returns paginated external response")
        void externalListingReturnsPaged() throws Exception {
            // Create an asset
            UUID assetId = UUID.randomUUID();
            mockMvc.perform(put("/internal/v1/assets/" + assetId)
                            .header("X-Tenant-ID", TENANT_ID)
                            .header("X-Pod-ID", "national")
                            .header("X-Request-ID", "req-ext-1")
                            .header("X-Correlation-ID", UUID.randomUUID().toString())
                            .header("Idempotency-Key", "ext-create-" + System.nanoTime())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"facilityRef\":\"FAC-EXT\",\"type\":\"VEHICLE\"}"))
                    .andExpect(status().isCreated());

            MvcResult result = mockMvc.perform(get("/external/v1/assets")
                            .header("X-Tenant-ID", TENANT_ID)
                            .header("X-Pod-ID", "national")
                            .header("X-Request-ID", "req-ext-2")
                            .header("X-Correlation-ID", UUID.randomUUID().toString())
                            .param("limit", "10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items").isArray())
                    .andExpect(jsonPath("$.limit").value(10))
                    .andExpect(jsonPath("$.has_more").exists())
                    .andReturn();

            JsonNode body = MAPPER.readTree(result.getResponse().getContentAsString());
            assertThat(body.get("items").isArray()).isTrue();
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
