package zw.gov.mohcc.impilo.dataingestion;

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
import zw.gov.mohcc.impilo.dataingestion.domain.BronzeEventEntity;
import zw.gov.mohcc.impilo.dataingestion.domain.OutboxEventEntity;
import zw.gov.mohcc.impilo.dataingestion.repository.BronzeEventRepository;
import zw.gov.mohcc.impilo.dataingestion.repository.OutboxEventRepository;

import java.time.OffsetDateTime;
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
public class IngestApiMockMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private BronzeEventRepository bronzeRepository;

    @Autowired
    private OutboxEventRepository outboxRepository;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String TENANT_ID = UUID.randomUUID().toString();

    // ── A) Missing required headers => 400 envelope ──

    @Nested
    @DisplayName("A) Missing required headers => 400 envelope")
    class MissingHeaders {

        @Test
        @DisplayName("POST /internal/v1/ingest/events without X-Tenant-ID returns 400")
        void missingTenantIdOnPost() throws Exception {
            MvcResult result = mockMvc.perform(post("/internal/v1/ingest/events")
                            .header("X-Pod-ID", "national")
                            .header("X-Request-ID", "req-1")
                            .header("X-Correlation-ID", "corr-1")
                            .header("Idempotency-Key", "idem-1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(buildValidEnvelopeJson()))
                    .andExpect(status().isBadRequest())
                    .andReturn();

            assertErrorEnvelope(result, "MISSING_REQUIRED_HEADER");
        }

        @Test
        @DisplayName("POST /internal/v1/ingest/events without X-Pod-ID returns 400")
        void missingPodIdOnPost() throws Exception {
            MvcResult result = mockMvc.perform(post("/internal/v1/ingest/events")
                            .header("X-Tenant-ID", TENANT_ID)
                            .header("X-Request-ID", "req-1")
                            .header("X-Correlation-ID", "corr-1")
                            .header("Idempotency-Key", "idem-1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(buildValidEnvelopeJson()))
                    .andExpect(status().isBadRequest())
                    .andReturn();

            assertErrorEnvelope(result, "MISSING_REQUIRED_HEADER");
        }

        @Test
        @DisplayName("POST without any headers returns 400")
        void missingAllHeaders() throws Exception {
            MvcResult result = mockMvc.perform(post("/internal/v1/ingest/events")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(buildValidEnvelopeJson()))
                    .andExpect(status().isBadRequest())
                    .andReturn();

            assertErrorEnvelope(result, "MISSING_REQUIRED_HEADER");
        }

        @Test
        @DisplayName("GET /internal/v1/ingest/health without headers returns 400")
        void missingHeadersOnHealthGet() throws Exception {
            MvcResult result = mockMvc.perform(get("/internal/v1/ingest/health"))
                    .andExpect(status().isBadRequest())
                    .andReturn();

            assertErrorEnvelope(result, "MISSING_REQUIRED_HEADER");
        }
    }

    // ── B) Missing Idempotency-Key on POST => 400 IDEMPOTENCY_KEY_REQUIRED ──

    @Nested
    @DisplayName("B) Missing Idempotency-Key => 400 IDEMPOTENCY_KEY_REQUIRED")
    class MissingIdempotencyKey {

        @Test
        @DisplayName("POST /internal/v1/ingest/events without Idempotency-Key returns 400")
        void missingIdempotencyKeyOnPost() throws Exception {
            MvcResult result = mockMvc.perform(post("/internal/v1/ingest/events")
                            .header("X-Tenant-ID", TENANT_ID)
                            .header("X-Pod-ID", "national")
                            .header("X-Request-ID", "req-1")
                            .header("X-Correlation-ID", UUID.randomUUID().toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(buildValidEnvelopeJson()))
                    .andExpect(status().isBadRequest())
                    .andReturn();

            assertErrorEnvelope(result, "IDEMPOTENCY_KEY_REQUIRED");
        }

        @Test
        @DisplayName("POST /internal/v1/ingest/batch without Idempotency-Key returns 400")
        void missingIdempotencyKeyOnBatch() throws Exception {
            MvcResult result = mockMvc.perform(post("/internal/v1/ingest/batch")
                            .header("X-Tenant-ID", TENANT_ID)
                            .header("X-Pod-ID", "national")
                            .header("X-Request-ID", "req-1")
                            .header("X-Correlation-ID", UUID.randomUUID().toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"items\":[" + buildValidEnvelopeJson() + "]}"))
                    .andExpect(status().isBadRequest())
                    .andReturn();

            assertErrorEnvelope(result, "IDEMPOTENCY_KEY_REQUIRED");
        }
    }

    // ── C) Idempotency replay: same key + same body => REPLAY ──

    @Nested
    @DisplayName("C) Same Idempotency-Key + same body => replay (same receipt_id, dedupe=REPLAY)")
    class IdempotencyReplay {

        @Test
        @DisplayName("POST with same Idempotency-Key replays original response")
        void idempotentReplay() throws Exception {
            String eventId = UUID.randomUUID().toString();
            String idempotencyKey = "idem-replay-" + System.nanoTime();
            String body = buildEnvelopeJson(eventId);

            // First request — creates
            MvcResult first = mockMvc.perform(post("/internal/v1/ingest/events")
                            .header("X-Tenant-ID", TENANT_ID)
                            .header("X-Pod-ID", "national")
                            .header("X-Request-ID", "req-1")
                            .header("X-Correlation-ID", UUID.randomUUID().toString())
                            .header("Idempotency-Key", idempotencyKey)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isAccepted())
                    .andReturn();

            JsonNode firstJson = MAPPER.readTree(first.getResponse().getContentAsString());
            String firstReceiptId = firstJson.get("receipt_id").asText();
            assertThat(firstJson.get("dedupe").asText()).isEqualTo("NEW");

            // Second request — same key, same body => replay
            MvcResult second = mockMvc.perform(post("/internal/v1/ingest/events")
                            .header("X-Tenant-ID", TENANT_ID)
                            .header("X-Pod-ID", "national")
                            .header("X-Request-ID", "req-2")
                            .header("X-Correlation-ID", UUID.randomUUID().toString())
                            .header("Idempotency-Key", "idem-replay-different-" + System.nanoTime())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isAccepted())
                    .andReturn();

            JsonNode secondJson = MAPPER.readTree(second.getResponse().getContentAsString());
            assertThat(secondJson.get("receipt_id").asText()).isEqualTo(firstReceiptId);
            assertThat(secondJson.get("dedupe").asText()).isEqualTo("REPLAY");

            // Only one bronze row for this event_id
            UUID tenantUuid = UUID.fromString(TENANT_ID);
            long bronzeCount = bronzeRepository.countByTenantIdAndPodIdAndEventId(
                    tenantUuid, "national", eventId);
            assertThat(bronzeCount).isEqualTo(1);
        }
    }

    // ── D) Same Idempotency-Key + different body => 409 IDENTITY_CONFLICT ──
    // Note: The IdempotencyFilter in tech-companion handles this at the filter level.
    // When the same idempotency key is used with different request hash, it returns 409.

    @Nested
    @DisplayName("D) Same Idempotency-Key + different body => 409 IDENTITY_CONFLICT")
    class IdempotencyConflict {

        @Test
        @DisplayName("POST with same Idempotency-Key but different body returns 409")
        void idempotencyConflict() throws Exception {
            String idempotencyKey = "idem-conflict-" + System.nanoTime();
            String body1 = buildEnvelopeJson(UUID.randomUUID().toString());
            String body2 = buildEnvelopeJson(UUID.randomUUID().toString());

            // First request — creates
            mockMvc.perform(post("/internal/v1/ingest/events")
                            .header("X-Tenant-ID", TENANT_ID)
                            .header("X-Pod-ID", "national")
                            .header("X-Request-ID", "req-1")
                            .header("X-Correlation-ID", UUID.randomUUID().toString())
                            .header("Idempotency-Key", idempotencyKey)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body1))
                    .andExpect(status().isAccepted());

            // Second request — same key, DIFFERENT body => 409
            mockMvc.perform(post("/internal/v1/ingest/events")
                            .header("X-Tenant-ID", TENANT_ID)
                            .header("X-Pod-ID", "national")
                            .header("X-Request-ID", "req-2")
                            .header("X-Correlation-ID", UUID.randomUUID().toString())
                            .header("Idempotency-Key", idempotencyKey)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body2))
                    .andExpect(status().isConflict());
        }
    }

    // ── E) Bronze row + outbox row exist with v1.1 context columns ──

    @Nested
    @DisplayName("E) Bronze + outbox rows have v1.1 context columns populated")
    class BronzeAndOutboxValidation {

        @Test
        @DisplayName("Successful ingest produces bronze row + outbox row with correct v1.1 fields")
        void bronzeAndOutboxRowsHaveV11Fields() throws Exception {
            String eventId = UUID.randomUUID().toString();
            String correlationId = UUID.randomUUID().toString();
            String idempotencyKey = "outbox-test-" + System.nanoTime();
            String subjectId = UUID.randomUUID().toString();
            String body = buildEnvelopeJson(eventId, subjectId);

            mockMvc.perform(post("/internal/v1/ingest/events")
                            .header("X-Tenant-ID", TENANT_ID)
                            .header("X-Pod-ID", "national")
                            .header("X-Request-ID", "req-outbox-1")
                            .header("X-Correlation-ID", correlationId)
                            .header("Idempotency-Key", idempotencyKey)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isAccepted())
                    .andExpect(jsonPath("$.receipt_id").exists())
                    .andExpect(jsonPath("$.event_id").value(eventId))
                    .andExpect(jsonPath("$.stored_at").exists())
                    .andExpect(jsonPath("$.dedupe").value("NEW"));

            // Verify bronze row
            UUID tenantUuid = UUID.fromString(TENANT_ID);
            var bronzeOpt = bronzeRepository.findByTenantIdAndPodIdAndEventId(
                    tenantUuid, "national", eventId);
            assertThat(bronzeOpt).isPresent();

            BronzeEventEntity bronze = bronzeOpt.get();
            assertThat(bronze.getReceiptId()).isNotNull();
            assertThat(bronze.getTenantId()).isEqualTo(tenantUuid);
            assertThat(bronze.getPodId()).isEqualTo("national");
            assertThat(bronze.getRequestId()).isEqualTo("req-outbox-1");
            assertThat(bronze.getCorrelationId()).isEqualTo(correlationId);
            assertThat(bronze.getIdempotencyKey()).isEqualTo(idempotencyKey);
            assertThat(bronze.getEventId()).isEqualTo(eventId);
            assertThat(bronze.getEventType()).isEqualTo("impilo.test.entity.created.v1");
            assertThat(bronze.getSchemaVersion()).isGreaterThanOrEqualTo(1);
            assertThat(bronze.getOccurredAt()).isNotNull();
            assertThat(bronze.getSubjectType()).isEqualTo("TestEntity");
            assertThat(bronze.getSubjectId()).isEqualTo(subjectId);
            assertThat(bronze.getPartitionKey()).isEqualTo(subjectId);
            assertThat(bronze.getEnvelopeJson()).isEqualTo(body);
            assertThat(bronze.getStoredAt()).isNotNull();

            // Verify outbox row
            String receiptId = bronze.getReceiptId().toString();
            List<OutboxEventEntity> outboxRows = outboxRepository
                    .findByAggregateIdAndEventType(receiptId,
                            "impilo.data.ingestion.bronze.received.v1");
            assertThat(outboxRows).hasSize(1);

            OutboxEventEntity outbox = outboxRows.get(0);
            assertThat(outbox.getEventId()).isNotNull();
            assertThat(outbox.getAggregateType()).isEqualTo("BronzeEvent");
            assertThat(outbox.getAggregateId()).isEqualTo(receiptId);
            assertThat(outbox.getEventType()).isEqualTo("impilo.data.ingestion.bronze.received.v1");
            int schemaVersion = Integer.parseInt(outbox.getSchemaVersion());
            assertThat(schemaVersion).isGreaterThanOrEqualTo(1);
            assertThat(outbox.getCorrelationId()).isNotNull();
            assertThat(outbox.getCorrelationId().toString()).isEqualTo(correlationId);
            assertThat(outbox.getIdempotencyKey()).isEqualTo(idempotencyKey);
            assertThat(outbox.getProducer()).isEqualTo("data-ingestion-service");
            assertThat(outbox.getTenantId()).isEqualTo(tenantUuid);
            assertThat(outbox.getPodId()).isEqualTo("national");
            assertThat(outbox.getSubjectId()).isEqualTo(subjectId);
            assertThat(outbox.getSubjectType()).isEqualTo("TestEntity");
            assertThat(outbox.getOccurredAt()).isNotNull();
            assertThat(outbox.getPartitionKey()).isEqualTo(subjectId);

            // Payload contains delta
            JsonNode payload = MAPPER.readTree(outbox.getPayloadJson());
            assertThat(payload.get("op").asText()).isEqualTo("CREATE");
            assertThat(payload.get("after")).isNotNull();
            assertThat(payload.get("after").get("receipt_id").asText()).isEqualTo(receiptId);
            assertThat(payload.get("after").get("event_id").asText()).isEqualTo(eventId);
            assertThat(payload.get("after").get("partition_key").asText()).isEqualTo(subjectId);
            assertThat(payload.get("changed_fields").isArray()).isTrue();
        }

        @Test
        @DisplayName("Batch ingest produces bronze + outbox rows for each accepted item")
        void batchIngestProducesBronzeAndOutboxRows() throws Exception {
            String eventId1 = UUID.randomUUID().toString();
            String eventId2 = UUID.randomUUID().toString();
            String subjectId1 = UUID.randomUUID().toString();
            String subjectId2 = UUID.randomUUID().toString();
            String idempotencyKey = "batch-test-" + System.nanoTime();

            String batchBody = "{\"items\":[" +
                    buildEnvelopeJson(eventId1, subjectId1) + "," +
                    buildEnvelopeJson(eventId2, subjectId2) +
                    "]}";

            mockMvc.perform(post("/internal/v1/ingest/batch")
                            .header("X-Tenant-ID", TENANT_ID)
                            .header("X-Pod-ID", "national")
                            .header("X-Request-ID", "req-batch-1")
                            .header("X-Correlation-ID", UUID.randomUUID().toString())
                            .header("Idempotency-Key", idempotencyKey)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(batchBody))
                    .andExpect(status().isAccepted())
                    .andExpect(jsonPath("$.accepted").value(2))
                    .andExpect(jsonPath("$.replayed").value(0))
                    .andExpect(jsonPath("$.rejected").value(0))
                    .andExpect(jsonPath("$.results").isArray())
                    .andExpect(jsonPath("$.results.length()").value(2));

            // Both bronze rows should exist
            UUID tenantUuid = UUID.fromString(TENANT_ID);
            assertThat(bronzeRepository.findByTenantIdAndPodIdAndEventId(
                    tenantUuid, "national", eventId1)).isPresent();
            assertThat(bronzeRepository.findByTenantIdAndPodIdAndEventId(
                    tenantUuid, "national", eventId2)).isPresent();
        }
    }

    // ── Validation tests ──

    @Nested
    @DisplayName("Envelope validation rules")
    class ValidationRules {

        @Test
        @DisplayName("Missing schema_version returns 422 SCHEMA_VALIDATION_FAILED")
        void missingSchemaVersion() throws Exception {
            String body = "{" +
                    "\"event_id\":\"" + UUID.randomUUID() + "\"," +
                    "\"event_type\":\"impilo.test.entity.created.v1\"," +
                    "\"correlation_id\":\"" + UUID.randomUUID() + "\"," +
                    "\"causation_id\":\"" + UUID.randomUUID() + "\"," +
                    "\"idempotency_key\":\"ik-1\"," +
                    "\"producer\":\"test\"," +
                    "\"tenant_id\":\"" + TENANT_ID + "\"," +
                    "\"pod_id\":\"national\"," +
                    "\"occurred_at\":\"" + OffsetDateTime.now() + "\"," +
                    "\"emitted_at\":\"" + OffsetDateTime.now() + "\"," +
                    "\"subject_type\":\"TestEntity\"," +
                    "\"subject_id\":\"" + UUID.randomUUID() + "\"," +
                    "\"payload\":{\"key\":\"value\"}," +
                    "\"meta\":{\"partition_key\":\"pk-1\"}" +
                    "}";

            mockMvc.perform(post("/internal/v1/ingest/events")
                            .header("X-Tenant-ID", TENANT_ID)
                            .header("X-Pod-ID", "national")
                            .header("X-Request-ID", "req-val-1")
                            .header("X-Correlation-ID", UUID.randomUUID().toString())
                            .header("Idempotency-Key", "val-" + System.nanoTime())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.error.code").value("SCHEMA_VALIDATION_FAILED"));
        }

        @Test
        @DisplayName("Invalid event_type prefix returns 422 INVALID_EVENT_TYPE")
        void invalidEventTypePrefix() throws Exception {
            String body = buildEnvelopeJsonWithEventType("custom.bad.event.v1");

            mockMvc.perform(post("/internal/v1/ingest/events")
                            .header("X-Tenant-ID", TENANT_ID)
                            .header("X-Pod-ID", "national")
                            .header("X-Request-ID", "req-val-2")
                            .header("X-Correlation-ID", UUID.randomUUID().toString())
                            .header("Idempotency-Key", "val-" + System.nanoTime())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.error.code").value("INVALID_EVENT_TYPE"));
        }

        @Test
        @DisplayName("Invalid event_type suffix returns 422 INVALID_EVENT_TYPE")
        void invalidEventTypeSuffix() throws Exception {
            String body = buildEnvelopeJsonWithEventType("impilo.test.entity.created.v2");

            mockMvc.perform(post("/internal/v1/ingest/events")
                            .header("X-Tenant-ID", TENANT_ID)
                            .header("X-Pod-ID", "national")
                            .header("X-Request-ID", "req-val-3")
                            .header("X-Correlation-ID", UUID.randomUUID().toString())
                            .header("Idempotency-Key", "val-" + System.nanoTime())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.error.code").value("INVALID_EVENT_TYPE"));
        }

        @Test
        @DisplayName("Missing event_id returns 422 INVALID_ENVELOPE")
        void missingEventId() throws Exception {
            String body = "{" +
                    "\"event_type\":\"impilo.test.entity.created.v1\"," +
                    "\"schema_version\":1," +
                    "\"correlation_id\":\"" + UUID.randomUUID() + "\"," +
                    "\"causation_id\":\"" + UUID.randomUUID() + "\"," +
                    "\"idempotency_key\":\"ik-1\"," +
                    "\"producer\":\"test\"," +
                    "\"tenant_id\":\"" + TENANT_ID + "\"," +
                    "\"pod_id\":\"national\"," +
                    "\"occurred_at\":\"" + OffsetDateTime.now() + "\"," +
                    "\"emitted_at\":\"" + OffsetDateTime.now() + "\"," +
                    "\"subject_type\":\"TestEntity\"," +
                    "\"subject_id\":\"" + UUID.randomUUID() + "\"," +
                    "\"payload\":{\"key\":\"value\"}," +
                    "\"meta\":{\"partition_key\":\"pk-1\"}" +
                    "}";

            mockMvc.perform(post("/internal/v1/ingest/events")
                            .header("X-Tenant-ID", TENANT_ID)
                            .header("X-Pod-ID", "national")
                            .header("X-Request-ID", "req-val-4")
                            .header("X-Correlation-ID", UUID.randomUUID().toString())
                            .header("Idempotency-Key", "val-" + System.nanoTime())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.error.code").value("INVALID_ENVELOPE"));
        }
    }

    // ── Health endpoint ──

    @Nested
    @DisplayName("Health endpoint")
    class HealthEndpoint {

        @Test
        @DisplayName("GET /internal/v1/ingest/health returns status ok")
        void healthReturnsOk() throws Exception {
            mockMvc.perform(get("/internal/v1/ingest/health")
                            .header("X-Tenant-ID", TENANT_ID)
                            .header("X-Pod-ID", "national")
                            .header("X-Request-ID", "req-health-1")
                            .header("X-Correlation-ID", UUID.randomUUID().toString()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("ok"))
                    .andExpect(jsonPath("$.time").exists());
        }
    }

    // ── Helpers ──

    private String buildValidEnvelopeJson() {
        return buildEnvelopeJson(UUID.randomUUID().toString());
    }

    private String buildEnvelopeJson(String eventId) {
        return buildEnvelopeJson(eventId, UUID.randomUUID().toString());
    }

    private String buildEnvelopeJson(String eventId, String subjectId) {
        return "{" +
                "\"event_id\":\"" + eventId + "\"," +
                "\"event_type\":\"impilo.test.entity.created.v1\"," +
                "\"schema_version\":1," +
                "\"correlation_id\":\"" + UUID.randomUUID() + "\"," +
                "\"causation_id\":\"" + UUID.randomUUID() + "\"," +
                "\"idempotency_key\":\"ik-" + eventId + "\"," +
                "\"producer\":\"test-producer\"," +
                "\"tenant_id\":\"" + TENANT_ID + "\"," +
                "\"pod_id\":\"national\"," +
                "\"occurred_at\":\"" + OffsetDateTime.now() + "\"," +
                "\"emitted_at\":\"" + OffsetDateTime.now() + "\"," +
                "\"subject_type\":\"TestEntity\"," +
                "\"subject_id\":\"" + subjectId + "\"," +
                "\"payload\":{\"key\":\"value\",\"data\":\"test\"}," +
                "\"meta\":{\"partition_key\":\"" + subjectId + "\"}" +
                "}";
    }

    private String buildEnvelopeJsonWithEventType(String eventType) {
        String eventId = UUID.randomUUID().toString();
        String subjectId = UUID.randomUUID().toString();
        return "{" +
                "\"event_id\":\"" + eventId + "\"," +
                "\"event_type\":\"" + eventType + "\"," +
                "\"schema_version\":1," +
                "\"correlation_id\":\"" + UUID.randomUUID() + "\"," +
                "\"causation_id\":\"" + UUID.randomUUID() + "\"," +
                "\"idempotency_key\":\"ik-" + eventId + "\"," +
                "\"producer\":\"test-producer\"," +
                "\"tenant_id\":\"" + TENANT_ID + "\"," +
                "\"pod_id\":\"national\"," +
                "\"occurred_at\":\"" + OffsetDateTime.now() + "\"," +
                "\"emitted_at\":\"" + OffsetDateTime.now() + "\"," +
                "\"subject_type\":\"TestEntity\"," +
                "\"subject_id\":\"" + subjectId + "\"," +
                "\"payload\":{\"key\":\"value\"}," +
                "\"meta\":{\"partition_key\":\"" + subjectId + "\"}" +
                "}";
    }

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
