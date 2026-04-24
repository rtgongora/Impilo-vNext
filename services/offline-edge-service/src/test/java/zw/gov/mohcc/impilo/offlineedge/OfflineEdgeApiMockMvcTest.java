package zw.gov.mohcc.impilo.offlineedge;

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
import zw.gov.mohcc.impilo.offlineedge.repository.CapturedActionRepository;
import zw.gov.mohcc.impilo.offlineedge.repository.OutboxEventRepository;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class OfflineEdgeApiMockMvcTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private OutboxEventRepository outboxRepository;
    @Autowired private CapturedActionRepository actionRepository;

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String TENANT_ID = UUID.randomUUID().toString();

    // ── A) Missing required headers => 400 ──

    @Nested
    @DisplayName("A) Missing required headers => 400 envelope")
    class MissingHeaders {

        @Test
        @DisplayName("POST /internal/v1/offline/entitlements without X-Tenant-ID returns 400")
        void missingTenantId() throws Exception {
            MvcResult result = mockMvc.perform(post("/internal/v1/offline/entitlements")
                            .header("X-Pod-ID", "national")
                            .header("X-Request-ID", "req-1")
                            .header("X-Correlation-ID", "corr-1")
                            .header("Idempotency-Key", "idem-1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"actorId\":\"nurse-001\",\"facilityRef\":\"FAC-001\"}"))
                    .andExpect(status().isBadRequest())
                    .andReturn();

            assertErrorEnvelope(result, "MISSING_REQUIRED_HEADER");
        }
    }

    // ── B) Entitlement issuance + token verification ──

    @Nested
    @DisplayName("B) Entitlement issuance and token verification")
    class EntitlementLifecycle {

        @Test
        @DisplayName("Issue entitlement returns 201 with tokenHash and expiresAt")
        void issueEntitlement() throws Exception {
            MvcResult result = mockMvc.perform(post("/internal/v1/offline/entitlements")
                            .header("X-Tenant-ID", TENANT_ID)
                            .header("X-Pod-ID", "national")
                            .header("X-Request-ID", UUID.randomUUID().toString())
                            .header("X-Correlation-ID", UUID.randomUUID().toString())
                            .header("Idempotency-Key", "issue-ent-" + System.nanoTime())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"actorId\":\"nurse-001\",\"facilityRef\":\"FAC-001\",\"workflowType\":\"CAPTURE_VITALS\"}"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.entitlementId").exists())
                    .andExpect(jsonPath("$.tokenHash").exists())
                    .andExpect(jsonPath("$.expiresAt").exists())
                    .andExpect(jsonPath("$.workflowType").value("CAPTURE_VITALS"))
                    .andExpect(jsonPath("$.revoked").value(false))
                    .andReturn();

            JsonNode body = MAPPER.readTree(result.getResponse().getContentAsString());
            assertThat(body.get("tokenHash").asText()).hasSize(64); // HMAC-SHA256 hex
        }

        @Test
        @DisplayName("Verify entitlement token returns valid=true for correct hash")
        void verifyEntitlementToken() throws Exception {
            // Issue entitlement
            MvcResult issueResult = mockMvc.perform(post("/internal/v1/offline/entitlements")
                            .header("X-Tenant-ID", TENANT_ID)
                            .header("X-Pod-ID", "national")
                            .header("X-Request-ID", UUID.randomUUID().toString())
                            .header("X-Correlation-ID", UUID.randomUUID().toString())
                            .header("Idempotency-Key", "verify-ent-" + System.nanoTime())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"actorId\":\"nurse-002\",\"facilityRef\":\"FAC-002\"}"))
                    .andExpect(status().isCreated())
                    .andReturn();

            JsonNode issued = MAPPER.readTree(issueResult.getResponse().getContentAsString());
            String entitlementId = issued.get("entitlementId").asText();
            String tokenHash = issued.get("tokenHash").asText();

            // Verify with correct token
            mockMvc.perform(get("/internal/v1/offline/entitlements/" + entitlementId + "/verify")
                            .header("X-Tenant-ID", TENANT_ID)
                            .header("X-Pod-ID", "national")
                            .header("X-Request-ID", UUID.randomUUID().toString())
                            .header("X-Correlation-ID", UUID.randomUUID().toString())
                            .param("token_hash", tokenHash))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.valid").value(true))
                    .andExpect(jsonPath("$.entitlement_id").value(entitlementId));
        }

        @Test
        @DisplayName("Verify entitlement token returns valid=false for wrong hash")
        void verifyEntitlementTokenInvalid() throws Exception {
            // Issue entitlement
            MvcResult issueResult = mockMvc.perform(post("/internal/v1/offline/entitlements")
                            .header("X-Tenant-ID", TENANT_ID)
                            .header("X-Pod-ID", "national")
                            .header("X-Request-ID", UUID.randomUUID().toString())
                            .header("X-Correlation-ID", UUID.randomUUID().toString())
                            .header("Idempotency-Key", "verify-bad-" + System.nanoTime())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"actorId\":\"nurse-003\",\"facilityRef\":\"FAC-003\"}"))
                    .andExpect(status().isCreated())
                    .andReturn();

            String entitlementId = MAPPER.readTree(issueResult.getResponse().getContentAsString())
                    .get("entitlementId").asText();

            // Verify with wrong token
            mockMvc.perform(get("/internal/v1/offline/entitlements/" + entitlementId + "/verify")
                            .header("X-Tenant-ID", TENANT_ID)
                            .header("X-Pod-ID", "national")
                            .header("X-Request-ID", UUID.randomUUID().toString())
                            .header("X-Correlation-ID", UUID.randomUUID().toString())
                            .param("token_hash", "wrong-hash-value"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.valid").value(false));
        }

        @Test
        @DisplayName("GET non-existent entitlement returns 404")
        void getEntitlementNotFound() throws Exception {
            mockMvc.perform(get("/internal/v1/offline/entitlements/" + UUID.randomUUID())
                            .header("X-Tenant-ID", TENANT_ID)
                            .header("X-Pod-ID", "national")
                            .header("X-Request-ID", UUID.randomUUID().toString())
                            .header("X-Correlation-ID", UUID.randomUUID().toString()))
                    .andExpect(status().isNotFound());
        }
    }

    // ── C) Capture action with entitlement validation ──

    @Nested
    @DisplayName("C) Capture action with entitlement validation")
    class ActionCapture {

        @Test
        @DisplayName("Capture action with valid entitlement returns 201 QUEUED")
        void captureActionSuccess() throws Exception {
            // Issue entitlement first
            MvcResult entResult = issueEntitlement();
            String entitlementId = MAPPER.readTree(entResult.getResponse().getContentAsString())
                    .get("entitlementId").asText();

            // Capture action
            String capturedAt = OffsetDateTime.now().toString();
            String actionBody = String.format("""
                {"entitlementId":"%s","patientRef":"PAT-001","actionType":"VITALS_CAPTURE",
                 "payload":{"systolic":120,"diastolic":80},"capturedAt":"%s",
                 "deviceId":"DEVICE-001","sequenceNum":1}""", entitlementId, capturedAt);

            mockMvc.perform(post("/internal/v1/offline/actions")
                            .header("X-Tenant-ID", TENANT_ID)
                            .header("X-Pod-ID", "national")
                            .header("X-Request-ID", UUID.randomUUID().toString())
                            .header("X-Correlation-ID", UUID.randomUUID().toString())
                            .header("Idempotency-Key", "capture-" + System.nanoTime())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(actionBody))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.actionId").exists())
                    .andExpect(jsonPath("$.status").value("QUEUED"))
                    .andExpect(jsonPath("$.patientRef").value("PAT-001"))
                    .andExpect(jsonPath("$.actionType").value("VITALS_CAPTURE"));
        }

        @Test
        @DisplayName("Capture action with non-existent entitlement returns 422")
        void captureActionInvalidEntitlement() throws Exception {
            String capturedAt = OffsetDateTime.now().toString();
            String actionBody = String.format("""
                {"entitlementId":"%s","patientRef":"PAT-002","actionType":"VITALS_CAPTURE",
                 "payload":{"temp":37.5},"capturedAt":"%s","sequenceNum":1}""",
                    UUID.randomUUID(), capturedAt);

            mockMvc.perform(post("/internal/v1/offline/actions")
                            .header("X-Tenant-ID", TENANT_ID)
                            .header("X-Pod-ID", "national")
                            .header("X-Request-ID", UUID.randomUUID().toString())
                            .header("X-Correlation-ID", UUID.randomUUID().toString())
                            .header("Idempotency-Key", "capture-bad-" + System.nanoTime())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(actionBody))
                    .andExpect(status().isUnprocessableEntity());
        }
    }

    // ── D) Replay pipeline ──

    @Nested
    @DisplayName("D) Replay pipeline processes queued actions")
    class ReplayPipeline {

        @Test
        @DisplayName("Replay converts QUEUED actions to REPLAYED with batch tracking")
        void replayQueuedActions() throws Exception {
            // Issue entitlement
            MvcResult entResult = issueEntitlement();
            String entitlementId = MAPPER.readTree(entResult.getResponse().getContentAsString())
                    .get("entitlementId").asText();

            // Capture two actions
            for (int i = 1; i <= 2; i++) {
                String capturedAt = OffsetDateTime.now().toString();
                String actionBody = String.format("""
                    {"entitlementId":"%s","patientRef":"PAT-REPLAY","actionType":"VITALS_CAPTURE",
                     "payload":{"reading":%d},"capturedAt":"%s","sequenceNum":%d}""",
                        entitlementId, i * 100, capturedAt, i);

                mockMvc.perform(post("/internal/v1/offline/actions")
                                .header("X-Tenant-ID", TENANT_ID)
                                .header("X-Pod-ID", "national")
                                .header("X-Request-ID", UUID.randomUUID().toString())
                                .header("X-Correlation-ID", UUID.randomUUID().toString())
                                .header("Idempotency-Key", "replay-capture-" + i + "-" + System.nanoTime())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(actionBody))
                        .andExpect(status().isCreated());
            }

            // Replay
            MvcResult replayResult = mockMvc.perform(post("/internal/v1/offline/replay/" + entitlementId)
                            .header("X-Tenant-ID", TENANT_ID)
                            .header("X-Pod-ID", "national")
                            .header("X-Request-ID", UUID.randomUUID().toString())
                            .header("X-Correlation-ID", UUID.randomUUID().toString())
                            .header("Idempotency-Key", "replay-batch-" + System.nanoTime()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.batch_id").exists())
                    .andExpect(jsonPath("$.total_actions").value(2))
                    .andExpect(jsonPath("$.replayed_count").value(2))
                    .andExpect(jsonPath("$.failed_count").value(0))
                    .andReturn();

            JsonNode replayBody = MAPPER.readTree(replayResult.getResponse().getContentAsString());
            assertThat(replayBody.get("status").asText()).isIn("COMPLETED", "PARTIAL");
        }

        @Test
        @DisplayName("Replay with no queued actions returns 404")
        void replayNoQueuedActions() throws Exception {
            // Issue entitlement but don't capture any actions
            MvcResult entResult = issueEntitlement();
            String entitlementId = MAPPER.readTree(entResult.getResponse().getContentAsString())
                    .get("entitlementId").asText();

            mockMvc.perform(post("/internal/v1/offline/replay/" + entitlementId)
                            .header("X-Tenant-ID", TENANT_ID)
                            .header("X-Pod-ID", "national")
                            .header("X-Request-ID", UUID.randomUUID().toString())
                            .header("X-Correlation-ID", UUID.randomUUID().toString())
                            .header("Idempotency-Key", "replay-empty-" + System.nanoTime()))
                    .andExpect(status().isNotFound());
        }
    }

    // ── E) Outbox events ──

    @Nested
    @DisplayName("E) Outbox events on entitlement and action")
    class OutboxValidation {

        @Test
        @DisplayName("Issued entitlement produces entitlement.issued.v1 outbox event")
        void outboxOnIssue() throws Exception {
            String idempotencyKey = "outbox-ent-" + System.nanoTime();

            MvcResult result = mockMvc.perform(post("/internal/v1/offline/entitlements")
                            .header("X-Tenant-ID", TENANT_ID)
                            .header("X-Pod-ID", "national")
                            .header("X-Request-ID", UUID.randomUUID().toString())
                            .header("X-Correlation-ID", UUID.randomUUID().toString())
                            .header("Idempotency-Key", idempotencyKey)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"actorId\":\"nurse-outbox\",\"facilityRef\":\"FAC-OBX\"}"))
                    .andExpect(status().isCreated())
                    .andReturn();

            String entId = MAPPER.readTree(result.getResponse().getContentAsString())
                    .get("entitlementId").asText();

            var outboxRows = outboxRepository.findByAggregateIdAndEventType(
                    entId, "impilo.offline.entitlement.issued.v1");
            assertThat(outboxRows).hasSize(1);
            assertThat(outboxRows.get(0).getIdempotencyKey()).isEqualTo(idempotencyKey);
        }
    }

    // ── F) List actions ──

    @Nested
    @DisplayName("F) List actions endpoint")
    class ListActions {

        @Test
        @DisplayName("List actions returns paged response")
        void listActionsReturnsPage() throws Exception {
            mockMvc.perform(get("/internal/v1/offline/actions")
                            .header("X-Tenant-ID", TENANT_ID)
                            .header("X-Pod-ID", "national")
                            .header("X-Request-ID", UUID.randomUUID().toString())
                            .header("X-Correlation-ID", UUID.randomUUID().toString())
                            .param("cursor", "0")
                            .param("limit", "10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items").isArray())
                    .andExpect(jsonPath("$.limit").value(10));
        }
    }

    // ── Helpers ──

    private MvcResult issueEntitlement() throws Exception {
        return mockMvc.perform(post("/internal/v1/offline/entitlements")
                        .header("X-Tenant-ID", TENANT_ID)
                        .header("X-Pod-ID", "national")
                        .header("X-Request-ID", UUID.randomUUID().toString())
                        .header("X-Correlation-ID", UUID.randomUUID().toString())
                        .header("Idempotency-Key", "helper-ent-" + System.nanoTime())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"actorId\":\"nurse-helper\",\"facilityRef\":\"FAC-HELPER\"}"))
                .andExpect(status().isCreated())
                .andReturn();
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
