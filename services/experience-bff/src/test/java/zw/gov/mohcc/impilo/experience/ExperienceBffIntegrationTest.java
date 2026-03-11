package zw.gov.mohcc.impilo.experience;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ExperienceBffIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("experience_bff_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

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
    // Test 6: Outbox row written with v1.1 context columns populated
    // ========================================================================
    @Test
    @Order(8)
    void outboxRowWritten_withV11ContextColumns() throws Exception {
        String idempotencyKey = "test-outbox-" + UUID.randomUUID();
        String requestId = UUID.randomUUID().toString();
        String correlationId = UUID.randomUUID().toString();

        String body = objectMapper.writeValueAsString(Map.of(
                "report_type", "OUTBOX_TEST",
                "requested_by", "outbox-tester",
                "parameters", Map.of()
        ));

        mvc.perform(post("/internal/v1/reports/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .header("X-Tenant-ID", TENANT_ID)
                        .header("X-Pod-ID", POD_ID)
                        .header("X-Request-ID", requestId)
                        .header("X-Correlation-ID", correlationId)
                        .header("Idempotency-Key", idempotencyKey))
                .andExpect(status().isCreated());

        // Verify outbox row was written
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM event_outbox WHERE correlation_id = ? AND tenant_id = ? AND pod_id = ?",
                Integer.class, correlationId, TENANT_ID, POD_ID);
        Assertions.assertNotNull(count);
        Assertions.assertTrue(count > 0, "Outbox row must be written for command endpoint");

        // Verify v1.1 context columns are populated
        Map<String, Object> row = jdbc.queryForMap(
                "SELECT * FROM event_outbox WHERE correlation_id = ? ORDER BY id DESC LIMIT 1",
                correlationId);
        Assertions.assertEquals(TENANT_ID, row.get("tenant_id"));
        Assertions.assertEquals(POD_ID, row.get("pod_id"));
        Assertions.assertEquals("experience-bff", row.get("producer"));
        Assertions.assertNotNull(row.get("event_id"));
        Assertions.assertNotNull(row.get("partition_key"));
        Assertions.assertEquals(1, row.get("schema_version"));
        Assertions.assertEquals("ReportJob", row.get("subject_type"));
        Assertions.assertNotNull(row.get("subject_id"));
    }

    // ========================================================================
    // Test 7: Workspace activate with outbox
    // ========================================================================
    @Test
    @Order(9)
    void activateWorkspace_writesOutboxEvent() throws Exception {
        String idempotencyKey = "test-ws-activate-" + UUID.randomUUID();
        String requestId = UUID.randomUUID().toString();
        String correlationId = UUID.randomUUID().toString();
        String workspaceId = "b1c2d3e4-0002-4000-8000-000000000002";

        String body = objectMapper.writeValueAsString(Map.of("actor_id", "dr-jane"));

        mvc.perform(post("/internal/v1/workspaces/" + workspaceId + "/activate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .header("X-Tenant-ID", TENANT_ID)
                        .header("X-Pod-ID", POD_ID)
                        .header("X-Request-ID", requestId)
                        .header("X-Correlation-ID", correlationId)
                        .header("Idempotency-Key", idempotencyKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.attributes.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.attributes.activated_by").value("dr-jane"));

        Integer outboxCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM event_outbox WHERE subject_type = 'Workspace' AND correlation_id = ?",
                Integer.class, correlationId);
        Assertions.assertTrue(outboxCount > 0, "Outbox row must be written for workspace activation");
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
