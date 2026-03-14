package zw.gov.mohcc.impilo.obs.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import zw.gov.mohcc.impilo.obs.config.TestSecurityConfig;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestSecurityConfig.class)
public class OpsApiMockMvcTest {

    @Autowired
    private MockMvc mockMvc;

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String TENANT_ID = UUID.randomUUID().toString();

    // ── A) Missing required headers => 400 ──

    @Nested
    @DisplayName("A) Missing required headers => 400 envelope")
    class MissingHeaders {

        @Test
        @DisplayName("POST /internal/v1/ops/heartbeat without X-Tenant-ID returns 400")
        void missingTenantIdOnHeartbeat() throws Exception {
            MvcResult result = mockMvc.perform(post("/internal/v1/ops/heartbeat")
                            .header("X-Pod-ID", "national")
                            .header("X-Request-ID", "req-1")
                            .header("X-Correlation-ID", "corr-1")
                            .header("Idempotency-Key", "hb-idem-" + System.nanoTime())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(validHeartbeatBody()))
                    .andExpect(status().isBadRequest())
                    .andReturn();

            assertErrorEnvelope(result, "MISSING_REQUIRED_HEADER");
        }

        @Test
        @DisplayName("GET /internal/v1/ops/health/summary without headers returns 400")
        void missingHeadersOnHealthSummary() throws Exception {
            MvcResult result = mockMvc.perform(get("/internal/v1/ops/health/summary"))
                    .andExpect(status().isBadRequest())
                    .andReturn();

            assertErrorEnvelope(result, "MISSING_REQUIRED_HEADER");
        }
    }

    // ── B) Heartbeat recording ──

    @Nested
    @DisplayName("B) Heartbeat recording")
    class HeartbeatRecording {

        @Test
        @DisplayName("POST /internal/v1/ops/heartbeat creates heartbeat and returns 201")
        void recordHeartbeat() throws Exception {
            MvcResult result = mockMvc.perform(post("/internal/v1/ops/heartbeat")
                            .header("X-Tenant-ID", TENANT_ID)
                            .header("X-Pod-ID", "national")
                            .header("X-Request-ID", UUID.randomUUID().toString())
                            .header("X-Correlation-ID", UUID.randomUUID().toString())
                            .header("Idempotency-Key", "hb-create-" + System.nanoTime())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(validHeartbeatBody()))
                    .andExpect(status().isCreated())
                    .andReturn();

            JsonNode body = MAPPER.readTree(result.getResponse().getContentAsString());
            assertThat(body.get("service_name").asText()).isEqualTo("test-service");
            assertThat(body.get("instance_id").asText()).isEqualTo("instance-001");
            assertThat(body.get("status").asText()).isEqualTo("UP");
            assertThat(body.get("acknowledged").asBoolean()).isTrue();
            assertThat(body.has("last_heartbeat")).isTrue();
        }

        @Test
        @DisplayName("Subsequent heartbeat updates existing record")
        void updateHeartbeat() throws Exception {
            String serviceName = "update-svc-" + System.nanoTime();
            String instanceId = "inst-" + System.nanoTime();

            // First heartbeat
            mockMvc.perform(post("/internal/v1/ops/heartbeat")
                            .header("X-Tenant-ID", TENANT_ID)
                            .header("X-Pod-ID", "national")
                            .header("X-Request-ID", UUID.randomUUID().toString())
                            .header("X-Correlation-ID", UUID.randomUUID().toString())
                            .header("Idempotency-Key", "hb-first-" + System.nanoTime())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(heartbeatBody(serviceName, instanceId, "UP")))
                    .andExpect(status().isCreated());

            // Second heartbeat — update
            MvcResult result = mockMvc.perform(post("/internal/v1/ops/heartbeat")
                            .header("X-Tenant-ID", TENANT_ID)
                            .header("X-Pod-ID", "national")
                            .header("X-Request-ID", UUID.randomUUID().toString())
                            .header("X-Correlation-ID", UUID.randomUUID().toString())
                            .header("Idempotency-Key", "hb-second-" + System.nanoTime())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(heartbeatBody(serviceName, instanceId, "DEGRADED")))
                    .andExpect(status().isCreated())
                    .andReturn();

            JsonNode body = MAPPER.readTree(result.getResponse().getContentAsString());
            assertThat(body.get("status").asText()).isEqualTo("DEGRADED");
        }
    }

    // ── C) Health summary ──

    @Nested
    @DisplayName("C) Health summary aggregation")
    class HealthSummary {

        @Test
        @DisplayName("GET /internal/v1/ops/health/summary returns service overview")
        void healthSummaryReturnsOverview() throws Exception {
            // Register a heartbeat first
            mockMvc.perform(post("/internal/v1/ops/heartbeat")
                            .header("X-Tenant-ID", TENANT_ID)
                            .header("X-Pod-ID", "national")
                            .header("X-Request-ID", UUID.randomUUID().toString())
                            .header("X-Correlation-ID", UUID.randomUUID().toString())
                            .header("Idempotency-Key", "hb-summary-" + System.nanoTime())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(heartbeatBody("summary-svc", "inst-1", "UP")))
                    .andExpect(status().isCreated());

            mockMvc.perform(get("/internal/v1/ops/health/summary")
                            .header("X-Tenant-ID", TENANT_ID)
                            .header("X-Pod-ID", "national")
                            .header("X-Request-ID", UUID.randomUUID().toString())
                            .header("X-Correlation-ID", UUID.randomUUID().toString()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.total_services").exists())
                    .andExpect(jsonPath("$.healthy").exists())
                    .andExpect(jsonPath("$.stale").exists())
                    .andExpect(jsonPath("$.services").isArray())
                    .andExpect(jsonPath("$.generated_at").exists());
        }
    }

    // ── D) Metrics lag ──

    @Nested
    @DisplayName("D) Outbox metrics lag")
    class MetricsLag {

        @Test
        @DisplayName("GET /internal/v1/ops/metrics/lag returns outbox depth")
        void metricsLagReturnsOutboxInfo() throws Exception {
            mockMvc.perform(get("/internal/v1/ops/metrics/lag")
                            .header("X-Tenant-ID", TENANT_ID)
                            .header("X-Pod-ID", "national")
                            .header("X-Request-ID", UUID.randomUUID().toString())
                            .header("X-Correlation-ID", UUID.randomUUID().toString()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.outbox_depth").exists())
                    .andExpect(jsonPath("$.outbox_lag_seconds").exists())
                    .andExpect(jsonPath("$.measured_at").exists());
        }
    }

    // ── Helpers ──

    private String validHeartbeatBody() {
        return heartbeatBody("test-service", "instance-001", "UP");
    }

    private String heartbeatBody(String serviceName, String instanceId, String status) {
        return String.format("""
                {
                  "serviceName": "%s",
                  "instanceId": "%s",
                  "status": "%s",
                  "versionTag": "1.0.0"
                }
                """, serviceName, instanceId, status);
    }

    private void assertErrorEnvelope(MvcResult result, String expectedCode) throws Exception {
        String body = result.getResponse().getContentAsString();
        assertThat(body).isNotBlank();
        JsonNode root = MAPPER.readTree(body);
        assertThat(root.has("error")).as("Response should contain 'error' field: " + body).isTrue();
        JsonNode error = root.get("error");
        assertThat(error.get("code").asText()).isEqualTo(expectedCode);
        assertThat(error.has("message")).isTrue();
    }
}
