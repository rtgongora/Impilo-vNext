package zw.gov.mohcc.impilo.companion.harness;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Golden Contract Test Suite for v1.1 compliance — Wave 6 (auto-discovery).
 *
 * <p>Uses {@link EndpointDiscovery} to find real v1.1 endpoints in the service
 * under test, eliminating the need for dedicated probe/mock controllers.
 *
 * <p>Apply to ANY Impilo service by:
 * <ol>
 *   <li>Adding {@code tech-companion-harness} as a test dependency</li>
 *   <li>Creating one test class: {@code @SpringBootTest + extends GoldenContractSuite}</li>
 * </ol>
 *
 * <p>Verifies:
 * <ul>
 *   <li>Header enforcement (missing any required header → 400 + envelope)</li>
 *   <li>Error envelope format (code, message, details, request_id, correlation_id)</li>
 *   <li>Idempotency (missing key → 400; same key + different body → 409)</li>
 *   <li>Federation authority (private pod → 403) — only if service provides endpoint</li>
 * </ul>
 *
 * <p>If a service has no v1.1 endpoints, tests will be SKIPPED (not failed).
 * If a service has no command endpoint, idempotency tests will be SKIPPED.
 * If a service has no federation endpoint, federation tests will be SKIPPED.
 */
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class GoldenContractSuite {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired(required = false)
    protected RequestMappingHandlerMapping handlerMapping;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    // Discovered endpoints (resolved in @BeforeAll)
    private String readEndpoint;
    private String commandEndpoint;
    private String commandMethod;
    private String federationEndpoint;

    // ── Override points (Wave 6 API) ────────────────────────────────

    /**
     * Override to provide a specific read endpoint path.
     * Return null to use auto-discovery.
     */
    protected String getReadEndpointOverride() {
        return null;
    }

    /**
     * Override to provide a specific command endpoint path.
     * Return null to use auto-discovery.
     */
    protected String getCommandEndpointOverride() {
        return null;
    }

    /**
     * Override to provide a federation-gated endpoint path.
     * Return null if this service has no federation-gated endpoints.
     * Federation tests will be SKIPPED when null.
     */
    protected String getFederationEndpointOverride() {
        return null;
    }

    // ── Backwards-compatible override points (Wave 5 API) ───────────

    /**
     * @deprecated Use {@link #getReadEndpointOverride()} instead.
     *             Returns null by default (triggers auto-discovery).
     */
    protected String getHealthPath() {
        return null;
    }

    /**
     * @deprecated Use {@link #getCommandEndpointOverride()} instead.
     *             Returns null by default (triggers auto-discovery).
     */
    protected String getCommandPath() {
        return null;
    }

    /**
     * @deprecated Use {@link #getFederationEndpointOverride()} instead.
     *             Returns null by default (triggers auto-discovery).
     */
    protected String getFederationPath() {
        return null;
    }

    // ── Discovery ──────────────────────────────────────────────────

    @BeforeAll
    void discoverEndpoints() {
        // Priority: explicit Wave 6 override > legacy Wave 5 override > auto-discovery

        readEndpoint = coalesce(getReadEndpointOverride(), getHealthPath());
        if (readEndpoint == null && handlerMapping != null) {
            readEndpoint = EndpointDiscovery.findReadEndpoint(handlerMapping).orElse(null);
        }

        commandEndpoint = coalesce(getCommandEndpointOverride(), getCommandPath());
        if (commandEndpoint == null && handlerMapping != null) {
            commandEndpoint = EndpointDiscovery.findCommandEndpoint(handlerMapping).orElse(null);
        }
        if (commandEndpoint != null && handlerMapping != null) {
            commandMethod = EndpointDiscovery.getCommandMethod(handlerMapping, commandEndpoint);
        } else {
            commandMethod = "POST";
        }

        federationEndpoint = coalesce(getFederationEndpointOverride(), getFederationPath());

        System.out.println("[GoldenContractSuite] Discovered endpoints:");
        System.out.println("  Read:       " + (readEndpoint != null ? readEndpoint : "NONE (header/envelope tests will be skipped)"));
        System.out.println("  Command:    " + (commandEndpoint != null ? commandEndpoint + " [" + commandMethod + "]" : "NONE (idempotency tests will be skipped)"));
        System.out.println("  Federation: " + (federationEndpoint != null ? federationEndpoint : "NONE (federation tests will be skipped)"));
    }

    private static String coalesce(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) return v;
        }
        return null;
    }

    // ── 1. Header Enforcement ──────────────────────────────────────

    @Nested
    @DisplayName("Header Enforcement")
    class HeaderEnforcement {

        @Test
        @DisplayName("Missing X-Tenant-ID returns 400 MISSING_REQUIRED_HEADER")
        void missingTenantIdReturns400() throws Exception {
            Assumptions.assumeTrue(readEndpoint != null,
                    "SKIPPED: No v1.1 read endpoint discovered for header enforcement test");

            MvcResult result = mockMvc.perform(get(readEndpoint)
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
            Assumptions.assumeTrue(readEndpoint != null,
                    "SKIPPED: No v1.1 read endpoint discovered for header enforcement test");

            MvcResult result = mockMvc.perform(get(readEndpoint)
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
            Assumptions.assumeTrue(readEndpoint != null,
                    "SKIPPED: No v1.1 read endpoint discovered for header enforcement test");

            MvcResult result = mockMvc.perform(get(readEndpoint)
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
            Assumptions.assumeTrue(readEndpoint != null,
                    "SKIPPED: No v1.1 read endpoint discovered for header enforcement test");

            mockMvc.perform(get(readEndpoint)
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
        @DisplayName("Error response contains all required envelope fields")
        void errorResponseContainsAllFields() throws Exception {
            Assumptions.assumeTrue(readEndpoint != null,
                    "SKIPPED: No v1.1 read endpoint discovered for error envelope test");

            MvcResult result = mockMvc.perform(get(readEndpoint))
                    .andExpect(status().isBadRequest())
                    .andReturn();

            String body = result.getResponse().getContentAsString();
            JsonNode root = MAPPER.readTree(body);

            assertThat(root.has("error")).as("Response should contain 'error' field: " + body).isTrue();
            JsonNode error = root.get("error");
            assertThat(error.has("code")).as("error.code must be present").isTrue();
            assertThat(error.has("message")).as("error.message must be present").isTrue();
            assertThat(error.has("details")).as("error.details must be present").isTrue();
            assertThat(error.has("request_id")).as("error.request_id must be present").isTrue();
            assertThat(error.has("correlation_id")).as("error.correlation_id must be present").isTrue();

            assertThat(error.get("code").asText()).isNotBlank();
            assertThat(error.get("request_id").asText()).isNotBlank();
            assertThat(error.get("correlation_id").asText()).isNotBlank();
        }

        @Test
        @DisplayName("Error response Content-Type is application/json")
        void errorResponseIsJson() throws Exception {
            Assumptions.assumeTrue(readEndpoint != null,
                    "SKIPPED: No v1.1 read endpoint discovered for error envelope test");

            mockMvc.perform(get(readEndpoint))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
        }
    }

    // ── 3. Idempotency ─────────────────────────────────────────────

    @Nested
    @DisplayName("Idempotency")
    class Idempotency {

        @Test
        @DisplayName("Missing Idempotency-Key on command returns 400 IDEMPOTENCY_KEY_REQUIRED")
        void missingIdempotencyKeyReturns400() throws Exception {
            Assumptions.assumeTrue(commandEndpoint != null,
                    "SKIPPED: No v1.1 command endpoint discovered for idempotency test");

            MockHttpServletRequestBuilder req = buildCommandRequest(commandEndpoint, commandMethod)
                    .header("X-Tenant-ID", "moh-zw")
                    .header("X-Pod-ID", "national")
                    .header("X-Request-ID", "req-1")
                    .header("X-Correlation-ID", "corr-1")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\":\"test\"}");

            MvcResult result = mockMvc.perform(req)
                    .andExpect(status().isBadRequest())
                    .andReturn();

            assertErrorEnvelope(result, "IDEMPOTENCY_KEY_REQUIRED");
        }

        @Test
        @DisplayName("Same Idempotency-Key + same body replays original response")
        void sameKeyAndBodyReplays() throws Exception {
            Assumptions.assumeTrue(commandEndpoint != null,
                    "SKIPPED: No v1.1 command endpoint discovered for idempotency test");

            String idempotencyKey = "idem-replay-" + System.nanoTime();
            String body = "{\"name\":\"replay-test\"}";

            MvcResult first = mockMvc.perform(buildCommandRequest(commandEndpoint, commandMethod)
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

            MvcResult second = mockMvc.perform(buildCommandRequest(commandEndpoint, commandMethod)
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
            Assumptions.assumeTrue(commandEndpoint != null,
                    "SKIPPED: No v1.1 command endpoint discovered for idempotency test");

            String idempotencyKey = "idem-conflict-" + System.nanoTime();

            mockMvc.perform(buildCommandRequest(commandEndpoint, commandMethod)
                            .header("X-Tenant-ID", "moh-zw")
                            .header("X-Pod-ID", "national")
                            .header("X-Request-ID", "req-1")
                            .header("X-Correlation-ID", "corr-1")
                            .header("Idempotency-Key", idempotencyKey)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"first\"}"))
                    .andReturn();

            MvcResult result = mockMvc.perform(buildCommandRequest(commandEndpoint, commandMethod)
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
            Assumptions.assumeTrue(federationEndpoint != null,
                    "SKIPPED: No federation-gated endpoint configured for this service");

            MvcResult result = mockMvc.perform(post(federationEndpoint)
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
            Assumptions.assumeTrue(federationEndpoint != null,
                    "SKIPPED: No federation-gated endpoint configured for this service");

            mockMvc.perform(post(federationEndpoint)
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

    // ── Helpers ─────────────────────────────────────────────────────

    private MockHttpServletRequestBuilder buildCommandRequest(String path, String method) {
        return switch (method) {
            case "PUT" -> put(path);
            case "PATCH" -> patch(path);
            default -> post(path);
        };
    }

    protected void assertErrorEnvelope(MvcResult result, String expectedCode) throws Exception {
        String body = result.getResponse().getContentAsString();
        assertThat(body).as("Response body should not be empty").isNotBlank();

        JsonNode root = MAPPER.readTree(body);
        assertThat(root.has("error"))
                .as("Response should contain 'error' field: " + body)
                .isTrue();

        JsonNode error = root.get("error");
        assertThat(error.get("code").asText())
                .as("error.code should be " + expectedCode)
                .isEqualTo(expectedCode);
        assertThat(error.has("message"))
                .as("error.message must be present")
                .isTrue();
        assertThat(error.has("request_id"))
                .as("error.request_id must be present")
                .isTrue();
        assertThat(error.has("correlation_id"))
                .as("error.correlation_id must be present")
                .isTrue();
    }
}
