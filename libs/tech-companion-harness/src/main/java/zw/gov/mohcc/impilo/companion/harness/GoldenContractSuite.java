package zw.gov.mohcc.impilo.companion.harness;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Golden Contract Test Suite for v1.1 compliance.
 *
 * Apply to ANY Impilo service by:
 * 1. Adding tech-companion-harness as a test dependency
 * 2. Creating a test class that extends this and adds @SpringBootTest
 *
 * Verifies:
 * - Header enforcement (missing tenant/pod → 400)
 * - Error envelope format (all fields present)
 * - Idempotency conflict (same key different body → 409 IDENTITY_CONFLICT)
 * - Federation authority violation (private pod → 403 FEDERATION_AUTHORITY_VIOLATION)
 *
 * Requires the service to expose at minimum:
 * - GET  /internal/v1/health (any 2xx endpoint)
 * - POST /internal/v1/test-command (any command endpoint for idempotency tests)
 * - POST /internal/v1/test-federation (national-only endpoint for federation tests)
 *
 * Override {@link #getHealthPath()}, {@link #getCommandPath()}, and
 * {@link #getFederationPath()} if your paths differ.
 */
@AutoConfigureMockMvc
public abstract class GoldenContractSuite {

    @Autowired
    protected MockMvc mockMvc;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    // ── Override points for service-specific paths ──────────────────

    protected String getHealthPath() {
        return "/internal/v1/health";
    }

    protected String getCommandPath() {
        return "/internal/v1/test-command";
    }

    protected String getFederationPath() {
        return "/internal/v1/test-federation";
    }

    // ── 1. Header Enforcement ──────────────────────────────────────

    @Nested
    @DisplayName("Header Enforcement")
    class HeaderEnforcement {

        @Test
        @DisplayName("Missing X-Tenant-ID returns 400 MISSING_REQUIRED_HEADER")
        void missingTenantIdReturns400() throws Exception {
            MvcResult result = mockMvc.perform(get(getHealthPath())
                            .header("X-Pod-ID", "national")
                            .header("X-Request-ID", "req-1")
                            .header("X-Correlation-ID", "corr-1"))
                    .andExpect(status().isBadRequest())
                    .andReturn();

            assertErrorEnvelope(result, "MISSING_REQUIRED_HEADER");
        }

        @Test
        @DisplayName("Missing X-Pod-ID returns 400 MISSING_REQUIRED_HEADER")
        void missingPodIdReturns400() throws Exception {
            MvcResult result = mockMvc.perform(get(getHealthPath())
                            .header("X-Tenant-ID", "moh-zw")
                            .header("X-Request-ID", "req-1")
                            .header("X-Correlation-ID", "corr-1"))
                    .andExpect(status().isBadRequest())
                    .andReturn();

            assertErrorEnvelope(result, "MISSING_REQUIRED_HEADER");
        }

        @Test
        @DisplayName("Blank X-Tenant-ID treated as missing")
        void blankTenantIdReturns400() throws Exception {
            MvcResult result = mockMvc.perform(get(getHealthPath())
                            .header("X-Tenant-ID", "  ")
                            .header("X-Pod-ID", "national")
                            .header("X-Request-ID", "req-1")
                            .header("X-Correlation-ID", "corr-1"))
                    .andExpect(status().isBadRequest())
                    .andReturn();

            assertErrorEnvelope(result, "MISSING_REQUIRED_HEADER");
        }

        @Test
        @DisplayName("All required headers present passes through")
        void allHeadersPresentSucceeds() throws Exception {
            mockMvc.perform(get(getHealthPath())
                            .header("X-Tenant-ID", "moh-zw")
                            .header("X-Pod-ID", "national")
                            .header("X-Request-ID", "req-1")
                            .header("X-Correlation-ID", "corr-1"))
                    .andExpect(status().is2xxSuccessful());
        }
    }

    // ── 2. Error Envelope Fields ───────────────────────────────────

    @Nested
    @DisplayName("Error Envelope Format")
    class ErrorEnvelopeFormat {

        @Test
        @DisplayName("Error response contains all required fields")
        void errorResponseContainsAllFields() throws Exception {
            MvcResult result = mockMvc.perform(get(getHealthPath()))
                    .andExpect(status().isBadRequest())
                    .andReturn();

            String body = result.getResponse().getContentAsString();
            JsonNode root = MAPPER.readTree(body);

            assertThat(root.has("error")).isTrue();
            JsonNode error = root.get("error");
            assertThat(error.has("code")).isTrue();
            assertThat(error.has("message")).isTrue();
            assertThat(error.has("details")).isTrue();
            assertThat(error.has("request_id")).isTrue();
            assertThat(error.has("correlation_id")).isTrue();

            // code should be a non-empty string
            assertThat(error.get("code").asText()).isNotBlank();
            // request_id and correlation_id should be auto-generated
            assertThat(error.get("request_id").asText()).isNotBlank();
            assertThat(error.get("correlation_id").asText()).isNotBlank();
        }

        @Test
        @DisplayName("Error response Content-Type is application/json")
        void errorResponseIsJson() throws Exception {
            mockMvc.perform(get(getHealthPath()))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
        }
    }

    // ── 3. Idempotency Conflict ────────────────────────────────────

    @Nested
    @DisplayName("Idempotency")
    class Idempotency {

        @Test
        @DisplayName("Missing Idempotency-Key on POST returns 400 IDEMPOTENCY_KEY_REQUIRED")
        void missingIdempotencyKeyReturns400() throws Exception {
            MvcResult result = mockMvc.perform(post(getCommandPath())
                            .header("X-Tenant-ID", "moh-zw")
                            .header("X-Pod-ID", "national")
                            .header("X-Request-ID", "req-1")
                            .header("X-Correlation-ID", "corr-1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"test\"}"))
                    .andExpect(status().isBadRequest())
                    .andReturn();

            assertErrorEnvelope(result, "IDEMPOTENCY_KEY_REQUIRED");
        }

        @Test
        @DisplayName("Same Idempotency-Key + same body replays original response")
        void sameKeyAndBodyReplays() throws Exception {
            String idempotencyKey = "idem-replay-" + System.nanoTime();
            String body = "{\"name\":\"replay-test\"}";

            // First call
            MvcResult first = mockMvc.perform(post(getCommandPath())
                            .header("X-Tenant-ID", "moh-zw")
                            .header("X-Pod-ID", "national")
                            .header("X-Request-ID", "req-1")
                            .header("X-Correlation-ID", "corr-1")
                            .header("Idempotency-Key", idempotencyKey)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andReturn();

            int firstStatus = first.getResponse().getStatus();
            String firstBody = first.getResponse().getContentAsString();

            // Second call with same key + same body
            MvcResult second = mockMvc.perform(post(getCommandPath())
                            .header("X-Tenant-ID", "moh-zw")
                            .header("X-Pod-ID", "national")
                            .header("X-Request-ID", "req-2")
                            .header("X-Correlation-ID", "corr-1")
                            .header("Idempotency-Key", idempotencyKey)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andReturn();

            assertThat(second.getResponse().getStatus()).isEqualTo(firstStatus);
            assertThat(second.getResponse().getContentAsString()).isEqualTo(firstBody);
        }

        @Test
        @DisplayName("Same Idempotency-Key + different body returns 409 IDENTITY_CONFLICT")
        void sameKeyDifferentBodyReturns409() throws Exception {
            String idempotencyKey = "idem-conflict-" + System.nanoTime();

            // First call
            mockMvc.perform(post(getCommandPath())
                            .header("X-Tenant-ID", "moh-zw")
                            .header("X-Pod-ID", "national")
                            .header("X-Request-ID", "req-1")
                            .header("X-Correlation-ID", "corr-1")
                            .header("Idempotency-Key", idempotencyKey)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"first\"}"))
                    .andReturn();

            // Second call — same key, different body
            MvcResult result = mockMvc.perform(post(getCommandPath())
                            .header("X-Tenant-ID", "moh-zw")
                            .header("X-Pod-ID", "national")
                            .header("X-Request-ID", "req-2")
                            .header("X-Correlation-ID", "corr-1")
                            .header("Idempotency-Key", idempotencyKey)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"second-different\"}"))
                    .andExpect(status().isConflict())
                    .andReturn();

            assertErrorEnvelope(result, "IDENTITY_CONFLICT");
        }
    }

    // ── 4. Federation Authority ────────────────────────────────────

    @Nested
    @DisplayName("Federation Authority")
    class FederationAuthority {

        @Test
        @DisplayName("Private pod on national-only endpoint returns 403 FEDERATION_AUTHORITY_VIOLATION")
        void privatePodReturns403() throws Exception {
            MvcResult result = mockMvc.perform(post(getFederationPath())
                            .header("X-Tenant-ID", "moh-zw")
                            .header("X-Pod-ID", "private-harare")
                            .header("X-Request-ID", "req-1")
                            .header("X-Correlation-ID", "corr-1")
                            .header("Idempotency-Key", "fed-test-" + System.nanoTime())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"action\":\"merge\"}"))
                    .andExpect(status().isForbidden())
                    .andReturn();

            assertErrorEnvelope(result, "FEDERATION_AUTHORITY_VIOLATION");
        }

        @Test
        @DisplayName("National pod on national-only endpoint succeeds")
        void nationalPodSucceeds() throws Exception {
            mockMvc.perform(post(getFederationPath())
                            .header("X-Tenant-ID", "moh-zw")
                            .header("X-Pod-ID", "national")
                            .header("X-Request-ID", "req-1")
                            .header("X-Correlation-ID", "corr-1")
                            .header("Idempotency-Key", "fed-test-ok-" + System.nanoTime())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"action\":\"merge\"}"))
                    .andExpect(status().is2xxSuccessful());
        }
    }

    // ── Assertion helper ───────────────────────────────────────────

    protected void assertErrorEnvelope(MvcResult result, String expectedCode) throws Exception {
        String body = result.getResponse().getContentAsString();
        assertThat(body).isNotBlank();

        JsonNode root = MAPPER.readTree(body);
        assertThat(root.has("error"))
                .as("Response should contain 'error' field: " + body)
                .isTrue();

        JsonNode error = root.get("error");
        assertThat(error.get("code").asText()).isEqualTo(expectedCode);
        assertThat(error.has("message")).isTrue();
        assertThat(error.has("request_id")).isTrue();
        assertThat(error.has("correlation_id")).isTrue();
    }
}
