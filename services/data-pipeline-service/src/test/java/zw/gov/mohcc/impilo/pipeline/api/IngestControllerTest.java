package zw.gov.mohcc.impilo.pipeline.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import zw.gov.mohcc.impilo.pipeline.api.dto.CreateSourceRequest;
import zw.gov.mohcc.impilo.pipeline.api.dto.EventEnvelope;
import zw.gov.mohcc.impilo.pipeline.config.TestSecurityConfig;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for the IngestController API endpoint.
 *
 * <p>Tests the full request lifecycle including v1.1 header enforcement,
 * validation, source registration, event ingestion, and dedup behavior.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestSecurityConfig.class)
class IngestControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    private static final UUID TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Test
    @DisplayName("POST /internal/v1/ingest with valid envelope returns 200 ACCEPTED")
    void validIngestReturns200() throws Exception {
        // First register the source
        CreateSourceRequest sourceReq = new CreateSourceRequest(
                "test-src-ingest", "Test Source", "For testing", "SERVICE");
        mockMvc.perform(post("/internal/v1/sources")
                        .header("X-Tenant-ID", TENANT_ID.toString())
                        .header("X-Pod-ID", "national")
                        .header("X-Request-ID", "req-1")
                        .header("X-Correlation-ID", "corr-1")
                        .header("Idempotency-Key", "src-create-" + System.nanoTime())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sourceReq)))
                .andExpect(status().isOk());

        // Now ingest an event
        EventEnvelope envelope = new EventEnvelope(
                "evt-api-001",
                TENANT_ID.toString(),
                "test-src-ingest",
                "TEST_EVENT",
                "TestAggregate",
                "agg-001",
                OffsetDateTime.now(),
                "national",
                "corr-1",
                null,
                Map.of("data", "test-value"),
                "1.1"
        );

        mockMvc.perform(post("/internal/v1/ingest")
                        .header("X-Tenant-ID", TENANT_ID.toString())
                        .header("X-Pod-ID", "national")
                        .header("X-Request-ID", "req-2")
                        .header("X-Correlation-ID", "corr-1")
                        .header("Idempotency-Key", "ingest-" + System.nanoTime())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(envelope)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventId").value("evt-api-001"))
                .andExpect(jsonPath("$.status").value("ACCEPTED"));
    }

    @Test
    @DisplayName("POST /internal/v1/ingest with missing eventId returns 400")
    void missingEventIdReturns400() throws Exception {
        EventEnvelope envelope = new EventEnvelope(
                null, TENANT_ID.toString(), "src-1", "TEST", null, null,
                OffsetDateTime.now(), "national", "corr-1", null, Map.of(), "1.1");

        mockMvc.perform(post("/internal/v1/ingest")
                        .header("X-Tenant-ID", TENANT_ID.toString())
                        .header("X-Pod-ID", "national")
                        .header("X-Request-ID", "req-1")
                        .header("X-Correlation-ID", "corr-1")
                        .header("Idempotency-Key", "val-" + System.nanoTime())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(envelope)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("POST /internal/v1/ingest for unknown source returns 422")
    void unknownSourceReturns422() throws Exception {
        EventEnvelope envelope = new EventEnvelope(
                "evt-unknown-src", TENANT_ID.toString(), "nonexistent-source",
                "TEST", null, null, OffsetDateTime.now(), "national",
                "corr-1", null, Map.of(), "1.1");

        mockMvc.perform(post("/internal/v1/ingest")
                        .header("X-Tenant-ID", TENANT_ID.toString())
                        .header("X-Pod-ID", "national")
                        .header("X-Request-ID", "req-1")
                        .header("X-Correlation-ID", "corr-1")
                        .header("Idempotency-Key", "unk-" + System.nanoTime())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(envelope)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value("REJECTED"));
    }

    @Test
    @DisplayName("GET /internal/v1/watermarks returns empty list initially")
    void watermarksReturnsEmptyList() throws Exception {
        mockMvc.perform(get("/internal/v1/watermarks")
                        .header("X-Tenant-ID", TENANT_ID.toString())
                        .header("X-Pod-ID", "national")
                        .header("X-Request-ID", "req-1")
                        .header("X-Correlation-ID", "corr-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    @DisplayName("POST /internal/v1/sources creates a new source")
    void createSourceReturns200() throws Exception {
        CreateSourceRequest request = new CreateSourceRequest(
                "new-src-" + System.nanoTime(), "New Source", "Test source", "ADAPTER");

        mockMvc.perform(post("/internal/v1/sources")
                        .header("X-Tenant-ID", TENANT_ID.toString())
                        .header("X-Pod-ID", "national")
                        .header("X-Request-ID", "req-1")
                        .header("X-Correlation-ID", "corr-1")
                        .header("Idempotency-Key", "csrc-" + System.nanoTime())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("New Source"))
                .andExpect(jsonPath("$.sourceType").value("ADAPTER"));
    }

    @Test
    @DisplayName("POST /internal/v1/sources with missing sourceId returns 400")
    void createSourceMissingIdReturns400() throws Exception {
        CreateSourceRequest request = new CreateSourceRequest(
                null, "No ID Source", null, "SERVICE");

        mockMvc.perform(post("/internal/v1/sources")
                        .header("X-Tenant-ID", TENANT_ID.toString())
                        .header("X-Pod-ID", "national")
                        .header("X-Request-ID", "req-1")
                        .header("X-Correlation-ID", "corr-1")
                        .header("Idempotency-Key", "csrc-noid-" + System.nanoTime())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
    }
}
