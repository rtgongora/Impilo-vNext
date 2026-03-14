package zw.gov.mohcc.impilo.iotingestion;

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
import zw.gov.mohcc.impilo.iotingestion.domain.OutboxEventEntity;
import zw.gov.mohcc.impilo.iotingestion.repository.OutboxEventRepository;
import zw.gov.mohcc.impilo.iotingestion.repository.TelemetryDlqRepository;
import zw.gov.mohcc.impilo.iotingestion.repository.TelemetryReadingRepository;

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
public class TelemetryApiMockMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OutboxEventRepository outboxRepository;

    @Autowired
    private TelemetryReadingRepository readingRepository;

    @Autowired
    private TelemetryDlqRepository dlqRepository;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String TENANT_ID = UUID.randomUUID().toString();

    // ── A) Missing required headers => 400 ──

    @Nested
    @DisplayName("A) Missing required headers => 400 envelope")
    class MissingHeaders {

        @Test
        @DisplayName("POST /internal/v1/telemetry/ingest without X-Tenant-ID returns 400")
        void missingTenantIdOnIngest() throws Exception {
            MvcResult result = mockMvc.perform(post("/internal/v1/telemetry/ingest")
                            .header("X-Pod-ID", "national")
                            .header("X-Request-ID", "req-1")
                            .header("X-Correlation-ID", "corr-1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(validTelemetryBody()))
                    .andExpect(status().isBadRequest())
                    .andReturn();

            assertErrorEnvelope(result, "MISSING_REQUIRED_HEADER");
        }

        @Test
        @DisplayName("GET /internal/v1/telemetry/readings without X-Pod-ID returns 400")
        void missingPodIdOnGet() throws Exception {
            MvcResult result = mockMvc.perform(get("/internal/v1/telemetry/readings")
                            .header("X-Tenant-ID", TENANT_ID)
                            .header("X-Request-ID", "req-1")
                            .header("X-Correlation-ID", "corr-1"))
                    .andExpect(status().isBadRequest())
                    .andReturn();

            assertErrorEnvelope(result, "MISSING_REQUIRED_HEADER");
        }
    }

    // ── B) Telemetry ingestion writes append-only ──

    @Nested
    @DisplayName("B) Telemetry ingestion writes append-only readings")
    class TelemetryIngestion {

        @Test
        @DisplayName("POST /internal/v1/telemetry/ingest creates reading and returns 201")
        void ingestCreatesReading() throws Exception {
            long countBefore = readingRepository.count();

            MvcResult result = mockMvc.perform(post("/internal/v1/telemetry/ingest")
                            .header("X-Tenant-ID", TENANT_ID)
                            .header("X-Pod-ID", "national")
                            .header("X-Request-ID", "req-ingest-1")
                            .header("X-Correlation-ID", UUID.randomUUID().toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(validTelemetryBody()))
                    .andExpect(status().isCreated())
                    .andReturn();

            JsonNode body = MAPPER.readTree(result.getResponse().getContentAsString());
            assertThat(body.get("status").asText()).isEqualTo("accepted");
            assertThat(body.has("reading_id")).isTrue();

            long countAfter = readingRepository.count();
            assertThat(countAfter).isEqualTo(countBefore + 1);
        }

        @Test
        @DisplayName("Multiple ingestions produce multiple append-only rows")
        void multipleIngestionsAppendOnly() throws Exception {
            String deviceId = "DEVICE-APPEND-" + System.nanoTime();
            long countBefore = readingRepository.countByDeviceId(deviceId);

            for (int i = 0; i < 3; i++) {
                mockMvc.perform(post("/internal/v1/telemetry/ingest")
                                .header("X-Tenant-ID", TENANT_ID)
                                .header("X-Pod-ID", "national")
                                .header("X-Request-ID", "req-multi-" + i)
                                .header("X-Correlation-ID", UUID.randomUUID().toString())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(telemetryBody(deviceId, "TEMPERATURE", 36.5 + i, "1")))
                        .andExpect(status().isCreated());
            }

            long countAfter = readingRepository.countByDeviceId(deviceId);
            assertThat(countAfter).isEqualTo(countBefore + 3);
        }
    }

    // ── C) DLQ on invalid schema_version ──

    @Nested
    @DisplayName("C) DLQ on invalid schema_version")
    class DlqInvalidSchema {

        @Test
        @DisplayName("POST with unsupported schema_version returns 422 and writes DLQ")
        void invalidSchemaVersionWritesDlq() throws Exception {
            long dlqBefore = dlqRepository.count();

            MvcResult result = mockMvc.perform(post("/internal/v1/telemetry/ingest")
                            .header("X-Tenant-ID", TENANT_ID)
                            .header("X-Pod-ID", "national")
                            .header("X-Request-ID", "req-dlq-1")
                            .header("X-Correlation-ID", UUID.randomUUID().toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(telemetryBody("DEVICE-DLQ", "TEMPERATURE", 37.0, "999")))
                    .andExpect(status().isUnprocessableEntity())
                    .andReturn();

            JsonNode body = MAPPER.readTree(result.getResponse().getContentAsString());
            assertThat(body.get("status").asText()).isEqualTo("rejected");
            assertThat(body.has("dlq_id")).isTrue();

            long dlqAfter = dlqRepository.count();
            assertThat(dlqAfter).isEqualTo(dlqBefore + 1);
        }

        @Test
        @DisplayName("DLQ record contains error_code INVALID_SCHEMA_VERSION")
        void dlqHasCorrectErrorCode() throws Exception {
            long dlqCountBefore = dlqRepository.countByErrorCode("INVALID_SCHEMA_VERSION");

            mockMvc.perform(post("/internal/v1/telemetry/ingest")
                            .header("X-Tenant-ID", TENANT_ID)
                            .header("X-Pod-ID", "national")
                            .header("X-Request-ID", "req-dlq-2")
                            .header("X-Correlation-ID", UUID.randomUUID().toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(telemetryBody("DEVICE-DLQ-2", "HUMIDITY", 85.0, "42")))
                    .andExpect(status().isUnprocessableEntity());

            long dlqCountAfter = dlqRepository.countByErrorCode("INVALID_SCHEMA_VERSION");
            assertThat(dlqCountAfter).isEqualTo(dlqCountBefore + 1);
        }
    }

    // ── D) Outbox event emission on telemetry bus ──

    @Nested
    @DisplayName("D) Outbox emits telemetry namespace events")
    class OutboxTelemetryEvents {

        @Test
        @DisplayName("Ingested reading produces outbox row with impilo.telemetry.* event type")
        void outboxRowHasTelemetryNamespace() throws Exception {
            String correlationId = UUID.randomUUID().toString();

            MvcResult result = mockMvc.perform(post("/internal/v1/telemetry/ingest")
                            .header("X-Tenant-ID", TENANT_ID)
                            .header("X-Pod-ID", "national")
                            .header("X-Request-ID", "req-outbox-1")
                            .header("X-Correlation-ID", correlationId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(validTelemetryBody()))
                    .andExpect(status().isCreated())
                    .andReturn();

            JsonNode respJson = MAPPER.readTree(result.getResponse().getContentAsString());
            String readingId = respJson.get("reading_id").asText();

            List<OutboxEventEntity> outboxRows = outboxRepository
                    .findByAggregateIdAndEventType(readingId, "impilo.iot.telemetry.reading.ingested.v1");

            assertThat(outboxRows).hasSize(1);
            OutboxEventEntity row = outboxRows.get(0);

            // Verify telemetry namespace
            assertThat(row.getEventType()).startsWith("impilo.iot.telemetry.");
            assertThat(row.getEventId()).isNotNull();
            assertThat(row.getAggregateType()).isEqualTo("TelemetryReading");
            assertThat(row.getProducer()).isEqualTo("iot-ingestion-service");
            assertThat(row.getSubjectType()).isEqualTo("Device");
            assertThat(row.getOccurredAt()).isNotNull();
            assertThat(row.getCorrelationId()).isNotNull();
            assertThat(row.getCorrelationId().toString()).isEqualTo(correlationId);

            // partition_key == device_id
            assertThat(row.getPartitionKey()).isNotNull();

            // Payload contains reading data
            JsonNode payload = MAPPER.readTree(row.getPayloadJson());
            assertThat(payload.get("reading_id").asText()).isEqualTo(readingId);
            assertThat(payload.has("device_id")).isTrue();
            assertThat(payload.has("metric_type")).isTrue();
            assertThat(payload.has("metric_value")).isTrue();
        }
    }

    // ── E) Batch ingest ──

    @Nested
    @DisplayName("E) Batch ingest accepts multiple readings")
    class BatchIngest {

        @Test
        @DisplayName("POST /internal/v1/telemetry/ingest/batch returns accepted/rejected counts")
        void batchIngestMixed() throws Exception {
            String body = """
                    {
                      "readings": [
                        {"deviceId":"BATCH-DEV-1","metricType":"TEMPERATURE","metricValue":36.5,"unit":"C","schemaVersion":"1","recordedAt":"2026-03-14T10:00:00Z"},
                        {"deviceId":"BATCH-DEV-2","metricType":"HUMIDITY","metricValue":75.0,"unit":"%","schemaVersion":"999","recordedAt":"2026-03-14T10:00:00Z"},
                        {"deviceId":"BATCH-DEV-3","metricType":"PRESSURE","metricValue":1013.25,"unit":"hPa","schemaVersion":"2","recordedAt":"2026-03-14T10:00:00Z"}
                      ]
                    }
                    """;

            MvcResult result = mockMvc.perform(post("/internal/v1/telemetry/ingest/batch")
                            .header("X-Tenant-ID", TENANT_ID)
                            .header("X-Pod-ID", "national")
                            .header("X-Request-ID", "req-batch-1")
                            .header("X-Correlation-ID", UUID.randomUUID().toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isCreated())
                    .andReturn();

            JsonNode respJson = MAPPER.readTree(result.getResponse().getContentAsString());
            assertThat(respJson.get("accepted").asInt()).isEqualTo(2);
            assertThat(respJson.get("rejected").asInt()).isEqualTo(1);
            assertThat(respJson.get("readingIds").size()).isEqualTo(2);
        }
    }

    // ── F) List readings ──

    @Nested
    @DisplayName("F) List readings returns paginated response")
    class ListReadings {

        @Test
        @DisplayName("GET /internal/v1/telemetry/readings returns paginated items")
        void listReadingsReturnsItems() throws Exception {
            // Ingest a reading first
            mockMvc.perform(post("/internal/v1/telemetry/ingest")
                            .header("X-Tenant-ID", TENANT_ID)
                            .header("X-Pod-ID", "national")
                            .header("X-Request-ID", "req-list-1")
                            .header("X-Correlation-ID", UUID.randomUUID().toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(validTelemetryBody()))
                    .andExpect(status().isCreated());

            mockMvc.perform(get("/internal/v1/telemetry/readings")
                            .header("X-Tenant-ID", TENANT_ID)
                            .header("X-Pod-ID", "national")
                            .header("X-Request-ID", "req-list-2")
                            .header("X-Correlation-ID", UUID.randomUUID().toString())
                            .param("limit", "10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items").isArray())
                    .andExpect(jsonPath("$.limit").value(10))
                    .andExpect(jsonPath("$.has_more").exists());
        }
    }

    // ── Helpers ──

    private String validTelemetryBody() {
        return telemetryBody("DEVICE-" + System.nanoTime(), "TEMPERATURE", 36.6, "1");
    }

    private String telemetryBody(String deviceId, String metricType, double value, String schemaVersion) {
        return String.format("""
                {
                  "deviceId": "%s",
                  "metricType": "%s",
                  "metricValue": %s,
                  "unit": "C",
                  "schemaVersion": "%s",
                  "recordedAt": "2026-03-14T12:00:00Z"
                }
                """, deviceId, metricType, value, schemaVersion);
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
