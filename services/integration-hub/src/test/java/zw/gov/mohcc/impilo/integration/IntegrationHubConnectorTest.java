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
import zw.gov.mohcc.impilo.integration.repository.DeadLetterRepository;
import zw.gov.mohcc.impilo.integration.repository.DispatchAttemptRepository;
import zw.gov.mohcc.impilo.integration.repository.MappingTemplateRepository;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for Integration Hub connector types, mapping templates,
 * and dead letter replay.
 *
 * Uses H2 in-memory database via the "test" profile with create-drop DDL
 * and Flyway disabled.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestSecurityConfig.class)
class IntegrationHubConnectorTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DeadLetterRepository deadLetterRepository;

    @Autowired
    private DispatchAttemptRepository dispatchAttemptRepository;

    @Autowired
    private MappingTemplateRepository mappingTemplateRepository;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String TENANT_ID = "moh-zw";
    private static final String POD_ID = "national";

    // ── Helpers ──────────────────────────────────────────────────────────

    private String createRouteAndGetId(String matchMethod, String matchPathRegex,
                                        String targetUrl, String connectorType,
                                        String mappingTemplateId) throws Exception {
        String connectorField = connectorType != null ? "\"connectorType\": \"" + connectorType + "\"," : "";
        String mappingField = mappingTemplateId != null ? "\"mappingTemplateId\": \"" + mappingTemplateId + "\"," : "";

        String body = """
                {
                  "matchMethod": "%s",
                  "matchPathRegex": "%s",
                  "targetUrl": "%s",
                  "targetTimeoutMs": 5000,
                  %s
                  %s
                  "enabled": true
                }
                """.formatted(matchMethod, matchPathRegex, targetUrl, connectorField, mappingField);

        MvcResult result = mockMvc.perform(post("/internal/v1/routes")
                        .header("X-Tenant-ID", TENANT_ID)
                        .header("X-Pod-ID", POD_ID)
                        .header("X-Request-ID", UUID.randomUUID().toString())
                        .header("X-Correlation-ID", UUID.randomUUID().toString())
                        .header("Idempotency-Key", "route-conn-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode root = MAPPER.readTree(result.getResponse().getContentAsString());
        return root.get("id").asText();
    }

    private String dispatchBody(String method, String path) {
        return """
                {
                  "method": "%s",
                  "path": "%s",
                  "body": null
                }
                """.formatted(method, path);
    }

    // ── Test 1: Create route with connector_type=HTTP ────────────────────

    @Test
    @DisplayName("POST /internal/v1/routes with connectorType=HTTP returns 201 with connectorType field")
    void createRouteWithHttpConnector() throws Exception {
        String body = """
                {
                  "matchMethod": "POST",
                  "matchPathRegex": "/api/http-conn/.*",
                  "targetUrl": "http://oros:8089/internal/v1/claims",
                  "targetTimeoutMs": 3000,
                  "connectorType": "HTTP",
                  "enabled": true
                }
                """;

        MvcResult result = mockMvc.perform(post("/internal/v1/routes")
                        .header("X-Tenant-ID", TENANT_ID)
                        .header("X-Pod-ID", POD_ID)
                        .header("X-Request-ID", UUID.randomUUID().toString())
                        .header("X-Correlation-ID", UUID.randomUUID().toString())
                        .header("Idempotency-Key", "conn-http-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode root = MAPPER.readTree(result.getResponse().getContentAsString());

        assertThat(root.get("id").asText()).isNotBlank();
        assertThat(root.get("connectorType").asText()).isEqualTo("HTTP");
        assertThat(root.get("matchMethod").asText()).isEqualTo("POST");
        assertThat(root.get("targetUrl").asText()).isEqualTo("http://oros:8089/internal/v1/claims");
    }

    // ── Test 2: Create route with connector_type=FILE_DROP, dispatch to it ─

    @Test
    @DisplayName("Route with connectorType=FILE_DROP accepts dispatch and records attempt")
    void createFileDropRouteAndDispatch() throws Exception {
        String routeId = createRouteAndGetId("POST", "/api/file-drop/.*",
                "file:///data/exports/claims", "FILE_DROP", null);

        String body = """
                {
                  "method": "POST",
                  "path": "/api/file-drop/claim-batch",
                  "body": "{\\"batchId\\": \\"B-001\\", \\"records\\": 42}"
                }
                """;

        MvcResult result = mockMvc.perform(post("/internal/v1/dispatch")
                        .header("X-Tenant-ID", TENANT_ID)
                        .header("X-Pod-ID", POD_ID)
                        .header("X-Request-ID", UUID.randomUUID().toString())
                        .header("X-Correlation-ID", UUID.randomUUID().toString())
                        .header("Idempotency-Key", "dispatch-fd-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isAccepted())
                .andReturn();

        JsonNode root = MAPPER.readTree(result.getResponse().getContentAsString());
        assertThat(root.get("status").asText()).isEqualTo("ACCEPTED");
        assertThat(root.get("routeId").asText()).isEqualTo(routeId);
        assertThat(root.get("targetUrl").asText()).isEqualTo("file:///data/exports/claims");

        // Verify dispatch attempt was recorded
        String dispatchId = root.get("id").asText();
        assertThat(dispatchAttemptRepository.findById(dispatchId)).isPresent();
    }

    // ── Test 3: Create mapping template via POST /internal/v1/mapping-templates ─

    @Test
    @DisplayName("POST /internal/v1/mapping-templates creates template and returns 201")
    void createMappingTemplate() throws Exception {
        String body = """
                {
                  "name": "HL7v2-to-FHIR",
                  "sourceFormat": "HL7v2",
                  "targetFormat": "FHIR_R4",
                  "mappingRulesJson": "{\\"PID.5\\": \\"Patient.name\\", \\"PID.7\\": \\"Patient.birthDate\\"}",
                  "active": true
                }
                """;

        MvcResult result = mockMvc.perform(post("/internal/v1/mapping-templates")
                        .header("X-Tenant-ID", TENANT_ID)
                        .header("X-Pod-ID", POD_ID)
                        .header("X-Request-ID", UUID.randomUUID().toString())
                        .header("X-Correlation-ID", UUID.randomUUID().toString())
                        .header("Idempotency-Key", "mt-create-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode root = MAPPER.readTree(result.getResponse().getContentAsString());

        assertThat(root.get("id").asText()).isNotBlank();
        assertThat(root.get("name").asText()).isEqualTo("HL7v2-to-FHIR");
        assertThat(root.get("sourceFormat").asText()).isEqualTo("HL7v2");
        assertThat(root.get("targetFormat").asText()).isEqualTo("FHIR_R4");
        assertThat(root.get("mappingRulesJson").asText()).contains("PID.5");
        assertThat(root.get("active").asBoolean()).isTrue();
        assertThat(root.get("tenantId").asText()).isEqualTo(TENANT_ID);
        assertThat(root.get("version").asInt()).isGreaterThanOrEqualTo(1);
    }

    // ── Test 4: List mapping templates ───────────────────────────────────

    @Test
    @DisplayName("GET /internal/v1/mapping-templates returns all templates for tenant")
    void listMappingTemplates() throws Exception {
        // Create two templates
        for (String name : List.of("CSV-to-FHIR", "XML-to-HL7")) {
            String body = """
                    {
                      "name": "%s",
                      "sourceFormat": "CSV",
                      "targetFormat": "FHIR_R4",
                      "active": true
                    }
                    """.formatted(name);

            mockMvc.perform(post("/internal/v1/mapping-templates")
                            .header("X-Tenant-ID", TENANT_ID)
                            .header("X-Pod-ID", POD_ID)
                            .header("X-Request-ID", UUID.randomUUID().toString())
                            .header("X-Correlation-ID", UUID.randomUUID().toString())
                            .header("Idempotency-Key", "mt-list-" + UUID.randomUUID())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isCreated());
        }

        MvcResult result = mockMvc.perform(get("/internal/v1/mapping-templates")
                        .header("X-Tenant-ID", TENANT_ID)
                        .header("X-Pod-ID", POD_ID)
                        .header("X-Request-ID", UUID.randomUUID().toString())
                        .header("X-Correlation-ID", UUID.randomUUID().toString()))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode root = MAPPER.readTree(result.getResponse().getContentAsString());
        assertThat(root.isArray()).isTrue();
        assertThat(root.size()).isGreaterThanOrEqualTo(2);

        // Verify both names are present
        boolean foundCsv = false;
        boolean foundXml = false;
        for (JsonNode node : root) {
            if ("CSV-to-FHIR".equals(node.get("name").asText())) foundCsv = true;
            if ("XML-to-HL7".equals(node.get("name").asText())) foundXml = true;
        }
        assertThat(foundCsv).as("Should contain CSV-to-FHIR template").isTrue();
        assertThat(foundXml).as("Should contain XML-to-HL7 template").isTrue();
    }

    // ── Test 5: Dead letter replay ───────────────────────────────────────

    @Test
    @DisplayName("POST /internal/v1/deadletters/{id}/replay re-dispatches and resolves on match")
    void deadLetterReplay() throws Exception {
        // Step 1: Dispatch an unmatched request to create a dead letter
        String unmatchedBody = dispatchBody("PUT", "/api/replay-test/unmatched-path");

        mockMvc.perform(post("/internal/v1/dispatch")
                        .header("X-Tenant-ID", TENANT_ID)
                        .header("X-Pod-ID", POD_ID)
                        .header("X-Request-ID", UUID.randomUUID().toString())
                        .header("X-Correlation-ID", UUID.randomUUID().toString())
                        .header("Idempotency-Key", "dl-replay-create-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(unmatchedBody))
                .andExpect(status().isAccepted());

        // Step 2: Find the dead letter
        List<DeadLetterEntity> deadLetters = deadLetterRepository.findAll();
        DeadLetterEntity deadLetter = deadLetters.stream()
                .filter(dl -> "/api/replay-test/unmatched-path".equals(dl.getPath()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Dead letter not found for /api/replay-test/unmatched-path"));

        assertThat(deadLetter.isResolved()).isFalse();
        assertThat(deadLetter.getRetryCount()).isEqualTo(0);

        // Step 3: Create a matching route so the replay succeeds
        createRouteAndGetId("PUT", "/api/replay-test/.*",
                "http://target:8080/replay-handler", null, null);

        // Step 4: Replay the dead letter
        MvcResult replayResult = mockMvc.perform(post("/internal/v1/deadletters/" + deadLetter.getId() + "/replay")
                        .header("X-Tenant-ID", TENANT_ID)
                        .header("X-Pod-ID", POD_ID)
                        .header("X-Request-ID", UUID.randomUUID().toString())
                        .header("X-Correlation-ID", UUID.randomUUID().toString()))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode replayRoot = MAPPER.readTree(replayResult.getResponse().getContentAsString());
        assertThat(replayRoot.get("status").asText()).isEqualTo("ACCEPTED");
        assertThat(replayRoot.get("targetUrl").asText()).isEqualTo("http://target:8080/replay-handler");

        // Step 5: Verify the dead letter is now resolved with incremented retry count
        DeadLetterEntity updatedDl = deadLetterRepository.findById(deadLetter.getId()).orElseThrow();
        assertThat(updatedDl.isResolved()).isTrue();
        assertThat(updatedDl.getRetryCount()).isEqualTo(1);
        assertThat(updatedDl.getLastRetryAt()).isNotNull();
    }

    // ── Test 6: Dead letter replay with no matching route still fails ────

    @Test
    @DisplayName("POST /internal/v1/deadletters/{id}/replay without matching route stays unresolved")
    void deadLetterReplayStillFails() throws Exception {
        // Dispatch unmatched to create dead letter
        String body = dispatchBody("DELETE", "/api/still-unmatched/resource");

        mockMvc.perform(post("/internal/v1/dispatch")
                        .header("X-Tenant-ID", TENANT_ID)
                        .header("X-Pod-ID", POD_ID)
                        .header("X-Request-ID", UUID.randomUUID().toString())
                        .header("X-Correlation-ID", UUID.randomUUID().toString())
                        .header("Idempotency-Key", "dl-fail-replay-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isAccepted());

        DeadLetterEntity deadLetter = deadLetterRepository.findAll().stream()
                .filter(dl -> "/api/still-unmatched/resource".equals(dl.getPath()))
                .findFirst()
                .orElseThrow();

        // Replay without adding a matching route
        MvcResult result = mockMvc.perform(post("/internal/v1/deadletters/" + deadLetter.getId() + "/replay")
                        .header("X-Tenant-ID", TENANT_ID)
                        .header("X-Pod-ID", POD_ID)
                        .header("X-Request-ID", UUID.randomUUID().toString())
                        .header("X-Correlation-ID", UUID.randomUUID().toString()))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode root = MAPPER.readTree(result.getResponse().getContentAsString());
        assertThat(root.get("status").asText()).isEqualTo("FAILED");

        // Verify dead letter is NOT resolved but retry count is incremented
        DeadLetterEntity updatedDl = deadLetterRepository.findById(deadLetter.getId()).orElseThrow();
        assertThat(updatedDl.isResolved()).isFalse();
        assertThat(updatedDl.getRetryCount()).isEqualTo(1);
    }

    // ── Test 7: Route with mappingTemplateId links correctly ─────────────

    @Test
    @DisplayName("Route created with mappingTemplateId reflects in response")
    void routeWithMappingTemplateId() throws Exception {
        // Create a mapping template first
        String mtBody = """
                {
                  "name": "Route-Link-Template",
                  "sourceFormat": "JSON",
                  "targetFormat": "FHIR_R4",
                  "active": true
                }
                """;

        MvcResult mtResult = mockMvc.perform(post("/internal/v1/mapping-templates")
                        .header("X-Tenant-ID", TENANT_ID)
                        .header("X-Pod-ID", POD_ID)
                        .header("X-Request-ID", UUID.randomUUID().toString())
                        .header("X-Correlation-ID", UUID.randomUUID().toString())
                        .header("Idempotency-Key", "mt-link-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mtBody))
                .andExpect(status().isCreated())
                .andReturn();

        String mappingTemplateId = MAPPER.readTree(mtResult.getResponse().getContentAsString())
                .get("id").asText();

        // Create route referencing the mapping template
        String routeId = createRouteAndGetId("POST", "/api/mapped-route/.*",
                "http://target:8080/mapped", "HTTP", mappingTemplateId);

        // Verify the route has the mapping template ID
        MvcResult listResult = mockMvc.perform(get("/internal/v1/routes")
                        .header("X-Tenant-ID", TENANT_ID)
                        .header("X-Pod-ID", POD_ID)
                        .header("X-Request-ID", UUID.randomUUID().toString())
                        .header("X-Correlation-ID", UUID.randomUUID().toString()))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode routes = MAPPER.readTree(listResult.getResponse().getContentAsString());
        boolean found = false;
        for (JsonNode route : routes) {
            if (routeId.equals(route.get("id").asText())) {
                assertThat(route.get("connectorType").asText()).isEqualTo("HTTP");
                assertThat(route.get("mappingTemplateId").asText()).isEqualTo(mappingTemplateId);
                found = true;
                break;
            }
        }
        assertThat(found).as("Should find route with mappingTemplateId").isTrue();
    }
}
