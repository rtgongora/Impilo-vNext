package zw.gov.mohcc.impilo.companion.mock;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Additional unit tests verifying companion filter behavior
 * beyond what the golden suite covers.
 */
@SpringBootTest(classes = MockServiceApplication.class)
@AutoConfigureMockMvc
class MockServiceUnitTest {

    @Autowired
    private MockMvc mockMvc;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Nested
    @DisplayName("Request Context Propagation")
    class ContextPropagation {

        @Test
        @DisplayName("RequestContext is populated with header values")
        void contextContainsHeaders() throws Exception {
            MvcResult result = mockMvc.perform(get("/internal/v1/health")
                            .header("X-Tenant-ID", "moh-zw")
                            .header("X-Pod-ID", "national")
                            .header("X-Request-ID", "req-123")
                            .header("X-Correlation-ID", "corr-456"))
                    .andExpect(status().isOk())
                    .andReturn();

            JsonNode body = MAPPER.readTree(result.getResponse().getContentAsString());
            assertThat(body.get("tenant_id").asText()).isEqualTo("moh-zw");
            assertThat(body.get("pod_id").asText()).isEqualTo("national");
            assertThat(body.get("request_id").asText()).isEqualTo("req-123");
            assertThat(body.get("correlation_id").asText()).isEqualTo("corr-456");
        }

        @Test
        @DisplayName("Missing X-Request-ID returns 400; error envelope still has trace ids")
        void missingRequestIdReturns400WithTraceIds() throws Exception {
            MvcResult result = mockMvc.perform(get("/internal/v1/health")
                            .header("X-Tenant-ID", "moh-zw")
                            .header("X-Pod-ID", "national")
                            .header("X-Correlation-ID", "corr-1"))
                    .andExpect(status().isBadRequest())
                    .andReturn();

            JsonNode error = MAPPER.readTree(result.getResponse().getContentAsString()).get("error");
            assertThat(error.get("code").asText()).isEqualTo("MISSING_REQUIRED_HEADER");
            assertThat(error.get("request_id").asText()).isNotBlank();
            assertThat(error.get("correlation_id").asText()).isEqualTo("corr-1");
        }
    }

    @Nested
    @DisplayName("Non-v1.1 Paths")
    class LegacyPaths {

        @Test
        @DisplayName("Legacy /v1/ paths are not enforced")
        void legacyPathNotEnforced() throws Exception {
            // The mock service doesn't have a /v1/legacy endpoint,
            // but the filter should NOT block it with 400.
            // It will get 404 from Spring, which is expected.
            mockMvc.perform(get("/v1/legacy"))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("Actuator paths are not enforced")
        void actuatorNotEnforced() throws Exception {
            // Actuator is not explicitly mapped, so 404 expected.
            // But NOT 400 from the header filter.
            mockMvc.perform(get("/actuator/health"))
                    .andExpect(result ->
                            assertThat(result.getResponse().getStatus()).isNotEqualTo(400));
        }
    }

    @Nested
    @DisplayName("GET requests skip idempotency")
    class GetSkipsIdempotency {

        @Test
        @DisplayName("GET request without Idempotency-Key succeeds")
        void getWithoutIdempotencyKey() throws Exception {
            mockMvc.perform(get("/internal/v1/health")
                            .header("X-Tenant-ID", "moh-zw")
                            .header("X-Pod-ID", "national")
                            .header("X-Request-ID", "req-1")
                            .header("X-Correlation-ID", "corr-1"))
                    .andExpect(status().isOk());
        }
    }
}
