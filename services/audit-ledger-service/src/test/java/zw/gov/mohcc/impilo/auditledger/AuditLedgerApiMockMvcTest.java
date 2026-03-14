package zw.gov.mohcc.impilo.auditledger;

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
import zw.gov.mohcc.impilo.auditledger.repository.AuditRecordRepository;
import zw.gov.mohcc.impilo.auditledger.repository.OutboxEventRepository;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class AuditLedgerApiMockMvcTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private OutboxEventRepository outboxRepository;
    @Autowired private AuditRecordRepository recordRepository;

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String TENANT_ID = UUID.randomUUID().toString();

    private String auditRecordBody(UUID correlationId) {
        return String.format("""
            {"correlationId":"%s","actorId":"user-001","actorType":"HUMAN",
             "action":"LOGIN","resourceType":"Session","resourceId":"sess-001",
             "outcome":"SUCCESS","detail":{"ip":"10.0.0.1"}}""", correlationId);
    }

    // ── A) Missing required headers => 400 ──

    @Nested
    @DisplayName("A) Missing required headers => 400 envelope")
    class MissingHeaders {

        @Test
        @DisplayName("POST /internal/v1/audit/records without X-Tenant-ID returns 400")
        void missingTenantId() throws Exception {
            MvcResult result = mockMvc.perform(post("/internal/v1/audit/records")
                            .header("X-Pod-ID", "national")
                            .header("X-Request-ID", "req-1")
                            .header("X-Correlation-ID", "corr-1")
                            .header("Idempotency-Key", "idem-1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(auditRecordBody(UUID.randomUUID())))
                    .andExpect(status().isBadRequest())
                    .andReturn();

            assertErrorEnvelope(result, "MISSING_REQUIRED_HEADER");
        }
    }

    // ── B) Append audit record + hash chaining ──

    @Nested
    @DisplayName("B) Append audit record with SHA-256 hash chaining")
    class HashChaining {

        @Test
        @DisplayName("First appended record has genesis prev_hash and valid entry_hash")
        void firstRecordHasGenesisHash() throws Exception {
            UUID correlationId = UUID.randomUUID();

            MvcResult result = mockMvc.perform(post("/internal/v1/audit/records")
                            .header("X-Tenant-ID", TENANT_ID)
                            .header("X-Pod-ID", "national")
                            .header("X-Request-ID", UUID.randomUUID().toString())
                            .header("X-Correlation-ID", UUID.randomUUID().toString())
                            .header("Idempotency-Key", "chain-first-" + System.nanoTime())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(auditRecordBody(correlationId)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.recordId").exists())
                    .andExpect(jsonPath("$.sequenceNum").value(1))
                    .andExpect(jsonPath("$.prevHash").value("0000000000000000000000000000000000000000000000000000000000000000"))
                    .andExpect(jsonPath("$.entryHash").exists())
                    .andReturn();

            JsonNode body = MAPPER.readTree(result.getResponse().getContentAsString());
            assertThat(body.get("entryHash").asText()).hasSize(64); // SHA-256 hex
        }

        @Test
        @DisplayName("Second record chains from first record's hash")
        void secondRecordChains() throws Exception {
            // Use a unique tenant to get a fresh chain
            String freshTenant = UUID.randomUUID().toString();
            UUID corr1 = UUID.randomUUID();
            UUID corr2 = UUID.randomUUID();

            // First record
            MvcResult first = mockMvc.perform(post("/internal/v1/audit/records")
                            .header("X-Tenant-ID", freshTenant)
                            .header("X-Pod-ID", "national")
                            .header("X-Request-ID", UUID.randomUUID().toString())
                            .header("X-Correlation-ID", UUID.randomUUID().toString())
                            .header("Idempotency-Key", "chain-a-" + System.nanoTime())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(auditRecordBody(corr1)))
                    .andExpect(status().isCreated())
                    .andReturn();

            String firstHash = MAPPER.readTree(first.getResponse().getContentAsString())
                    .get("entryHash").asText();

            // Second record
            MvcResult second = mockMvc.perform(post("/internal/v1/audit/records")
                            .header("X-Tenant-ID", freshTenant)
                            .header("X-Pod-ID", "national")
                            .header("X-Request-ID", UUID.randomUUID().toString())
                            .header("X-Correlation-ID", UUID.randomUUID().toString())
                            .header("Idempotency-Key", "chain-b-" + System.nanoTime())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(auditRecordBody(corr2)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.sequenceNum").value(2))
                    .andReturn();

            JsonNode secondBody = MAPPER.readTree(second.getResponse().getContentAsString());
            assertThat(secondBody.get("prevHash").asText()).isEqualTo(firstHash);
            assertThat(secondBody.get("entryHash").asText()).isNotEqualTo(firstHash);
        }
    }

    // ── C) Chain integrity verification ──

    @Nested
    @DisplayName("C) Chain integrity verification endpoint")
    class ChainVerification {

        @Test
        @DisplayName("Verify chain returns valid=true for valid chain")
        void verifyChainValid() throws Exception {
            String freshTenant = UUID.randomUUID().toString();

            // Append two records
            for (int i = 0; i < 2; i++) {
                mockMvc.perform(post("/internal/v1/audit/records")
                                .header("X-Tenant-ID", freshTenant)
                                .header("X-Pod-ID", "national")
                                .header("X-Request-ID", UUID.randomUUID().toString())
                                .header("X-Correlation-ID", UUID.randomUUID().toString())
                                .header("Idempotency-Key", "verify-" + i + "-" + System.nanoTime())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(auditRecordBody(UUID.randomUUID())))
                        .andExpect(status().isCreated());
            }

            // Verify chain
            mockMvc.perform(get("/internal/v1/audit/chain/verify")
                            .header("X-Tenant-ID", freshTenant)
                            .header("X-Pod-ID", "national")
                            .header("X-Request-ID", UUID.randomUUID().toString())
                            .header("X-Correlation-ID", UUID.randomUUID().toString())
                            .param("from_seq", "1")
                            .param("to_seq", "2"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.valid").value(true));
        }
    }

    // ── D) Correlation query ──

    @Nested
    @DisplayName("D) Query by correlation ID")
    class CorrelationQuery {

        @Test
        @DisplayName("Query returns records matching correlation_id")
        void queryByCorrelation() throws Exception {
            UUID correlationId = UUID.randomUUID();

            // Append record with specific correlation
            mockMvc.perform(post("/internal/v1/audit/records")
                            .header("X-Tenant-ID", TENANT_ID)
                            .header("X-Pod-ID", "national")
                            .header("X-Request-ID", UUID.randomUUID().toString())
                            .header("X-Correlation-ID", UUID.randomUUID().toString())
                            .header("Idempotency-Key", "query-corr-" + System.nanoTime())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(auditRecordBody(correlationId)))
                    .andExpect(status().isCreated());

            // Query by correlation
            mockMvc.perform(get("/internal/v1/audit/query")
                            .header("X-Tenant-ID", TENANT_ID)
                            .header("X-Pod-ID", "national")
                            .header("X-Request-ID", UUID.randomUUID().toString())
                            .header("X-Correlation-ID", UUID.randomUUID().toString())
                            .param("correlation_id", correlationId.toString()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items").isArray())
                    .andExpect(jsonPath("$.count").value(1));
        }
    }

    // ── E) Outbox events ──

    @Nested
    @DisplayName("E) Outbox events on append")
    class OutboxValidation {

        @Test
        @DisplayName("Appended record produces record.appended.v1 outbox event")
        void outboxOnAppend() throws Exception {
            String correlationId = UUID.randomUUID().toString();
            String idempotencyKey = "outbox-audit-" + System.nanoTime();

            MvcResult result = mockMvc.perform(post("/internal/v1/audit/records")
                            .header("X-Tenant-ID", TENANT_ID)
                            .header("X-Pod-ID", "national")
                            .header("X-Request-ID", UUID.randomUUID().toString())
                            .header("X-Correlation-ID", correlationId)
                            .header("Idempotency-Key", idempotencyKey)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(auditRecordBody(UUID.randomUUID())))
                    .andExpect(status().isCreated())
                    .andReturn();

            String recordId = MAPPER.readTree(result.getResponse().getContentAsString())
                    .get("recordId").asText();

            var outboxRows = outboxRepository.findByAggregateIdAndEventType(
                    recordId, "impilo.audit.record.appended.v1");

            assertThat(outboxRows).hasSize(1);
            var row = outboxRows.get(0);
            assertThat(row.getAggregateType()).isEqualTo("AuditRecord");
            assertThat(row.getIdempotencyKey()).isEqualTo(idempotencyKey);
            assertThat(row.getTenantId()).isNotNull();
        }
    }

    // ── F) Immutability — records cannot be updated (JPA-level) ──

    @Nested
    @DisplayName("F) Audit records are immutable (JPA updatable=false)")
    class Immutability {

        @Test
        @DisplayName("All entity fields marked updatable=false")
        void fieldsAreImmutable() {
            var fields = zw.gov.mohcc.impilo.auditledger.domain.AuditRecordEntity.class.getDeclaredFields();
            for (var field : fields) {
                var column = field.getAnnotation(jakarta.persistence.Column.class);
                if (column != null && !"id".equals(field.getName())) {
                    assertThat(column.updatable())
                            .as("Field '%s' should be immutable (updatable=false)", field.getName())
                            .isFalse();
                }
            }
        }
    }

    // ── Helpers ──

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
