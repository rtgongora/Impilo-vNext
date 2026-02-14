package zw.gov.mohcc.impilo.rules;

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
import zw.gov.mohcc.impilo.rules.domain.OutboxEventEntity;
import zw.gov.mohcc.impilo.rules.repository.OutboxEventRepository;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RulesServiceTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String TENANT_ID = "moh-zw";
    private static final String POD_ID = "national";

    private static String ruleBody() {
        return """
                {
                  "name": "age-check",
                  "expression": "facts.age >= 18",
                  "enabled": true
                }
                """;
    }

    private static String ruleBody(String name, String expression) {
        return """
                {
                  "name": "%s",
                  "expression": "%s",
                  "enabled": true
                }
                """.formatted(name, expression);
    }

    private static String evaluateBody() {
        return """
                {
                  "facts": {"age": 25, "country": "ZW"}
                }
                """;
    }

    @Test
    @DisplayName("POST /internal/v1/rules persists rule and returns 201 with id")
    void ruleUpsertPersistsRow() throws Exception {
        MvcResult result = mockMvc.perform(post("/internal/v1/rules")
                        .header("X-Tenant-ID", TENANT_ID)
                        .header("X-Pod-ID", POD_ID)
                        .header("X-Request-ID", UUID.randomUUID().toString())
                        .header("X-Correlation-ID", UUID.randomUUID().toString())
                        .header("Idempotency-Key", "rule-upsert-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ruleBody()))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode root = MAPPER.readTree(result.getResponse().getContentAsString());
        assertThat(root.has("id")).isTrue();
        assertThat(root.get("id").asText()).isNotBlank();
        assertThat(root.get("name").asText()).isEqualTo("age-check");
        assertThat(root.get("expression").asText()).isEqualTo("facts.age >= 18");
    }

    @Test
    @DisplayName("GET /internal/v1/rules returns all rules for tenant")
    void listRulesReturnsExpectedSize() throws Exception {
        String correlationId = UUID.randomUUID().toString();

        mockMvc.perform(post("/internal/v1/rules")
                        .header("X-Tenant-ID", TENANT_ID)
                        .header("X-Pod-ID", POD_ID)
                        .header("X-Request-ID", UUID.randomUUID().toString())
                        .header("X-Correlation-ID", correlationId)
                        .header("Idempotency-Key", "rule-list-1-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ruleBody("country-check", "facts.country == 'ZW'")))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/internal/v1/rules")
                        .header("X-Tenant-ID", TENANT_ID)
                        .header("X-Pod-ID", POD_ID)
                        .header("X-Request-ID", UUID.randomUUID().toString())
                        .header("X-Correlation-ID", correlationId)
                        .header("Idempotency-Key", "rule-list-2-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ruleBody("status-check", "facts.status == 'ACTIVE'")))
                .andExpect(status().isCreated());

        MvcResult listResult = mockMvc.perform(get("/internal/v1/rules")
                        .header("X-Tenant-ID", TENANT_ID)
                        .header("X-Pod-ID", POD_ID)
                        .header("X-Request-ID", UUID.randomUUID().toString())
                        .header("X-Correlation-ID", correlationId))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode array = MAPPER.readTree(listResult.getResponse().getContentAsString());
        assertThat(array.isArray()).isTrue();
        assertThat(array.size()).isGreaterThanOrEqualTo(2);
    }

    @Test
    @DisplayName("POST /internal/v1/evaluate returns decisions and persists outbox event")
    void evaluatePersistsOutboxRow() throws Exception {
        mockMvc.perform(post("/internal/v1/rules")
                        .header("X-Tenant-ID", "moh-eval")
                        .header("X-Pod-ID", POD_ID)
                        .header("X-Request-ID", UUID.randomUUID().toString())
                        .header("X-Correlation-ID", UUID.randomUUID().toString())
                        .header("Idempotency-Key", "rule-eval-setup-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ruleBody("eval-age-rule", "facts.age >= 18")))
                .andExpect(status().isCreated());

        long outboxCountBefore = outboxEventRepository.count();

        MvcResult result = mockMvc.perform(post("/internal/v1/evaluate")
                        .header("X-Tenant-ID", "moh-eval")
                        .header("X-Pod-ID", POD_ID)
                        .header("X-Request-ID", UUID.randomUUID().toString())
                        .header("X-Correlation-ID", UUID.randomUUID().toString())
                        .header("Idempotency-Key", "eval-test-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(evaluateBody()))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode root = MAPPER.readTree(result.getResponse().getContentAsString());
        assertThat(root.has("decisions")).isTrue();
        assertThat(root.get("decisions").isArray()).isTrue();
        assertThat(root.get("decisions").size()).isGreaterThanOrEqualTo(1);

        List<OutboxEventEntity> allOutboxEvents = outboxEventRepository.findAll();
        assertThat(allOutboxEvents.size()).isGreaterThan((int) outboxCountBefore);

        boolean hasDecisionEvent = allOutboxEvents.stream()
                .anyMatch(e -> "impilo.rules.decision.recorded.v1".equals(e.getEventType()));
        assertThat(hasDecisionEvent)
                .as("Outbox should contain 'impilo.rules.decision.recorded.v1'")
                .isTrue();
    }

    @Test
    @DisplayName("Same Idempotency-Key with same body replays original response")
    void idempotencyReplayReturnsSameId() throws Exception {
        String idempotencyKey = "rule-replay-" + UUID.randomUUID();
        String body = ruleBody();

        MvcResult first = mockMvc.perform(post("/internal/v1/rules")
                        .header("X-Tenant-ID", TENANT_ID)
                        .header("X-Pod-ID", POD_ID)
                        .header("X-Request-ID", UUID.randomUUID().toString())
                        .header("X-Correlation-ID", UUID.randomUUID().toString())
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();

        MvcResult second = mockMvc.perform(post("/internal/v1/rules")
                        .header("X-Tenant-ID", TENANT_ID)
                        .header("X-Pod-ID", POD_ID)
                        .header("X-Request-ID", UUID.randomUUID().toString())
                        .header("X-Correlation-ID", UUID.randomUUID().toString())
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andReturn();

        assertThat(second.getResponse().getStatus()).isEqualTo(first.getResponse().getStatus());
        assertThat(second.getResponse().getContentAsString())
                .isEqualTo(first.getResponse().getContentAsString());
    }

    @Test
    @DisplayName("Same Idempotency-Key with different body returns 409 IDENTITY_CONFLICT")
    void idempotencyConflictReturns409() throws Exception {
        String idempotencyKey = "rule-conflict-" + UUID.randomUUID();

        mockMvc.perform(post("/internal/v1/rules")
                        .header("X-Tenant-ID", TENANT_ID)
                        .header("X-Pod-ID", POD_ID)
                        .header("X-Request-ID", UUID.randomUUID().toString())
                        .header("X-Correlation-ID", UUID.randomUUID().toString())
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ruleBody("first-rule", "facts.age >= 18")))
                .andExpect(status().isCreated());

        MvcResult conflict = mockMvc.perform(post("/internal/v1/rules")
                        .header("X-Tenant-ID", TENANT_ID)
                        .header("X-Pod-ID", POD_ID)
                        .header("X-Request-ID", UUID.randomUUID().toString())
                        .header("X-Correlation-ID", UUID.randomUUID().toString())
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ruleBody("different-rule", "facts.score > 80")))
                .andExpect(status().isConflict())
                .andReturn();

        JsonNode root = MAPPER.readTree(conflict.getResponse().getContentAsString());
        assertThat(root.has("error")).isTrue();
        assertThat(root.get("error").get("code").asText()).isEqualTo("IDENTITY_CONFLICT");
    }
}
