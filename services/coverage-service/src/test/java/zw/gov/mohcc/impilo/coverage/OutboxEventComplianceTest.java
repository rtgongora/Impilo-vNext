package zw.gov.mohcc.impilo.coverage;

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
import zw.gov.mohcc.impilo.coverage.domain.OutboxEventEntity;
import zw.gov.mohcc.impilo.coverage.repository.OutboxEventRepository;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Validates outbox rows comply with v1.1 requirements:
 * - event_type starts with "impilo.coverage." and ends with ".v1"
 * - schema_version >= 1
 * - payload_json contains EventEnvelope with meta.partition_key
 * - v1.1 context columns (correlation_id, tenant_id, pod_id, subject_id, subject_type) populated
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class OutboxEventComplianceTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OutboxEventRepository outboxRepository;

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String TENANT_ID = "f47ac10b-58cc-4372-a567-0e02b2c3d479";

    private OutboxEventEntity unpublishedByAggregateId(UUID aggregateId) {
        return outboxRepository.findAll().stream()
                .filter(e -> e.getPublishedAt() == null && aggregateId.toString().equals(e.getAggregateId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No unpublished outbox row for aggregate " + aggregateId));
    }

    /** H2 JSON / JPA may surface JSON as a quoted string; normalize to an object node. */
    private static JsonNode parsePayloadObject(String payloadJson) {
        try {
            JsonNode n = MAPPER.readTree(payloadJson);
            if (n != null && n.isTextual()) {
                n = MAPPER.readTree(n.asText());
            }
            return n;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    @DisplayName("Coverage plan creation writes compliant outbox row")
    void coveragePlanOutboxCompliant() throws Exception {
        long countBefore = outboxRepository.count();

        String key = "outbox-compliance-plan-" + System.nanoTime();
        MvcResult created = mockMvc.perform(post("/internal/v1/coverage")
                        .header("X-Tenant-ID", TENANT_ID)
                        .header("X-Pod-ID", "national-spine")
                        .header("X-Request-ID", "req-outbox-1")
                        .header("X-Correlation-ID", "corr-outbox-1")
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"planCode\":\"PLAN-OB\",\"planName\":\"Outbox Compliance\",\"payerId\":\"payer-ob\",\"planType\":\"MEDICAL_AID\",\"effectiveFrom\":\"2026-01-01\"}"))
                .andExpect(status().isCreated())
                .andReturn();

        long countAfter = outboxRepository.count();
        assertThat(countAfter).isGreaterThan(countBefore);

        UUID planId = UUID.fromString(MAPPER.readTree(created.getResponse().getContentAsString()).get("id").asText());
        OutboxEventEntity latest = unpublishedByAggregateId(planId);

        // event_type starts with "impilo.coverage." and ends with ".v1"
        assertThat(latest.getEventType()).startsWith("impilo.coverage.");
        assertThat(latest.getEventType()).endsWith(".v1");

        // schema_version >= 1
        assertThat(latest.getSchemaVersion()).isNotNull();
        String[] versionParts = latest.getSchemaVersion().split("\\.");
        assertThat(Integer.parseInt(versionParts[0])).isGreaterThanOrEqualTo(1);

        // v1.1 context columns populated
        assertThat(latest.getTenantId()).isNotNull();
        assertThat(latest.getPodId()).isNotNull();
        assertThat(latest.getSubjectId()).isNotNull();
        assertThat(latest.getSubjectType()).isNotNull();
        assertThat(latest.getCorrelationId()).isNotNull();
        assertThat(latest.getIdempotencyKey()).isNotNull();
        assertThat(latest.getProducer()).isEqualTo("coverage-service");

        // payload_json is the business payload; envelope fields live on the outbox row / publisher
        String payloadJson = latest.getPayloadJson();
        assertThat(payloadJson).isNotBlank();
        JsonNode payload = parsePayloadObject(payloadJson);
        assertThat(payload.has("meta")).isTrue();
        assertThat(payload.get("meta").has("partition_key")).isTrue();
        assertThat(payload.get("meta").get("partition_key").asText()).isNotBlank();
    }

    @Test
    @DisplayName("Remittance creation writes compliant outbox row")
    void remittanceOutboxCompliant() throws Exception {
        String key = "outbox-compliance-remit-" + System.nanoTime();
        MvcResult created = mockMvc.perform(post("/internal/v1/coverage/remittances")
                        .header("X-Tenant-ID", TENANT_ID)
                        .header("X-Pod-ID", "national-spine")
                        .header("X-Request-ID", "req-outbox-2")
                        .header("X-Correlation-ID", "corr-outbox-2")
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"payerId\":\"payer-ob\",\"providerRef\":\"prov-ob\",\"amount\":500.00}"))
                .andExpect(status().isCreated())
                .andReturn();

        UUID remittanceId = UUID.fromString(MAPPER.readTree(created.getResponse().getContentAsString()).get("id").asText());
        OutboxEventEntity latest = unpublishedByAggregateId(remittanceId);

        assertThat(latest.getEventType()).isEqualTo("impilo.coverage.remittance.created.v1");
        assertThat(latest.getSubjectType()).isEqualTo("REMITTANCE");

        JsonNode payload = parsePayloadObject(latest.getPayloadJson());
        assertThat(payload.has("meta")).isTrue();
        assertThat(payload.get("meta").has("partition_key")).isTrue();
        assertThat(payload.get("meta").get("partition_key").asText()).isNotBlank();
    }

    @Test
    @DisplayName("Eligibility check writes compliant outbox row")
    void eligibilityOutboxCompliant() throws Exception {
        String key = "outbox-compliance-elig-" + System.nanoTime();
        MvcResult created = mockMvc.perform(post("/internal/v1/coverage/eligibility")
                        .header("X-Tenant-ID", TENANT_ID)
                        .header("X-Pod-ID", "national-spine")
                        .header("X-Request-ID", "req-outbox-3")
                        .header("X-Correlation-ID", "corr-outbox-3")
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"coverageId\":\"00000000-0000-0000-0000-000000000001\",\"patientRef\":\"cpid-ob\"}"))
                .andExpect(status().isCreated())
                .andReturn();

        UUID checkId = UUID.fromString(MAPPER.readTree(created.getResponse().getContentAsString()).get("id").asText());
        OutboxEventEntity latest = unpublishedByAggregateId(checkId);

        assertThat(latest.getEventType()).isEqualTo("impilo.coverage.eligibility.checked.v1");
        assertThat(latest.getSubjectType()).isEqualTo("MEMBER_COVERAGE");

        JsonNode payload = parsePayloadObject(latest.getPayloadJson());
        assertThat(payload.has("meta")).isTrue();
        JsonNode meta = payload.get("meta");
        assertThat(meta.has("decision")).isTrue();
        assertThat(meta.get("decision").has("evidence")).isTrue();
    }

    @Test
    @DisplayName("Idempotency replay does NOT create duplicate outbox row")
    void idempotencyReplayNoDoubleOutbox() throws Exception {
        long countBefore = outboxRepository.count();

        String key = "outbox-nodup-" + System.nanoTime();
        String body = "{\"payerId\":\"payer-nodup\",\"providerRef\":\"prov-nodup\",\"amount\":100.00}";

        mockMvc.perform(post("/internal/v1/coverage/remittances")
                        .header("X-Tenant-ID", TENANT_ID)
                        .header("X-Pod-ID", "national-spine")
                        .header("X-Request-ID", "req-1")
                        .header("X-Correlation-ID", "corr-1")
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());

        long countAfterFirst = outboxRepository.count();
        assertThat(countAfterFirst).isEqualTo(countBefore + 1);

        // Replay
        mockMvc.perform(post("/internal/v1/coverage/remittances")
                        .header("X-Tenant-ID", TENANT_ID)
                        .header("X-Pod-ID", "national-spine")
                        .header("X-Request-ID", "req-2")
                        .header("X-Correlation-ID", "corr-1")
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());

        long countAfterReplay = outboxRepository.count();
        // Replay should NOT create additional outbox row
        assertThat(countAfterReplay).isEqualTo(countAfterFirst);
    }
}
