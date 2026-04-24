package zw.gov.mohcc.impilo.experience;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@ExtendWith(DockerOrExternalPostgresCondition.class)
class ExperienceBffIntegrationTest {

    private static final ExperienceBffTestRedisSupport REDIS = ExperienceBffTestRedisSupport.fromEnvironment();

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        REDIS.configure(registry);
        ExperienceBffReportingWireMockSupport.register(registry);
    }

    @AfterAll
    static void stopRedis() {
        REDIS.stop();
    }

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

    private static final String TENANT_ID = "tenant-moh-zw";
    private static final String POD_ID = "national-spine";

    // ========================================================================
    // Test 1: Missing headers => 400 envelope
    // ========================================================================
    @Test
    @Order(1)
    void missingHeaders_returns400Envelope() throws Exception {
        // No v1.1 headers at all
        mvc.perform(get("/internal/v1/facilities"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("MISSING_REQUIRED_HEADER"))
                .andExpect(jsonPath("$.error.message").exists())
                .andExpect(jsonPath("$.error.request_id").exists())
                .andExpect(jsonPath("$.error.correlation_id").exists());
    }

    @Test
    @Order(2)
    void partialHeaders_returns400Envelope() throws Exception {
        // Only X-Tenant-ID, missing rest
        mvc.perform(get("/internal/v1/facilities")
                        .header("X-Tenant-ID", TENANT_ID))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("MISSING_REQUIRED_HEADER"));
    }

    // ========================================================================
    // Test 2: Facilities read endpoint with full headers
    // ========================================================================
    @Test
    @Order(3)
    void listFacilities_withValidHeaders_returnsPagedData() throws Exception {
        mvc.perform(get("/internal/v1/facilities")
                        .header("X-Tenant-ID", TENANT_ID)
                        .header("X-Pod-ID", POD_ID)
                        .header("X-Request-ID", UUID.randomUUID().toString())
                        .header("X-Correlation-ID", UUID.randomUUID().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.meta.page.number").value(0))
                .andExpect(jsonPath("$.meta.page.size").value(20))
                .andExpect(jsonPath("$.meta.request_id").exists())
                .andExpect(jsonPath("$.meta.correlation_id").exists());
    }

    @Test
    @Order(4)
    void listFacilities_withFilters_returnsFilteredResults() throws Exception {
        mvc.perform(get("/internal/v1/facilities")
                        .param("province", "Harare")
                        .param("status", "ACTIVE")
                        .header("X-Tenant-ID", TENANT_ID)
                        .header("X-Pod-ID", POD_ID)
                        .header("X-Request-ID", UUID.randomUUID().toString())
                        .header("X-Correlation-ID", UUID.randomUUID().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }

    // ========================================================================
    // Test 3: Idempotency replay => same response
    // ========================================================================
    @Test
    @Order(5)
    void idempotencyReplay_returnsSameResponse() throws Exception {
        String idempotencyKey = "test-idem-" + UUID.randomUUID();
        String requestId = UUID.randomUUID().toString();
        String correlationId = UUID.randomUUID().toString();

        String body = objectMapper.writeValueAsString(Map.of(
                "report_type", "FACILITY_SUMMARY",
                "requested_by", "test-user",
                "parameters", Map.of("month", "2026-03")
        ));

        // First request
        String response1 = mvc.perform(post("/internal/v1/reports/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .header("X-Tenant-ID", TENANT_ID)
                        .header("X-Pod-ID", POD_ID)
                        .header("X-Request-ID", requestId)
                        .header("X-Correlation-ID", correlationId)
                        .header("Idempotency-Key", idempotencyKey))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.type").value("ReportJob"))
                .andExpect(jsonPath("$.data.attributes.status").value("QUEUED"))
                .andReturn().getResponse().getContentAsString();

        // Second request with same key and same body — should be replay
        String response2 = mvc.perform(post("/internal/v1/reports/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .header("X-Tenant-ID", TENANT_ID)
                        .header("X-Pod-ID", POD_ID)
                        .header("X-Request-ID", UUID.randomUUID().toString())
                        .header("X-Correlation-ID", UUID.randomUUID().toString())
                        .header("Idempotency-Key", idempotencyKey))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        // Responses should be identical (same stored response replayed)
        Assertions.assertEquals(response1, response2, "Idempotency replay must return identical response");
    }

    // ========================================================================
    // Test 4: Idempotency conflict => 409 envelope
    // ========================================================================
    @Test
    @Order(6)
    void idempotencyConflict_returns409Envelope() throws Exception {
        String idempotencyKey = "test-conflict-" + UUID.randomUUID();
        String correlationId = UUID.randomUUID().toString();

        String body1 = objectMapper.writeValueAsString(Map.of(
                "report_type", "FACILITY_SUMMARY",
                "requested_by", "user-1",
                "parameters", Map.of("month", "2026-01")
        ));

        String body2 = objectMapper.writeValueAsString(Map.of(
                "report_type", "DIFFERENT_REPORT",
                "requested_by", "user-2",
                "parameters", Map.of("month", "2026-02")
        ));

        // First request
        mvc.perform(post("/internal/v1/reports/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body1)
                        .header("X-Tenant-ID", TENANT_ID)
                        .header("X-Pod-ID", POD_ID)
                        .header("X-Request-ID", UUID.randomUUID().toString())
                        .header("X-Correlation-ID", correlationId)
                        .header("Idempotency-Key", idempotencyKey))
                .andExpect(status().isCreated());

        // Second request with same key but different body
        mvc.perform(post("/internal/v1/reports/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body2)
                        .header("X-Tenant-ID", TENANT_ID)
                        .header("X-Pod-ID", POD_ID)
                        .header("X-Request-ID", UUID.randomUUID().toString())
                        .header("X-Correlation-ID", correlationId)
                        .header("Idempotency-Key", idempotencyKey))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("IDENTITY_CONFLICT"));
    }

    // ========================================================================
    // Test 5: Missing idempotency key on POST => 400
    // ========================================================================
    @Test
    @Order(7)
    void missingIdempotencyKey_returns400() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "report_type", "FACILITY_SUMMARY",
                "requested_by", "test-user",
                "parameters", Map.of()
        ));

        mvc.perform(post("/internal/v1/reports/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .header("X-Tenant-ID", TENANT_ID)
                        .header("X-Pod-ID", POD_ID)
                        .header("X-Request-ID", UUID.randomUUID().toString())
                        .header("X-Correlation-ID", UUID.randomUUID().toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("IDEMPOTENCY_KEY_REQUIRED"));
    }

    // ========================================================================
    // Test 6–7: Local DB outbox (removed — BFF is a pure proxy; events emit from sovereign services)
    // ========================================================================
    @Test
    @Order(8)
    @Disabled("BFF no longer persists event_outbox to PostgreSQL")
    void outboxRowWritten_withV11ContextColumns() {
        // Retained as documentation hook; sovereign services own transactional outbox.
    }

    @Test
    @Order(9)
    @Disabled("BFF no longer persists event_outbox to PostgreSQL")
    void activateWorkspace_writesOutboxEvent() {
        // Retained as documentation hook; sovereign services own transactional outbox.
    }

    // ========================================================================
    // Test 8: Health endpoint
    // ========================================================================
    @Test
    @Order(10)
    void healthEndpoint_returnsUp() throws Exception {
        mvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }
}
