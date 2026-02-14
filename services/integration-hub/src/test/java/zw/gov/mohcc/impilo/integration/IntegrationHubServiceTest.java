package zw.gov.mohcc.impilo.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import zw.gov.mohcc.impilo.integration.domain.OutboxEventEntity;
import zw.gov.mohcc.impilo.integration.repository.OutboxEventRepository;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for the Integration Hub service endpoints.
 *
 * Uses H2 in-memory database via the "test" profile with create-drop DDL
 * and Flyway disabled.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class IntegrationHubServiceTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String TENANT_ID = "moh-zw";
    private static final String POD_ID = "national";

    // ── Route body templates ─────────────────────────────────────

    private static String routeBody() {
        return """
                {
                  "sourceService": "oros",
                  "eventTypePrefix": "oros.order.*",
                  "targetService": "pharmacy",
                  "targetUrl": "http://pharmacy:8096/internal/v1/orders",
                  "enabled": true
                }
                """;
    }

    private static String routeBody(String sourceService, String targetService) {
        return """
                {
                  "sourceService": "%s",
                  "eventTypePrefix": "%s.order.*",
                  "targetService": "%s",
                  "targetUrl": "http://%s:8096/internal/v1/orders",
                  "enabled": true
                }
                """.formatted(sourceService, sourceService, targetService, targetService);
    }

    private static String dispatchBody() {
        return """
                {
                  "sourceService": "oros",
                  "eventType": "oros.order.placed",
                  "targetService": "pharmacy",
                  "payloadJson": "{\\"orderId\\":\\"123\\"}"
                }
                """;
    }

    // ── Test 1: Route upsert persists a row ──────────────────────

    @Test
    @DisplayName("POST /internal/v1/routes persists route and returns 201 with id")
    void routeUpsertPersistsRow() throws Exception {
        String requestId = UUID.randomUUID().toString();
        String correlationId = UUID.randomUUID().toString();
        String idempotencyKey = "route-upsert-" + UUID.randomUUID();

        MvcResult result = mockMvc.perform(post("/internal/v1/routes")
                        .header("X-Tenant-ID", TENANT_ID)
                        .header("X-Pod-ID", POD_ID)
                        .header("X-Request-ID", requestId)
                        .header("X-Correlation-ID", correlationId)
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(routeBody()))
                .andExpect(status().isCreated())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        JsonNode root = MAPPER.readTree(body);

        assertThat(root.has("id")).as("Response must contain 'id' field").isTrue();
        assertThat(root.get("id").asText()).as("id must not be blank").isNotBlank();
        assertThat(root.get("sourceService").asText()).isEqualTo("oros");
        assertThat(root.get("targetService").asText()).isEqualTo("pharmacy");
        assertThat(root.get("enabled").asBoolean()).isTrue();
    }

    // ── Test 2: List routes returns expected size ─────────────────

    @Test
    @DisplayName("GET /internal/v1/routes returns all routes for tenant")
    void listRoutesReturnsExpectedSize() throws Exception {
        String correlationId = UUID.randomUUID().toString();

        // Create first route
        mockMvc.perform(post("/internal/v1/routes")
                        .header("X-Tenant-ID", TENANT_ID)
                        .header("X-Pod-ID", POD_ID)
                        .header("X-Request-ID", UUID.randomUUID().toString())
                        .header("X-Correlation-ID", correlationId)
                        .header("Idempotency-Key", "list-test-1-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(routeBody("pct", "butano")))
                .andExpect(status().isCreated());

        // Create second route
        mockMvc.perform(post("/internal/v1/routes")
                        .header("X-Tenant-ID", TENANT_ID)
                        .header("X-Pod-ID", POD_ID)
                        .header("X-Request-ID", UUID.randomUUID().toString())
                        .header("X-Correlation-ID", correlationId)
                        .header("Idempotency-Key", "list-test-2-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(routeBody("zibo", "tuso")))
                .andExpect(status().isCreated());

        // List routes
        MvcResult listResult = mockMvc.perform(get("/internal/v1/routes")
                        .header("X-Tenant-ID", TENANT_ID)
                        .header("X-Pod-ID", POD_ID)
                        .header("X-Request-ID", UUID.randomUUID().toString())
                        .header("X-Correlation-ID", correlationId))
                .andExpect(status().isOk())
                .andReturn();

        String body = listResult.getResponse().getContentAsString();
        JsonNode array = MAPPER.readTree(body);

        assertThat(array.isArray()).as("Response should be a JSON array").isTrue();
        // At least the 2 routes we just created (may include routes from other tests in same tenant)
        assertThat(array.size()).as("Should have at least 2 routes").isGreaterThanOrEqualTo(2);
    }

    // ── Test 3: Dispatch persists outbox row ─────────────────────

    @Test
    @DisplayName("POST /internal/v1/dispatch returns ACCEPTED and persists outbox event")
    void dispatchPersistsOutboxRow() throws Exception {
        String requestId = UUID.randomUUID().toString();
        String correlationId = UUID.randomUUID().toString();
        String idempotencyKey = "dispatch-test-" + UUID.randomUUID();

        long outboxCountBefore = outboxEventRepository.count();

        MvcResult result = mockMvc.perform(post("/internal/v1/dispatch")
                        .header("X-Tenant-ID", TENANT_ID)
                        .header("X-Pod-ID", POD_ID)
                        .header("X-Request-ID", requestId)
                        .header("X-Correlation-ID", correlationId)
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(dispatchBody()))
                .andExpect(status().isAccepted())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        JsonNode root = MAPPER.readTree(body);

        assertThat(root.get("status").asText()).isEqualTo("ACCEPTED");
        assertThat(root.has("id")).as("Response must contain 'id' field").isTrue();

        // Verify outbox row was created with the correct event type
        List<OutboxEventEntity> allOutboxEvents = outboxEventRepository.findAll();
        assertThat(allOutboxEvents.size()).isGreaterThan((int) outboxCountBefore);

        boolean hasDispatchEvent = allOutboxEvents.stream()
                .anyMatch(e -> "impilo.integration.dispatch.requested.v1".equals(e.getEventType()));
        assertThat(hasDispatchEvent)
                .as("Outbox should contain an event with type 'impilo.integration.dispatch.requested.v1'")
                .isTrue();
    }

    // ── Test 4: Idempotency replay returns same id ───────────────

    @Test
    @DisplayName("Same Idempotency-Key with same body replays original response")
    void idempotencyReplayReturnsSameId() throws Exception {
        String correlationId = UUID.randomUUID().toString();
        String idempotencyKey = "replay-test-" + UUID.randomUUID();
        String body = routeBody();

        // First request
        MvcResult first = mockMvc.perform(post("/internal/v1/routes")
                        .header("X-Tenant-ID", TENANT_ID)
                        .header("X-Pod-ID", POD_ID)
                        .header("X-Request-ID", UUID.randomUUID().toString())
                        .header("X-Correlation-ID", correlationId)
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();

        String firstResponseBody = first.getResponse().getContentAsString();
        JsonNode firstJson = MAPPER.readTree(firstResponseBody);
        String firstId = firstJson.get("id").asText();

        // Second request with same key and same body
        MvcResult second = mockMvc.perform(post("/internal/v1/routes")
                        .header("X-Tenant-ID", TENANT_ID)
                        .header("X-Pod-ID", POD_ID)
                        .header("X-Request-ID", UUID.randomUUID().toString())
                        .header("X-Correlation-ID", correlationId)
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andReturn();

        String secondResponseBody = second.getResponse().getContentAsString();
        JsonNode secondJson = MAPPER.readTree(secondResponseBody);
        String secondId = secondJson.get("id").asText();

        assertThat(second.getResponse().getStatus()).isEqualTo(first.getResponse().getStatus());
        assertThat(secondId).as("Replayed response must return the same id").isEqualTo(firstId);
        assertThat(secondResponseBody).isEqualTo(firstResponseBody);
    }

    // ── Test 5: Idempotency conflict returns 409 ─────────────────

    @Test
    @DisplayName("Same Idempotency-Key with different body returns 409 IDENTITY_CONFLICT")
    void idempotencyConflictReturns409() throws Exception {
        String correlationId = UUID.randomUUID().toString();
        String idempotencyKey = "conflict-test-" + UUID.randomUUID();

        // First request
        mockMvc.perform(post("/internal/v1/routes")
                        .header("X-Tenant-ID", TENANT_ID)
                        .header("X-Pod-ID", POD_ID)
                        .header("X-Request-ID", UUID.randomUUID().toString())
                        .header("X-Correlation-ID", correlationId)
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(routeBody("oros", "pharmacy")))
                .andExpect(status().isCreated());

        // Second request with same key but different body
        MvcResult conflict = mockMvc.perform(post("/internal/v1/routes")
                        .header("X-Tenant-ID", TENANT_ID)
                        .header("X-Pod-ID", POD_ID)
                        .header("X-Request-ID", UUID.randomUUID().toString())
                        .header("X-Correlation-ID", correlationId)
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(routeBody("vito", "tuso")))
                .andExpect(status().isConflict())
                .andReturn();

        String body = conflict.getResponse().getContentAsString();
        JsonNode root = MAPPER.readTree(body);

        assertThat(root.has("error")).as("Response should contain 'error' field").isTrue();
        assertThat(root.get("error").get("code").asText()).isEqualTo("IDENTITY_CONFLICT");
    }
}
