package zw.gov.mohcc.impilo.datagovernance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import zw.gov.mohcc.impilo.datagovernance.domain.DecisionAuditEntity;
import zw.gov.mohcc.impilo.datagovernance.domain.OutboxEventEntity;
import zw.gov.mohcc.impilo.datagovernance.repository.DecisionAuditRepository;
import zw.gov.mohcc.impilo.datagovernance.repository.OutboxEventRepository;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class GovernanceApiMockMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DecisionAuditRepository decisionAuditRepository;

    @Autowired
    private OutboxEventRepository outboxRepository;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String TENANT_ID = UUID.randomUUID().toString();

    // ── A) Missing required headers => 400 envelope ──

    @Nested
    @DisplayName("A) Missing required headers => 400 envelope")
    class MissingHeaders {

        @Test
        @DisplayName("POST /internal/v1/governance/datasets without X-Tenant-ID returns 400")
        void missingTenantIdOnDatasetCreate() throws Exception {
            MvcResult result = mockMvc.perform(post("/internal/v1/governance/datasets")
                            .header("X-Pod-ID", "national")
                            .header("X-Request-ID", "req-1")
                            .header("X-Correlation-ID", "corr-1")
                            .header("Idempotency-Key", "idem-1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"test_dataset\"}"))
                    .andExpect(status().isBadRequest())
                    .andReturn();

            assertErrorEnvelope(result, "MISSING_REQUIRED_HEADER");
        }

        @Test
        @DisplayName("POST /internal/v1/governance/grants without X-Pod-ID returns 400")
        void missingPodIdOnGrantCreate() throws Exception {
            MvcResult result = mockMvc.perform(post("/internal/v1/governance/grants")
                            .header("X-Tenant-ID", TENANT_ID)
                            .header("X-Request-ID", "req-1")
                            .header("X-Correlation-ID", "corr-1")
                            .header("Idempotency-Key", "idem-1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"dataset_id\":\"" + UUID.randomUUID() + "\",\"principal_id\":\"user-1\",\"purpose_of_use\":\"TREATMENT\"}"))
                    .andExpect(status().isBadRequest())
                    .andReturn();

            assertErrorEnvelope(result, "MISSING_REQUIRED_HEADER");
        }

        @Test
        @DisplayName("POST /internal/v1/governance/decide without any headers returns 400")
        void missingAllHeadersOnDecide() throws Exception {
            MvcResult result = mockMvc.perform(post("/internal/v1/governance/decide")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"principal_id\":\"user-1\",\"dataset\":\"test\",\"purpose_of_use\":\"TREATMENT\"}"))
                    .andExpect(status().isBadRequest())
                    .andReturn();

            assertErrorEnvelope(result, "MISSING_REQUIRED_HEADER");
        }

        @Test
        @DisplayName("GET /external/v1/governance/datasets without headers returns 400")
        void missingHeadersOnExternalGet() throws Exception {
            MvcResult result = mockMvc.perform(get("/external/v1/governance/datasets"))
                    .andExpect(status().isBadRequest())
                    .andReturn();

            assertErrorEnvelope(result, "MISSING_REQUIRED_HEADER");
        }
    }

    // ── B) Missing Idempotency-Key on dataset/grant create => 400 ──

    @Nested
    @DisplayName("B) Missing Idempotency-Key on writes => 400 IDEMPOTENCY_KEY_REQUIRED")
    class MissingIdempotencyKey {

        @Test
        @DisplayName("POST /internal/v1/governance/datasets without Idempotency-Key returns 400")
        void missingIdempotencyKeyOnDatasetCreate() throws Exception {
            MvcResult result = mockMvc.perform(post("/internal/v1/governance/datasets")
                            .header("X-Tenant-ID", TENANT_ID)
                            .header("X-Pod-ID", "national")
                            .header("X-Request-ID", "req-1")
                            .header("X-Correlation-ID", UUID.randomUUID().toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"test_dataset\"}"))
                    .andExpect(status().isBadRequest())
                    .andReturn();

            assertErrorEnvelope(result, "IDEMPOTENCY_KEY_REQUIRED");
        }

        @Test
        @DisplayName("POST /internal/v1/governance/grants without Idempotency-Key returns 400")
        void missingIdempotencyKeyOnGrantCreate() throws Exception {
            // First create a dataset to get a valid dataset_id
            UUID datasetId = createTestDataset("idem-test-ds-" + System.nanoTime());

            MvcResult result = mockMvc.perform(post("/internal/v1/governance/grants")
                            .header("X-Tenant-ID", TENANT_ID)
                            .header("X-Pod-ID", "national")
                            .header("X-Request-ID", "req-1")
                            .header("X-Correlation-ID", UUID.randomUUID().toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"dataset_id\":\"" + datasetId + "\",\"principal_id\":\"user-1\",\"purpose_of_use\":\"TREATMENT\"}"))
                    .andExpect(status().isBadRequest())
                    .andReturn();

            assertErrorEnvelope(result, "IDEMPOTENCY_KEY_REQUIRED");
        }
    }

    // ── C) Idempotency replay/conflict on grants ──

    @Nested
    @DisplayName("C) Idempotency replay on grants => same response; conflict => 409")
    class IdempotencyReplayAndConflict {

        @Test
        @DisplayName("POST grant with same Idempotency-Key replays original response")
        void grantIdempotentReplay() throws Exception {
            UUID datasetId = createTestDataset("replay-ds-" + System.nanoTime());
            String idempotencyKey = "grant-replay-" + System.nanoTime();
            String grantBody = "{\"dataset_id\":\"" + datasetId + "\"," +
                    "\"principal_id\":\"replay-principal\"," +
                    "\"purpose_of_use\":\"TREATMENT\"}";

            // First request — creates
            MvcResult first = mockMvc.perform(post("/internal/v1/governance/grants")
                            .header("X-Tenant-ID", TENANT_ID)
                            .header("X-Pod-ID", "national")
                            .header("X-Request-ID", "req-1")
                            .header("X-Correlation-ID", UUID.randomUUID().toString())
                            .header("Idempotency-Key", idempotencyKey)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(grantBody))
                    .andExpect(status().isCreated())
                    .andReturn();

            String firstBody = first.getResponse().getContentAsString();

            // Second request — same key, same body => replay
            MvcResult second = mockMvc.perform(post("/internal/v1/governance/grants")
                            .header("X-Tenant-ID", TENANT_ID)
                            .header("X-Pod-ID", "national")
                            .header("X-Request-ID", "req-2")
                            .header("X-Correlation-ID", UUID.randomUUID().toString())
                            .header("Idempotency-Key", idempotencyKey)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(grantBody))
                    .andReturn();

            assertThat(second.getResponse().getStatus()).isEqualTo(201);
            assertThat(second.getResponse().getContentAsString()).isEqualTo(firstBody);

            // grant_id should be the same
            JsonNode firstJson = MAPPER.readTree(firstBody);
            JsonNode secondJson = MAPPER.readTree(second.getResponse().getContentAsString());
            assertThat(secondJson.get("grant_id").asText())
                    .isEqualTo(firstJson.get("grant_id").asText());
        }

        @Test
        @DisplayName("POST grant with same Idempotency-Key but different body => 409")
        void grantIdempotencyConflict() throws Exception {
            UUID datasetId = createTestDataset("conflict-ds-" + System.nanoTime());
            String idempotencyKey = "grant-conflict-" + System.nanoTime();

            String grantBody1 = "{\"dataset_id\":\"" + datasetId + "\"," +
                    "\"principal_id\":\"conflict-principal-1\"," +
                    "\"purpose_of_use\":\"TREATMENT\"}";

            String grantBody2 = "{\"dataset_id\":\"" + datasetId + "\"," +
                    "\"principal_id\":\"conflict-principal-2\"," +
                    "\"purpose_of_use\":\"RESEARCH\"}";

            // First request
            mockMvc.perform(post("/internal/v1/governance/grants")
                            .header("X-Tenant-ID", TENANT_ID)
                            .header("X-Pod-ID", "national")
                            .header("X-Request-ID", "req-1")
                            .header("X-Correlation-ID", UUID.randomUUID().toString())
                            .header("Idempotency-Key", idempotencyKey)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(grantBody1))
                    .andExpect(status().isCreated());

            // Second request — same key, DIFFERENT body => 409
            mockMvc.perform(post("/internal/v1/governance/grants")
                            .header("X-Tenant-ID", TENANT_ID)
                            .header("X-Pod-ID", "national")
                            .header("X-Request-ID", "req-2")
                            .header("X-Correlation-ID", UUID.randomUUID().toString())
                            .header("Idempotency-Key", idempotencyKey)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(grantBody2))
                    .andExpect(status().isConflict());
        }
    }

    // ── D) Decide endpoint: ALLOW after grant, DENY without grant ──

    @Nested
    @DisplayName("D) Decide returns ALLOW after grant, DENY without grant")
    class DecideEndpoint {

        @Test
        @DisplayName("Decide returns DENY when no grant exists")
        void decideDenyWithoutGrant() throws Exception {
            // Create a dataset but NO grant
            String datasetName = "deny-test-ds-" + System.nanoTime();
            createTestDatasetByName(datasetName);

            String decideBody = "{\"principal_id\":\"user-no-grant\"," +
                    "\"dataset\":\"" + datasetName + "\"," +
                    "\"purpose_of_use\":\"TREATMENT\"," +
                    "\"query_fingerprint\":\"sha256-test\"}";

            mockMvc.perform(post("/internal/v1/governance/decide")
                            .header("X-Tenant-ID", TENANT_ID)
                            .header("X-Pod-ID", "national")
                            .header("X-Request-ID", "req-decide-1")
                            .header("X-Correlation-ID", UUID.randomUUID().toString())
                            .header("Idempotency-Key", "decide-deny-" + System.nanoTime())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(decideBody))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.decision").value("DENY"))
                    .andExpect(jsonPath("$.policy_version").exists())
                    .andExpect(jsonPath("$.reason_codes").isArray())
                    .andExpect(jsonPath("$.reason_codes[0]").value("NO_ACTIVE_GRANT"))
                    .andExpect(jsonPath("$.ttl_seconds").value(60));
        }

        @Test
        @DisplayName("Decide returns ALLOW after active grant is created")
        void decideAllowWithGrant() throws Exception {
            // Create dataset
            String datasetName = "allow-test-ds-" + System.nanoTime();
            UUID datasetId = createTestDatasetByName(datasetName);

            // Create grant
            String principalId = "user-with-grant-" + System.nanoTime();
            String grantBody = "{\"dataset_id\":\"" + datasetId + "\"," +
                    "\"principal_id\":\"" + principalId + "\"," +
                    "\"purpose_of_use\":\"TREATMENT\"}";

            mockMvc.perform(post("/internal/v1/governance/grants")
                            .header("X-Tenant-ID", TENANT_ID)
                            .header("X-Pod-ID", "national")
                            .header("X-Request-ID", "req-grant-allow")
                            .header("X-Correlation-ID", UUID.randomUUID().toString())
                            .header("Idempotency-Key", "grant-allow-" + System.nanoTime())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(grantBody))
                    .andExpect(status().isCreated());

            // Decide — should be ALLOW
            String decideBody = "{\"principal_id\":\"" + principalId + "\"," +
                    "\"dataset\":\"" + datasetName + "\"," +
                    "\"purpose_of_use\":\"TREATMENT\"," +
                    "\"query_fingerprint\":\"sha256-allow\"}";

            mockMvc.perform(post("/internal/v1/governance/decide")
                            .header("X-Tenant-ID", TENANT_ID)
                            .header("X-Pod-ID", "national")
                            .header("X-Request-ID", "req-decide-allow")
                            .header("X-Correlation-ID", UUID.randomUUID().toString())
                            .header("Idempotency-Key", "decide-allow-" + System.nanoTime())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(decideBody))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.decision").value("ALLOW"))
                    .andExpect(jsonPath("$.reason_codes[0]").value("GRANT_ACTIVE"))
                    .andExpect(jsonPath("$.ttl_seconds").value(60));
        }

        @Test
        @DisplayName("Decide returns DENY for unknown dataset")
        void decideDenyUnknownDataset() throws Exception {
            String decideBody = "{\"principal_id\":\"user-1\"," +
                    "\"dataset\":\"nonexistent_dataset_" + System.nanoTime() + "\"," +
                    "\"purpose_of_use\":\"TREATMENT\"}";

            mockMvc.perform(post("/internal/v1/governance/decide")
                            .header("X-Tenant-ID", TENANT_ID)
                            .header("X-Pod-ID", "national")
                            .header("X-Request-ID", "req-decide-unknown")
                            .header("X-Correlation-ID", UUID.randomUUID().toString())
                            .header("Idempotency-Key", "decide-unknown-" + System.nanoTime())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(decideBody))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.decision").value("DENY"))
                    .andExpect(jsonPath("$.reason_codes[0]").value("DATASET_NOT_FOUND"));
        }

        @Test
        @DisplayName("Decide returns DENY for wrong purpose even with grant")
        void decideDenyWrongPurpose() throws Exception {
            String datasetName = "purpose-test-ds-" + System.nanoTime();
            UUID datasetId = createTestDatasetByName(datasetName);
            String principalId = "user-wrong-purpose-" + System.nanoTime();

            // Grant for TREATMENT
            mockMvc.perform(post("/internal/v1/governance/grants")
                            .header("X-Tenant-ID", TENANT_ID)
                            .header("X-Pod-ID", "national")
                            .header("X-Request-ID", "req-grant-purpose")
                            .header("X-Correlation-ID", UUID.randomUUID().toString())
                            .header("Idempotency-Key", "grant-purpose-" + System.nanoTime())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"dataset_id\":\"" + datasetId + "\",\"principal_id\":\"" + principalId + "\",\"purpose_of_use\":\"TREATMENT\"}"))
                    .andExpect(status().isCreated());

            // Decide for RESEARCH — should DENY
            String decideBody = "{\"principal_id\":\"" + principalId + "\"," +
                    "\"dataset\":\"" + datasetName + "\"," +
                    "\"purpose_of_use\":\"RESEARCH\"}";

            mockMvc.perform(post("/internal/v1/governance/decide")
                            .header("X-Tenant-ID", TENANT_ID)
                            .header("X-Pod-ID", "national")
                            .header("X-Request-ID", "req-decide-purpose")
                            .header("X-Correlation-ID", UUID.randomUUID().toString())
                            .header("Idempotency-Key", "decide-purpose-" + System.nanoTime())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(decideBody))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.decision").value("DENY"))
                    .andExpect(jsonPath("$.reason_codes[0]").value("NO_ACTIVE_GRANT"));
        }
    }

    // ── E) Decision audit row + outbox for every decide ──

    @Nested
    @DisplayName("E) Decision audit row and outbox event for every decide call")
    class DecisionAuditAndOutbox {

        @Test
        @DisplayName("Every decide call inserts a decision_audit row with correct fields")
        void decisionAuditRowInserted() throws Exception {
            String datasetName = "audit-test-ds-" + System.nanoTime();
            createTestDatasetByName(datasetName);
            String principalId = "audit-principal-" + System.nanoTime();
            String correlationId = UUID.randomUUID().toString();

            String decideBody = "{\"principal_id\":\"" + principalId + "\"," +
                    "\"dataset\":\"" + datasetName + "\"," +
                    "\"purpose_of_use\":\"ADMIN\"," +
                    "\"query_fingerprint\":\"sha256-audit\"}";

            mockMvc.perform(post("/internal/v1/governance/decide")
                            .header("X-Tenant-ID", TENANT_ID)
                            .header("X-Pod-ID", "national")
                            .header("X-Request-ID", "req-audit-1")
                            .header("X-Correlation-ID", correlationId)
                            .header("Idempotency-Key", "decide-audit-" + System.nanoTime())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(decideBody))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.decision").value("DENY"));

            // Verify audit row
            List<DecisionAuditEntity> auditRows = decisionAuditRepository
                    .findByPrincipalIdAndDatasetName(principalId, datasetName);
            assertThat(auditRows).hasSize(1);

            DecisionAuditEntity audit = auditRows.get(0);
            assertThat(audit.getDecisionId()).isNotNull();
            assertThat(audit.getTenantId()).isEqualTo(UUID.fromString(TENANT_ID));
            assertThat(audit.getDatasetName()).isEqualTo(datasetName);
            assertThat(audit.getPrincipalId()).isEqualTo(principalId);
            assertThat(audit.getDecision()).isEqualTo("DENY");
            assertThat(audit.getPolicyVersion()).isNotBlank();
            assertThat(audit.getQueryFingerprint()).isEqualTo("sha256-audit");
            assertThat(audit.getCorrelationId()).isEqualTo(correlationId);
            assertThat(audit.getDecidedAt()).isNotNull();

            // Verify reason_codes_json
            JsonNode reasonCodes = MAPPER.readTree(audit.getReasonCodesJson());
            assertThat(reasonCodes.isArray()).isTrue();
            assertThat(reasonCodes.size()).isGreaterThanOrEqualTo(1);
        }

        @Test
        @DisplayName("Decision produces outbox row with correct v1.1 fields and partition_key=principal_id")
        void decisionOutboxRow() throws Exception {
            String datasetName = "outbox-test-ds-" + System.nanoTime();
            createTestDatasetByName(datasetName);
            String principalId = "outbox-principal-" + System.nanoTime();
            String correlationId = UUID.randomUUID().toString();

            String decideBody = "{\"principal_id\":\"" + principalId + "\"," +
                    "\"dataset\":\"" + datasetName + "\"," +
                    "\"purpose_of_use\":\"PUBLIC_HEALTH\"}";

            mockMvc.perform(post("/internal/v1/governance/decide")
                            .header("X-Tenant-ID", TENANT_ID)
                            .header("X-Pod-ID", "national")
                            .header("X-Request-ID", "req-outbox-1")
                            .header("X-Correlation-ID", correlationId)
                            .header("Idempotency-Key", "decide-outbox-" + System.nanoTime())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(decideBody))
                    .andExpect(status().isOk());

            // Get the decision audit to find the outbox aggregate_id
            List<DecisionAuditEntity> auditRows = decisionAuditRepository
                    .findByPrincipalIdAndDatasetName(principalId, datasetName);
            assertThat(auditRows).hasSize(1);
            String decisionId = auditRows.get(0).getDecisionId().toString();

            // Verify outbox row
            List<OutboxEventEntity> outboxRows = outboxRepository
                    .findByAggregateIdAndEventType(decisionId,
                            "impilo.data.governance.decision.logged.v1");
            assertThat(outboxRows).hasSize(1);

            OutboxEventEntity outbox = outboxRows.get(0);
            assertThat(outbox.getEventId()).isNotNull();
            assertThat(outbox.getAggregateType()).isEqualTo("DecisionAudit");
            assertThat(outbox.getEventType()).isEqualTo("impilo.data.governance.decision.logged.v1");
            int schemaVersion = Integer.parseInt(outbox.getSchemaVersion());
            assertThat(schemaVersion).isGreaterThanOrEqualTo(1);
            assertThat(outbox.getCorrelationId()).isNotNull();
            assertThat(outbox.getCorrelationId().toString()).isEqualTo(correlationId);
            assertThat(outbox.getProducer()).isEqualTo("data-governance-service");
            assertThat(outbox.getTenantId()).isEqualTo(UUID.fromString(TENANT_ID));
            assertThat(outbox.getOccurredAt()).isNotNull();

            // partition_key MUST be principal_id
            assertThat(outbox.getPartitionKey()).isEqualTo(principalId);

            // Payload contains decision details
            JsonNode payload = MAPPER.readTree(outbox.getPayloadJson());
            assertThat(payload.get("decision_id").asText()).isEqualTo(decisionId);
            assertThat(payload.get("principal_id").asText()).isEqualTo(principalId);
            assertThat(payload.get("dataset").asText()).isEqualTo(datasetName);
            assertThat(payload.get("decision").asText()).isEqualTo("DENY");
            assertThat(payload.get("reason_codes").isArray()).isTrue();
            assertThat(payload.get("policy_version").asText()).isNotBlank();
        }

        @Test
        @DisplayName("Dataset creation produces outbox row with dataset.created event")
        void datasetCreationOutbox() throws Exception {
            String dsName = "outbox-ds-" + System.nanoTime();
            String correlationId = UUID.randomUUID().toString();
            String idempotencyKey = "ds-outbox-" + System.nanoTime();

            MvcResult result = mockMvc.perform(post("/internal/v1/governance/datasets")
                            .header("X-Tenant-ID", TENANT_ID)
                            .header("X-Pod-ID", "national")
                            .header("X-Request-ID", "req-ds-outbox")
                            .header("X-Correlation-ID", correlationId)
                            .header("Idempotency-Key", idempotencyKey)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"" + dsName + "\",\"classification\":\"CONFIDENTIAL\"}"))
                    .andExpect(status().isCreated())
                    .andReturn();

            JsonNode body = MAPPER.readTree(result.getResponse().getContentAsString());
            String datasetId = body.get("dataset_id").asText();

            List<OutboxEventEntity> outboxRows = outboxRepository
                    .findByAggregateIdAndEventType(datasetId,
                            "impilo.data.governance.dataset.created.v1");
            assertThat(outboxRows).hasSize(1);

            OutboxEventEntity outbox = outboxRows.get(0);
            assertThat(outbox.getAggregateType()).isEqualTo("Dataset");
            assertThat(outbox.getIdempotencyKey()).isEqualTo(idempotencyKey);
            assertThat(outbox.getProducer()).isEqualTo("data-governance-service");
            assertThat(outbox.getPartitionKey()).isEqualTo(datasetId);

            JsonNode payload = MAPPER.readTree(outbox.getPayloadJson());
            assertThat(payload.get("op").asText()).isEqualTo("CREATE");
            assertThat(payload.get("after")).isNotNull();
            assertThat(payload.get("changed_fields").isArray()).isTrue();
        }
    }

    // ── External endpoint ──

    @Nested
    @DisplayName("External dataset listing")
    class ExternalEndpoint {

        @Test
        @DisplayName("GET /external/v1/governance/datasets returns policy-filtered list")
        void externalListingReturnsPolicyFiltered() throws Exception {
            // Create a dataset first
            createTestDatasetByName("external-test-ds-" + System.nanoTime());

            mockMvc.perform(get("/external/v1/governance/datasets")
                            .header("X-Tenant-ID", TENANT_ID)
                            .header("X-Pod-ID", "national")
                            .header("X-Request-ID", "req-ext-1")
                            .header("X-Correlation-ID", UUID.randomUUID().toString()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray());
        }
    }

    // ── Export endpoint tests ──

    @Nested
    @DisplayName("Export endpoint validation")
    class ExportEndpoint {

        @Test
        @DisplayName("POST /external/v1/exports without purpose_of_use returns 400")
        void exportWithoutPurposeReturns400() throws Exception {
            mockMvc.perform(post("/external/v1/exports")
                            .header("X-Tenant-ID", TENANT_ID)
                            .header("X-Pod-ID", "national")
                            .header("X-Request-ID", "req-exp-1")
                            .header("X-Correlation-ID", "corr-exp-1")
                            .header("Idempotency-Key", "idem-exp-1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"dataset\": \"encounters\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("MISSING_PURPOSE_OF_USE"));
        }

        @Test
        @DisplayName("POST /external/v1/exports denies by default without allow rule")
        void exportDeniedByDefault() throws Exception {
            // Use a dataset name that will not match rules created in other nested tests in the same JVM
            // (e.g. allow-research-encounters matches resourcePattern "encounters").
            String dataset = "export-deny-isolated-" + System.nanoTime();
            mockMvc.perform(post("/external/v1/exports")
                            .header("X-Tenant-ID", TENANT_ID)
                            .header("X-Pod-ID", "national")
                            .header("X-Request-ID", "req-exp-2")
                            .header("X-Correlation-ID", "corr-exp-2")
                            .header("Idempotency-Key", "idem-exp-2")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"dataset\": \"" + dataset + "\", \"purposeOfUse\": \"RESEARCH\"}"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.decision").value("DENY"))
                    .andExpect(jsonPath("$.dataset").value(dataset));
        }
    }

    // ── Governance rules tests ──

    @Nested
    @DisplayName("Governance rules CRUD")
    class GovernanceRules {

        @Test
        @DisplayName("GET /internal/v1/governance/rules returns list")
        void listRulesReturnsOk() throws Exception {
            mockMvc.perform(get("/internal/v1/governance/rules")
                            .header("X-Tenant-ID", TENANT_ID)
                            .header("X-Pod-ID", "national")
                            .header("X-Request-ID", "req-rules-1")
                            .header("X-Correlation-ID", "corr-rules-1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray());
        }

        @Test
        @DisplayName("POST /internal/v1/governance/rules creates a rule")
        void createRuleReturnsCreated() throws Exception {
            String body = """
                    {"name": "allow-research-encounters", "resourcePattern": "encounters",
                     "action": "EXPORT", "effect": "ALLOW", "requiredPurpose": "RESEARCH"}
                    """;

            mockMvc.perform(post("/internal/v1/governance/rules")
                            .header("X-Tenant-ID", TENANT_ID)
                            .header("X-Pod-ID", "national")
                            .header("X-Request-ID", "req-rules-2")
                            .header("X-Correlation-ID", "corr-rules-2")
                            .header("Idempotency-Key", "idem-rule-1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.name").value("allow-research-encounters"))
                    .andExpect(jsonPath("$.effect").value("ALLOW"));
        }
    }

    // ── Helpers ──

    private UUID createTestDataset(String idempotencyKey) throws Exception {
        String dsName = "test-ds-" + System.nanoTime();
        return createTestDatasetByName(dsName, idempotencyKey);
    }

    private UUID createTestDatasetByName(String name) throws Exception {
        return createTestDatasetByName(name, "ds-create-" + System.nanoTime());
    }

    private UUID createTestDatasetByName(String name, String idempotencyKey) throws Exception {
        MvcResult result = mockMvc.perform(post("/internal/v1/governance/datasets")
                        .header("X-Tenant-ID", TENANT_ID)
                        .header("X-Pod-ID", "national")
                        .header("X-Request-ID", "req-ds-create")
                        .header("X-Correlation-ID", UUID.randomUUID().toString())
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\",\"classification\":\"INTERNAL\",\"description\":\"Test dataset\"}"))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode body = MAPPER.readTree(result.getResponse().getContentAsString());
        return UUID.fromString(body.get("dataset_id").asText());
    }

    private void assertErrorEnvelope(MvcResult result, String expectedCode) throws Exception {
        String body = result.getResponse().getContentAsString();
        assertThat(body).isNotBlank();

        JsonNode root = MAPPER.readTree(body);
        assertThat(root.has("error")).as("Response should contain 'error' field: " + body).isTrue();

        JsonNode error = root.get("error");
        assertThat(error.get("code").asText()).isEqualTo(expectedCode);
        assertThat(error.has("message")).isTrue();
        assertThat(error.has("request_id")).isTrue();
        assertThat(error.has("correlation_id")).isTrue();
    }
}
