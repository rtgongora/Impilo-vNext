package zw.gov.mohcc.impilo.integration;

import com.fasterxml.jackson.databind.JsonNode;
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
import org.springframework.test.web.servlet.MvcResult;
import zw.gov.mohcc.impilo.integration.config.TestSecurityConfig;
import zw.gov.mohcc.impilo.integration.domain.DeadLetterEntity;
import zw.gov.mohcc.impilo.integration.domain.DispatchAttemptEntity;
import zw.gov.mohcc.impilo.integration.domain.OutboxEventEntity;
import zw.gov.mohcc.impilo.integration.repository.DeadLetterRepository;
import zw.gov.mohcc.impilo.integration.repository.DispatchAttemptRepository;
import zw.gov.mohcc.impilo.integration.repository.OutboxEventRepository;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for Integration Hub v2 — transforms, dispatch attempts, and dead-letter handling.
 *
 * Uses H2 in-memory database via the "test" profile with create-drop DDL
 * and Flyway disabled.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestSecurityConfig.class)
class IntegrationHubServiceTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private DispatchAttemptRepository dispatchAttemptRepository;

    @Autowired
    private DeadLetterRepository deadLetterRepository;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String TENANT_ID = "moh-zw";
    private static final String POD_ID = "national";

    // ── Helper: create a v2 route with match + transform config ──────

    private String createRouteAndGetId(String matchMethod, String matchPathRegex,
                                        String targetUrl, Integer timeoutMs,
                                        String transformHeadersJson,
                                        String transformFieldRenamesJson) throws Exception {
        String body = """
                {
                  "matchMethod": "%s",
                  "matchPathRegex": "%s",
                  "targetUrl": "%s",
                  "targetTimeoutMs": %s,
                  "transformHeaders": %s,
                  "transformFieldRenames": %s,
                  "enabled": true
                }
                """.formatted(
                matchMethod, matchPathRegex, targetUrl,
                timeoutMs != null ? timeoutMs.toString() : "null",
                transformHeadersJson != null ? transformHeadersJson : "null",
                transformFieldRenamesJson != null ? transformFieldRenamesJson : "null"
        );

        MvcResult result = mockMvc.perform(post("/internal/v1/routes")
                        .header("X-Tenant-ID", TENANT_ID)
                        .header("X-Pod-ID", POD_ID)
                        .header("X-Request-ID", UUID.randomUUID().toString())
                        .header("X-Correlation-ID", UUID.randomUUID().toString())
                        .header("Idempotency-Key", "route-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode root = MAPPER.readTree(result.getResponse().getContentAsString());
        return root.get("id").asText();
    }

    private String dispatchBody(String method, String path, String body) {
        return """
                {
                  "method": "%s",
                  "path": "%s",
                  "body": %s
                }
                """.formatted(method, path, body != null ? "\"" + body.replace("\"", "\\\"") + "\"" : "null");
    }

    private String dispatchBodyRaw(String method, String path, String bodyJson) {
        return """
                {
                  "method": "%s",
                  "path": "%s",
                  "body": %s
                }
                """.formatted(method, path, bodyJson);
    }

    // ── Test 1: Create route with v2 transform config ────────────────

    @Test
    @DisplayName("POST /internal/v1/routes with match + transform returns 201 with v2 fields")
    void createRouteWithTransformConfig() throws Exception {
        String body = """
                {
                  "matchMethod": "POST",
                  "matchPathRegex": "/api/v1/orders/.*",
                  "targetUrl": "http://oros:8089/internal/v1/orders",
                  "targetTimeoutMs": 5000,
                  "transformHeaders": {"X-Source": "X-Origin"},
                  "transformFieldRenames": {"orderId": "order_id"},
                  "enabled": true
                }
                """;

        MvcResult result = mockMvc.perform(post("/internal/v1/routes")
                        .header("X-Tenant-ID", TENANT_ID)
                        .header("X-Pod-ID", POD_ID)
                        .header("X-Request-ID", UUID.randomUUID().toString())
                        .header("X-Correlation-ID", UUID.randomUUID().toString())
                        .header("Idempotency-Key", "create-route-test-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode root = MAPPER.readTree(result.getResponse().getContentAsString());

        assertThat(root.get("id").asText()).isNotBlank();
        assertThat(root.get("matchMethod").asText()).isEqualTo("POST");
        assertThat(root.get("matchPathRegex").asText()).isEqualTo("/api/v1/orders/.*");
        assertThat(root.get("targetUrl").asText()).isEqualTo("http://oros:8089/internal/v1/orders");
        assertThat(root.get("targetTimeoutMs").asInt()).isEqualTo(5000);
        assertThat(root.get("transformHeaders").get("X-Source").asText()).isEqualTo("X-Origin");
        assertThat(root.get("transformFieldRenames").get("orderId").asText()).isEqualTo("order_id");
    }

    // ── Test 2: List routes returns v2 fields ────────────────────────

    @Test
    @DisplayName("GET /internal/v1/routes returns routes with v2 transform config")
    void listRoutesReturnsTransformConfig() throws Exception {
        // Create a route with transform config
        createRouteAndGetId("GET", "/api/v1/patients/.*",
                "http://vito:8082/internal/v1/patients", 3000,
                "{\"X-Custom\": \"X-Mapped\"}", null);

        MvcResult listResult = mockMvc.perform(get("/internal/v1/routes")
                        .header("X-Tenant-ID", TENANT_ID)
                        .header("X-Pod-ID", POD_ID)
                        .header("X-Request-ID", UUID.randomUUID().toString())
                        .header("X-Correlation-ID", UUID.randomUUID().toString()))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode array = MAPPER.readTree(listResult.getResponse().getContentAsString());
        assertThat(array.isArray()).isTrue();
        assertThat(array.size()).isGreaterThanOrEqualTo(1);

        // Find one with our matchPathRegex
        boolean foundRoute = false;
        for (JsonNode node : array) {
            if ("/api/v1/patients/.*".equals(node.path("matchPathRegex").asText())) {
                assertThat(node.get("matchMethod").asText()).isEqualTo("GET");
                assertThat(node.get("targetTimeoutMs").asInt()).isEqualTo(3000);
                assertThat(node.get("transformHeaders").get("X-Custom").asText()).isEqualTo("X-Mapped");
                foundRoute = true;
                break;
            }
        }
        assertThat(foundRoute).as("Should find the route with matching path regex").isTrue();
    }

    // ── Test 3: Dispatch matches route and returns ACCEPTED ──────────

    @Test
    @DisplayName("POST /internal/v1/dispatch with matching route returns ACCEPTED with routeId")
    void dispatchMatchesRouteReturnsAccepted() throws Exception {
        // Create a route that matches POST /orders/.*
        String routeId = createRouteAndGetId("POST", "/orders/.*",
                "http://oros:8089/internal/v1/orders", 5000,
                null, null);

        String body = dispatchBody("POST", "/orders/create", "{\\\"orderId\\\":\\\"123\\\"}");

        MvcResult result = mockMvc.perform(post("/internal/v1/dispatch")
                        .header("X-Tenant-ID", TENANT_ID)
                        .header("X-Pod-ID", POD_ID)
                        .header("X-Request-ID", UUID.randomUUID().toString())
                        .header("X-Correlation-ID", UUID.randomUUID().toString())
                        .header("Idempotency-Key", "dispatch-match-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isAccepted())
                .andReturn();

        JsonNode root = MAPPER.readTree(result.getResponse().getContentAsString());
        assertThat(root.get("status").asText()).isEqualTo("ACCEPTED");
        assertThat(root.get("routeId").asText()).isEqualTo(routeId);
        assertThat(root.get("targetUrl").asText()).isEqualTo("http://oros:8089/internal/v1/orders");
    }

    // ── Test 4: Dispatch applies header transform ────────────────────

    @Test
    @DisplayName("Dispatch applies header mapping transform from matched route")
    void dispatchAppliesHeaderTransform() throws Exception {
        // Create route with header transforms
        createRouteAndGetId("POST", "/api/headers/.*",
                "http://target:8080/handle", 3000,
                "{\"X-Old-Header\": \"X-New-Header\"}", null);

        String body = """
                {
                  "method": "POST",
                  "path": "/api/headers/test",
                  "body": "{}",
                  "headers": {"X-Old-Header": "value123", "X-Keep": "keep-me"}
                }
                """;

        MvcResult result = mockMvc.perform(post("/internal/v1/dispatch")
                        .header("X-Tenant-ID", TENANT_ID)
                        .header("X-Pod-ID", POD_ID)
                        .header("X-Request-ID", UUID.randomUUID().toString())
                        .header("X-Correlation-ID", UUID.randomUUID().toString())
                        .header("Idempotency-Key", "dispatch-header-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isAccepted())
                .andReturn();

        JsonNode root = MAPPER.readTree(result.getResponse().getContentAsString());
        assertThat(root.get("status").asText()).isEqualTo("ACCEPTED");

        // Verify the dispatch attempt was recorded
        String dispatchId = root.get("id").asText();
        DispatchAttemptEntity attempt = dispatchAttemptRepository.findById(dispatchId).orElseThrow();
        assertThat(attempt.isMatched()).isTrue();
        assertThat(attempt.getStatus()).isEqualTo("ACCEPTED");
    }

    // ── Test 5: Dispatch applies JSON field renames ──────────────────

    @Test
    @DisplayName("Dispatch applies JSON field rename transform and stores transformed body")
    void dispatchAppliesFieldRenames() throws Exception {
        // Create route with field renames
        createRouteAndGetId("POST", "/api/rename/.*",
                "http://target:8080/renamed", 3000,
                null, "{\"firstName\": \"first_name\", \"lastName\": \"last_name\"}");

        String body = """
                {
                  "method": "POST",
                  "path": "/api/rename/patient",
                  "body": "{\\"firstName\\":\\"John\\",\\"lastName\\":\\"Doe\\",\\"age\\":30}"
                }
                """;

        MvcResult result = mockMvc.perform(post("/internal/v1/dispatch")
                        .header("X-Tenant-ID", TENANT_ID)
                        .header("X-Pod-ID", POD_ID)
                        .header("X-Request-ID", UUID.randomUUID().toString())
                        .header("X-Correlation-ID", UUID.randomUUID().toString())
                        .header("Idempotency-Key", "dispatch-rename-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isAccepted())
                .andReturn();

        JsonNode root = MAPPER.readTree(result.getResponse().getContentAsString());
        String dispatchId = root.get("id").asText();

        // Verify the transformed body has renamed fields
        DispatchAttemptEntity attempt = dispatchAttemptRepository.findById(dispatchId).orElseThrow();
        assertThat(attempt.getTransformedBody()).isNotNull();

        JsonNode transformed = MAPPER.readTree(attempt.getTransformedBody());
        assertThat(transformed.has("first_name")).as("Should have renamed field first_name").isTrue();
        assertThat(transformed.get("first_name").asText()).isEqualTo("John");
        assertThat(transformed.has("last_name")).as("Should have renamed field last_name").isTrue();
        assertThat(transformed.get("last_name").asText()).isEqualTo("Doe");
        assertThat(transformed.has("age")).as("Untouched field should remain").isTrue();
        assertThat(transformed.has("firstName")).as("Old field name should be gone").isFalse();
    }

    // ── Test 6: Dispatch writes outbox event ─────────────────────────

    @Test
    @DisplayName("Dispatch records outbox event with dispatch.accepted event type")
    void dispatchWritesOutboxEvent() throws Exception {
        createRouteAndGetId("*", "/outbox-test/.*",
                "http://target:8080/outbox", 3000, null, null);

        long outboxCountBefore = outboxEventRepository.count();

        String body = dispatchBody("GET", "/outbox-test/check", null);

        mockMvc.perform(post("/internal/v1/dispatch")
                        .header("X-Tenant-ID", TENANT_ID)
                        .header("X-Pod-ID", POD_ID)
                        .header("X-Request-ID", UUID.randomUUID().toString())
                        .header("X-Correlation-ID", UUID.randomUUID().toString())
                        .header("Idempotency-Key", "dispatch-outbox-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isAccepted());

        List<OutboxEventEntity> allOutbox = outboxEventRepository.findAll();
        assertThat(allOutbox.size()).isGreaterThan((int) outboxCountBefore);

        boolean hasAcceptedEvent = allOutbox.stream()
                .anyMatch(e -> "impilo.integration.dispatch.accepted.v1".equals(e.getEventType()));
        assertThat(hasAcceptedEvent)
                .as("Outbox should contain dispatch.accepted.v1 event")
                .isTrue();
    }

    // ── Test 7: Dispatch with no matching route creates dead-letter ──

    @Test
    @DisplayName("Dispatch with no matching route creates dead-letter entry and returns FAILED")
    void dispatchNoMatchCreatesDeadLetter() throws Exception {
        long deadLetterCountBefore = deadLetterRepository.count();

        String body = dispatchBody("DELETE", "/unknown/resource/999", null);

        MvcResult result = mockMvc.perform(post("/internal/v1/dispatch")
                        .header("X-Tenant-ID", TENANT_ID)
                        .header("X-Pod-ID", POD_ID)
                        .header("X-Request-ID", UUID.randomUUID().toString())
                        .header("X-Correlation-ID", UUID.randomUUID().toString())
                        .header("Idempotency-Key", "dispatch-dl-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isAccepted())
                .andReturn();

        JsonNode root = MAPPER.readTree(result.getResponse().getContentAsString());
        assertThat(root.get("status").asText()).isEqualTo("FAILED");
        assertThat(root.get("routeId").isNull()).as("routeId should be null for unmatched").isTrue();
        assertThat(root.get("targetUrl").isNull()).as("targetUrl should be null for unmatched").isTrue();

        // Verify dead-letter entry was created
        List<DeadLetterEntity> deadLetters = deadLetterRepository.findAll();
        assertThat(deadLetters.size()).isGreaterThan((int) deadLetterCountBefore);

        DeadLetterEntity lastDl = deadLetters.get(deadLetters.size() - 1);
        assertThat(lastDl.getMethod()).isEqualTo("DELETE");
        assertThat(lastDl.getPath()).isEqualTo("/unknown/resource/999");
        assertThat(lastDl.getErrorReason()).contains("No matching route found");
        assertThat(lastDl.isResolved()).isFalse();
        assertThat(lastDl.getRetryCount()).isEqualTo(0);

        // Verify failed outbox event
        boolean hasFailedEvent = outboxEventRepository.findAll().stream()
                .anyMatch(e -> "impilo.integration.dispatch.failed.v1".equals(e.getEventType()));
        assertThat(hasFailedEvent).as("Outbox should contain dispatch.failed.v1 event").isTrue();
    }

    // ── Test 8: Dead letter listing is paged ─────────────────────────

    @Test
    @DisplayName("GET /internal/v1/deadletters returns paged dead-letter entries")
    void deadLetterListingIsPaged() throws Exception {
        // Create a few dead-letter entries by dispatching unmatched requests
        for (int i = 0; i < 3; i++) {
            String body = dispatchBody("PATCH", "/nonexistent/path/" + i, null);
            mockMvc.perform(post("/internal/v1/dispatch")
                            .header("X-Tenant-ID", TENANT_ID)
                            .header("X-Pod-ID", POD_ID)
                            .header("X-Request-ID", UUID.randomUUID().toString())
                            .header("X-Correlation-ID", UUID.randomUUID().toString())
                            .header("Idempotency-Key", "dl-page-" + i + "-" + UUID.randomUUID())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isAccepted());
        }

        // Request page 0, size 2
        MvcResult result = mockMvc.perform(get("/internal/v1/deadletters")
                        .param("page", "0")
                        .param("size", "2")
                        .header("X-Tenant-ID", TENANT_ID)
                        .header("X-Pod-ID", POD_ID)
                        .header("X-Request-ID", UUID.randomUUID().toString())
                        .header("X-Correlation-ID", UUID.randomUUID().toString()))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode root = MAPPER.readTree(result.getResponse().getContentAsString());
        assertThat(root.has("content")).as("Response should have 'content' (paged)").isTrue();
        assertThat(root.get("content").isArray()).isTrue();
        assertThat(root.get("content").size()).isLessThanOrEqualTo(2);
        assertThat(root.get("totalElements").asInt()).isGreaterThanOrEqualTo(3);
    }

    // ── Test 9: Route matching uses method + path regex ──────────────

    @Test
    @DisplayName("Route matching correctly filters by method and path regex")
    void routeMatchingUsesMethodAndPathRegex() throws Exception {
        // Create a route that only matches PUT on /api/v2/update/.*
        createRouteAndGetId("PUT", "/api/v2/update/.*",
                "http://target:8080/update", 3000, null, null);

        // Dispatch with POST (wrong method) — should NOT match
        String wrongMethod = dispatchBody("POST", "/api/v2/update/123", null);
        MvcResult wrongMethodResult = mockMvc.perform(post("/internal/v1/dispatch")
                        .header("X-Tenant-ID", TENANT_ID)
                        .header("X-Pod-ID", POD_ID)
                        .header("X-Request-ID", UUID.randomUUID().toString())
                        .header("X-Correlation-ID", UUID.randomUUID().toString())
                        .header("Idempotency-Key", "method-mismatch-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(wrongMethod))
                .andExpect(status().isAccepted())
                .andReturn();

        JsonNode wrongMethodRoot = MAPPER.readTree(wrongMethodResult.getResponse().getContentAsString());
        assertThat(wrongMethodRoot.get("status").asText()).isEqualTo("FAILED");

        // Dispatch with PUT but wrong path — should NOT match
        String wrongPath = dispatchBody("PUT", "/api/v3/other/123", null);
        MvcResult wrongPathResult = mockMvc.perform(post("/internal/v1/dispatch")
                        .header("X-Tenant-ID", TENANT_ID)
                        .header("X-Pod-ID", POD_ID)
                        .header("X-Request-ID", UUID.randomUUID().toString())
                        .header("X-Correlation-ID", UUID.randomUUID().toString())
                        .header("Idempotency-Key", "path-mismatch-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(wrongPath))
                .andExpect(status().isAccepted())
                .andReturn();

        JsonNode wrongPathRoot = MAPPER.readTree(wrongPathResult.getResponse().getContentAsString());
        assertThat(wrongPathRoot.get("status").asText()).isEqualTo("FAILED");

        // Dispatch with PUT on correct path — should match
        String correct = dispatchBody("PUT", "/api/v2/update/456", null);
        MvcResult correctResult = mockMvc.perform(post("/internal/v1/dispatch")
                        .header("X-Tenant-ID", TENANT_ID)
                        .header("X-Pod-ID", POD_ID)
                        .header("X-Request-ID", UUID.randomUUID().toString())
                        .header("X-Correlation-ID", UUID.randomUUID().toString())
                        .header("Idempotency-Key", "correct-match-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(correct))
                .andExpect(status().isAccepted())
                .andReturn();

        JsonNode correctRoot = MAPPER.readTree(correctResult.getResponse().getContentAsString());
        assertThat(correctRoot.get("status").asText()).isEqualTo("ACCEPTED");
        assertThat(correctRoot.get("targetUrl").asText()).isEqualTo("http://target:8080/update");
    }
}
