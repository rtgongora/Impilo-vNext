package zw.gov.mohcc.impilo.rules;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import zw.gov.mohcc.impilo.rules.repository.EvaluationAuditRepository;
import zw.gov.mohcc.impilo.rules.repository.OutboxEventRepository;
import zw.gov.mohcc.impilo.rules.repository.RuleActivationRepository;
import zw.gov.mohcc.impilo.rules.repository.RuleVersionRepository;

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

    @Autowired
    private RuleVersionRepository versionRepository;

    @Autowired
    private RuleActivationRepository activationRepository;

    @Autowired
    private EvaluationAuditRepository auditRepository;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String TENANT_ID = "moh-zw";
    private static final String POD_ID = "national";

    @BeforeEach
    void cleanUp() {
        auditRepository.deleteAll();
        activationRepository.deleteAll();
        versionRepository.deleteAll();
        outboxEventRepository.deleteAll();
    }

    private MvcResult createRule(String key, String name) throws Exception {
        String body = """
                {"key": "%s", "name": "%s"}
                """.formatted(key, name);
        return mockMvc.perform(post("/internal/v1/rules")
                        .header("X-Tenant-ID", TENANT_ID)
                        .header("X-Pod-ID", POD_ID)
                        .header("X-Request-ID", UUID.randomUUID().toString())
                        .header("X-Correlation-ID", UUID.randomUUID().toString())
                        .header("Idempotency-Key", "cr-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andReturn();
    }

    private MvcResult createVersion(String key, String dslText) throws Exception {
        String body = """
                {"dslText": "%s"}
                """.formatted(dslText);
        return mockMvc.perform(post("/internal/v1/rules/" + key + "/versions")
                        .header("X-Tenant-ID", TENANT_ID)
                        .header("X-Pod-ID", POD_ID)
                        .header("X-Request-ID", UUID.randomUUID().toString())
                        .header("X-Correlation-ID", UUID.randomUUID().toString())
                        .header("Idempotency-Key", "cv-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andReturn();
    }

    private MvcResult activateVersion(String key, int version) throws Exception {
        return mockMvc.perform(post("/internal/v1/rules/" + key + "/activate")
                        .param("version", String.valueOf(version))
                        .header("X-Tenant-ID", TENANT_ID)
                        .header("X-Pod-ID", POD_ID)
                        .header("X-Request-ID", UUID.randomUUID().toString())
                        .header("X-Correlation-ID", UUID.randomUUID().toString())
                        .header("Idempotency-Key", "act-" + UUID.randomUUID()))
                .andReturn();
    }

    private MvcResult deactivateRule(String key) throws Exception {
        return mockMvc.perform(post("/internal/v1/rules/" + key + "/deactivate")
                        .header("X-Tenant-ID", TENANT_ID)
                        .header("X-Pod-ID", POD_ID)
                        .header("X-Request-ID", UUID.randomUUID().toString())
                        .header("X-Correlation-ID", UUID.randomUUID().toString())
                        .header("Idempotency-Key", "deact-" + UUID.randomUUID()))
                .andReturn();
    }

    private MvcResult evaluateRule(String key, String factsJson) throws Exception {
        String body = """
                {"facts": %s}
                """.formatted(factsJson);
        return mockMvc.perform(post("/internal/v1/rules/" + key + "/evaluate")
                        .header("X-Tenant-ID", TENANT_ID)
                        .header("X-Pod-ID", POD_ID)
                        .header("X-Request-ID", UUID.randomUUID().toString())
                        .header("X-Correlation-ID", UUID.randomUUID().toString())
                        .header("Idempotency-Key", "eval-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andReturn();
    }

    // ── Test 1: Rule creation ───────────────────────────────────

    @Test
    @DisplayName("POST /rules creates rule with key and returns 201")
    void createRuleReturns201WithKey() throws Exception {
        MvcResult result = createRule("age-check", "Age Check Rule");
        assertThat(result.getResponse().getStatus()).isEqualTo(201);

        JsonNode root = MAPPER.readTree(result.getResponse().getContentAsString());
        assertThat(root.get("key").asText()).isEqualTo("age-check");
        assertThat(root.get("name").asText()).isEqualTo("Age Check Rule");
        assertThat(root.get("status").asText()).isEqualTo("ACTIVE");
        assertThat(root.get("id").asText()).isNotBlank();
    }

    // ── Test 2: Duplicate key conflict ──────────────────────────

    @Test
    @DisplayName("POST /rules with duplicate key returns 409")
    void duplicateKeyReturns409() throws Exception {
        createRule("dup-check", "First");
        MvcResult second = createRule("dup-check", "Second");
        assertThat(second.getResponse().getStatus()).isEqualTo(409);
    }

    // ── Test 3: List rules ──────────────────────────────────────

    @Test
    @DisplayName("GET /rules returns all rules for tenant")
    void listRulesReturnsAll() throws Exception {
        createRule("list-a", "Rule A");
        createRule("list-b", "Rule B");

        MvcResult result = mockMvc.perform(get("/internal/v1/rules")
                        .header("X-Tenant-ID", TENANT_ID)
                        .header("X-Pod-ID", POD_ID)
                        .header("X-Request-ID", UUID.randomUUID().toString())
                        .header("X-Correlation-ID", UUID.randomUUID().toString()))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode array = MAPPER.readTree(result.getResponse().getContentAsString());
        assertThat(array.isArray()).isTrue();
        assertThat(array.size()).isGreaterThanOrEqualTo(2);
    }

    // ── Test 4: Version creation ────────────────────────────────

    @Test
    @DisplayName("POST /rules/{key}/versions creates version 1 and returns 201")
    void createVersionReturns201() throws Exception {
        createRule("ver-rule", "Versioned Rule");

        MvcResult result = createVersion("ver-rule", "facts.age >= 18");
        assertThat(result.getResponse().getStatus()).isEqualTo(201);

        JsonNode root = MAPPER.readTree(result.getResponse().getContentAsString());
        assertThat(root.get("version").asInt()).isEqualTo(1);
        assertThat(root.get("dslText").asText()).isEqualTo("facts.age >= 18");
    }

    // ── Test 5: Version auto-increment ──────────────────────────

    @Test
    @DisplayName("Second version auto-increments to version 2")
    void versionAutoIncrements() throws Exception {
        createRule("incr-rule", "Increment Rule");
        createVersion("incr-rule", "facts.age >= 18");
        MvcResult v2 = createVersion("incr-rule", "facts.age >= 21");

        assertThat(v2.getResponse().getStatus()).isEqualTo(201);
        JsonNode root = MAPPER.readTree(v2.getResponse().getContentAsString());
        assertThat(root.get("version").asInt()).isEqualTo(2);
    }

    // ── Test 6: Activation ──────────────────────────────────────

    @Test
    @DisplayName("POST /rules/{key}/activate sets rule as active with version")
    void activateVersionReturns200() throws Exception {
        createRule("act-rule", "Activate Rule");
        createVersion("act-rule", "facts.age >= 18");

        MvcResult result = activateVersion("act-rule", 1);
        assertThat(result.getResponse().getStatus()).isEqualTo(200);

        JsonNode root = MAPPER.readTree(result.getResponse().getContentAsString());
        assertThat(root.get("ruleKey").asText()).isEqualTo("act-rule");
        assertThat(root.get("version").asInt()).isEqualTo(1);
        assertThat(root.has("activeFrom")).isTrue();
    }

    // ── Test 7: Evaluate active rule — MATCH ────────────────────

    @Test
    @DisplayName("Evaluating active rule with matching facts returns MATCH")
    void evaluateMatchReturnsMatch() throws Exception {
        createRule("eval-match", "Eval Match");
        createVersion("eval-match", "facts.age >= 18");
        activateVersion("eval-match", 1);

        MvcResult result = evaluateRule("eval-match", "{\"age\": 25}");
        assertThat(result.getResponse().getStatus()).isEqualTo(200);

        JsonNode root = MAPPER.readTree(result.getResponse().getContentAsString());
        assertThat(root.get("ruleKey").asText()).isEqualTo("eval-match");
        assertThat(root.get("outcome").asText()).isEqualTo("MATCH");
        assertThat(root.get("version").asInt()).isEqualTo(1);
        assertThat(root.get("auditId").asText()).isNotBlank();
    }

    // ── Test 8: Evaluate active rule — NO_MATCH ─────────────────

    @Test
    @DisplayName("Evaluating active rule with non-matching facts returns NO_MATCH")
    void evaluateNoMatchReturnsNoMatch() throws Exception {
        createRule("eval-nomatch", "Eval NoMatch");
        createVersion("eval-nomatch", "facts.age >= 18");
        activateVersion("eval-nomatch", 1);

        MvcResult result = evaluateRule("eval-nomatch", "{\"age\": 10}");
        assertThat(result.getResponse().getStatus()).isEqualTo(200);

        JsonNode root = MAPPER.readTree(result.getResponse().getContentAsString());
        assertThat(root.get("outcome").asText()).isEqualTo("NO_MATCH");
    }

    // ── Test 9: Audit row persisted ─────────────────────────────

    @Test
    @DisplayName("Evaluation persists audit row with input hash")
    void evaluationPersistsAuditRow() throws Exception {
        createRule("eval-audit", "Eval Audit");
        createVersion("eval-audit", "facts.score > 50");
        activateVersion("eval-audit", 1);

        long auditCountBefore = auditRepository.count();
        evaluateRule("eval-audit", "{\"score\": 75}");

        assertThat(auditRepository.count()).isGreaterThan(auditCountBefore);

        var audits = auditRepository.findByRuleKeyAndTenantId("eval-audit", TENANT_ID);
        assertThat(audits).isNotEmpty();
        var audit = audits.get(0);
        assertThat(audit.getInputHash()).isNotBlank();
        assertThat(audit.getInputHash()).hasSize(64); // SHA-256 hex
    }

    // ── Test 10: Deactivation prevents evaluation ───────────────

    @Test
    @DisplayName("Deactivated rule returns 422 on evaluate")
    void deactivatedRuleReturns422() throws Exception {
        createRule("eval-deact", "Deactivate Eval");
        createVersion("eval-deact", "facts.age >= 18");
        activateVersion("eval-deact", 1);

        deactivateRule("eval-deact");

        MvcResult result = evaluateRule("eval-deact", "{\"age\": 25}");
        assertThat(result.getResponse().getStatus()).isEqualTo(422);
    }

    // ── Test 11: Version switch changes evaluation ──────────────

    @Test
    @DisplayName("Switching active version changes evaluation result")
    void versionSwitchChangesResult() throws Exception {
        createRule("eval-switch", "Switch Rule");
        createVersion("eval-switch", "facts.age >= 21");  // v1: needs 21+
        createVersion("eval-switch", "facts.age >= 10");  // v2: needs 10+
        activateVersion("eval-switch", 1);

        // Age 15 does NOT match v1 (>= 21)
        MvcResult r1 = evaluateRule("eval-switch", "{\"age\": 15}");
        JsonNode j1 = MAPPER.readTree(r1.getResponse().getContentAsString());
        assertThat(j1.get("outcome").asText()).isEqualTo("NO_MATCH");

        // Switch to v2
        activateVersion("eval-switch", 2);

        // Age 15 DOES match v2 (>= 10)
        MvcResult r2 = evaluateRule("eval-switch", "{\"age\": 15}");
        JsonNode j2 = MAPPER.readTree(r2.getResponse().getContentAsString());
        assertThat(j2.get("outcome").asText()).isEqualTo("MATCH");
    }

    // ── Test 12: Outbox events emitted ──────────────────────────

    @Test
    @DisplayName("Full lifecycle emits correct outbox event types")
    void lifecycleEmitsOutboxEvents() throws Exception {
        outboxEventRepository.deleteAll();

        createRule("evt-rule", "Event Rule");
        createVersion("evt-rule", "facts.x > 0");
        activateVersion("evt-rule", 1);
        evaluateRule("evt-rule", "{\"x\": 5}");
        deactivateRule("evt-rule");

        var events = outboxEventRepository.findAll();
        var types = events.stream().map(e -> e.getEventType()).toList();

        assertThat(types).contains(
                "impilo.rules.rule.created.v1",
                "impilo.rules.version.created.v1",
                "impilo.rules.rule.activated.v1",
                "impilo.rules.evaluated.v1",
                "impilo.rules.rule.deactivated.v1"
        );
    }

    // ── Test 13: Evaluating non-existent rule returns 404 ───────

    @Test
    @DisplayName("Evaluating non-existent rule returns 404")
    void evaluateNonExistentRuleReturns404() throws Exception {
        MvcResult result = evaluateRule("no-such-rule", "{\"x\": 1}");
        assertThat(result.getResponse().getStatus()).isEqualTo(404);
    }

    // ── Test 14: Activate non-existent version returns 404 ──────

    @Test
    @DisplayName("Activating non-existent version returns 404")
    void activateNonExistentVersionReturns404() throws Exception {
        createRule("no-ver", "No Version");
        MvcResult result = activateVersion("no-ver", 99);
        assertThat(result.getResponse().getStatus()).isEqualTo(404);
    }
}
