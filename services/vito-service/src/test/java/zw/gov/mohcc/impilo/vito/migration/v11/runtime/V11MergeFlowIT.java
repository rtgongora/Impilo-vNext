package zw.gov.mohcc.impilo.vito.migration.v11.runtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import zw.gov.mohcc.impilo.vito.core.IdentityStatus;
import zw.gov.mohcc.impilo.vito.persistence.entity.ClientEntity;
import zw.gov.mohcc.impilo.vito.persistence.repository.ClientRepository;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * V1.1 Merge Flow runtime integration tests.
 *
 * Boots the full Spring context (H2, Flyway disabled, Kafka/Redis excluded)
 * and exercises the v1.1 merge request path through MockMvc, validating:
 * <ul>
 *   <li>A) Missing required v1.1 headers => 400 with error envelope</li>
 *   <li>B) Non-national pod merge => 403 FEDERATION_AUTHORITY_VIOLATION</li>
 *   <li>C) Idempotency replay => same merge_id, no extra side effects</li>
 *   <li>D) Idempotency conflict => 409 IDENTITY_CONFLICT</li>
 *   <li>E) Outbox v1.1 context columns populated from headers</li>
 * </ul>
 *
 * Security is satisfied via @MockBean JwtDecoder + SecurityMockMvcRequestPostProcessors.jwt().
 * KafkaTemplate is mocked to prevent VitoOutboxPublisher from needing a real broker.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class V11MergeFlowIT {

    private static final String MERGE_URL = "/internal/v1/patients/merge";
    private static final UUID TENANT_UUID = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
    private static final String TENANT_ID = TENANT_UUID.toString();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ClientRepository clientRepository;

    @MockBean
    private JwtDecoder jwtDecoder;

    @MockBean
    @SuppressWarnings("unused") // prevents VitoOutboxPublisher from needing a real Kafka broker
    private KafkaTemplate<String, String> kafkaTemplate;

    private UUID survivorCrid;
    private UUID mergedCrid;

    @BeforeEach
    void setUp() {
        // Create the idempotency_keys table (not managed by JPA/Hibernate)
        jdbcTemplate.execute(
                "CREATE TABLE IF NOT EXISTS vito.idempotency_keys ("
                        + "  tenant_id       TEXT NOT NULL,"
                        + "  pod_id          TEXT NOT NULL,"
                        + "  idempotency_key TEXT NOT NULL,"
                        + "  request_hash    TEXT NOT NULL,"
                        + "  response_status INT  NOT NULL,"
                        + "  response_body   TEXT NOT NULL,"
                        + "  created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,"
                        + "  expires_at      TIMESTAMP NULL,"
                        + "  CONSTRAINT pk_idempotency_keys PRIMARY KEY (tenant_id, pod_id, idempotency_key)"
                        + ")"
        );

        // Clean up between tests
        jdbcTemplate.execute("DELETE FROM vito.idempotency_keys");
        jdbcTemplate.execute("DELETE FROM vito.event_outbox");

        // Create two client records for merge tests (C, D, E)
        clientRepository.deleteAll();
        survivorCrid = UUID.randomUUID();
        mergedCrid = UUID.randomUUID();
        createClient(survivorCrid, IdentityStatus.ACTIVE);
        createClient(mergedCrid, IdentityStatus.ACTIVE);
    }

    private void createClient(UUID crid, IdentityStatus status) {
        ClientEntity client = new ClientEntity();
        client.setTenantId(TENANT_UUID);
        client.setCrid(crid);
        client.setHealthId(UUID.randomUUID());
        client.setGivenName("Test");
        client.setFamilyName("Patient-" + crid.toString().substring(0, 8));
        client.setStatus(status);
        clientRepository.save(client);
    }

    private String mergeBody(UUID survivor, UUID... merged) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"survivor_crid\":\"").append(survivor).append("\",");
        sb.append("\"merged_crids\":[");
        for (int i = 0; i < merged.length; i++) {
            if (i > 0) sb.append(",");
            sb.append("\"").append(merged[i]).append("\"");
        }
        sb.append("],\"reason\":\"DUPLICATE_REGISTRATION\"}");
        return sb.toString();
    }

    // ----- Test A: Missing required v1.1 headers => 400 -----

    @Test
    @DisplayName("A) POST /internal/v1/patients/merge with no v1.1 headers => 400 with error envelope")
    void missingRequiredHeaders_returns400_withErrorEnvelope() throws Exception {
        // POST with jwt() for auth but NO v1.1 headers at all
        mockMvc.perform(post(MERGE_URL)
                        .with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mergeBody(survivorCrid, mergedCrid)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists())
                .andExpect(jsonPath("$.error.code").value("MISSING_REQUIRED_HEADER"))
                .andExpect(jsonPath("$.error.message").exists())
                .andExpect(jsonPath("$.error.request_id").exists())
                .andExpect(jsonPath("$.error.correlation_id").exists());
    }

    @Test
    @DisplayName("A.1) POST with partial headers (missing X-Pod-ID) => 400 with missing list")
    void partialHeaders_returns400_withMissingDetail() throws Exception {
        mockMvc.perform(post(MERGE_URL)
                        .with(jwt())
                        .header("X-Tenant-ID", TENANT_ID)
                        .header("X-Request-ID", UUID.randomUUID().toString())
                        .header("X-Correlation-ID", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mergeBody(survivorCrid, mergedCrid)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("MISSING_REQUIRED_HEADER"))
                .andExpect(jsonPath("$.error.details.missing").isArray());
    }

    // ----- Test B: Non-national pod => 403 FEDERATION_AUTHORITY_VIOLATION -----

    @Test
    @DisplayName("B) Non-national pod merge blocked => 403 FEDERATION_AUTHORITY_VIOLATION")
    void nonNationalPod_mergeBlocked_returns403_federationViolation() throws Exception {
        String requestId = UUID.randomUUID().toString();
        String correlationId = UUID.randomUUID().toString();

        mockMvc.perform(post(MERGE_URL)
                        .with(jwt())
                        .header("X-Tenant-ID", TENANT_ID)
                        .header("X-Pod-ID", "privatepod-001")
                        .header("X-Request-ID", requestId)
                        .header("X-Correlation-ID", correlationId)
                        .header("Idempotency-Key", "idem-fed-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mergeBody(survivorCrid, mergedCrid)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FEDERATION_AUTHORITY_VIOLATION"))
                .andExpect(jsonPath("$.error.message").exists())
                .andExpect(jsonPath("$.error.details.pod_id").value("privatepod-001"));
    }

    // ----- Test C: Idempotency replay => same result, no extra side effects -----

    @Test
    @DisplayName("C) Idempotency replay: same key + same body => same merge_id, stable outbox")
    void idempotencyReplay_returnsSameResult_andSingleSideEffect() throws Exception {
        String idempotencyKey = "idem-replay-" + UUID.randomUUID();
        String body = mergeBody(survivorCrid, mergedCrid);

        // First request
        MvcResult first = mockMvc.perform(post(MERGE_URL)
                        .with(jwt())
                        .header("X-Tenant-ID", TENANT_ID)
                        .header("X-Pod-ID", "national")
                        .header("X-Request-ID", UUID.randomUUID().toString())
                        .header("X-Correlation-ID", UUID.randomUUID().toString())
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode firstJson = objectMapper.readTree(first.getResponse().getContentAsString());
        String firstMergeId = firstJson.get("merge_id").asText();
        assertThat(firstMergeId).isNotNull().isNotEmpty();

        // Count outbox rows written by first request
        Integer outboxCountAfterFirst = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM vito.event_outbox WHERE aggregate_type = 'MERGE'",
                Integer.class);

        // Replay: same key + same body
        MvcResult second = mockMvc.perform(post(MERGE_URL)
                        .with(jwt())
                        .header("X-Tenant-ID", TENANT_ID)
                        .header("X-Pod-ID", "national")
                        .header("X-Request-ID", UUID.randomUUID().toString())
                        .header("X-Correlation-ID", UUID.randomUUID().toString())
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode secondJson = objectMapper.readTree(second.getResponse().getContentAsString());
        String secondMergeId = secondJson.get("merge_id").asText();

        // Both responses yield the same merge_id
        assertThat(secondMergeId).isEqualTo(firstMergeId);

        // Replay must NOT add additional outbox rows
        Integer outboxCountAfterReplay = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM vito.event_outbox WHERE aggregate_type = 'MERGE'",
                Integer.class);
        assertThat(outboxCountAfterReplay).isEqualTo(outboxCountAfterFirst);

        // Idempotency record exists
        Integer idempotencyCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM vito.idempotency_keys WHERE tenant_id = ? AND idempotency_key = ?",
                Integer.class, TENANT_ID, idempotencyKey);
        assertThat(idempotencyCount).isEqualTo(1);
    }

    // ----- Test D: Idempotency conflict => 409 -----

    @Test
    @DisplayName("D) Idempotency conflict: same key + different body => 409 IDENTITY_CONFLICT")
    void idempotencyConflict_sameKeyDifferentBody_returns409() throws Exception {
        String idempotencyKey = "idem-conflict-" + UUID.randomUUID();

        // First request succeeds
        mockMvc.perform(post(MERGE_URL)
                        .with(jwt())
                        .header("X-Tenant-ID", TENANT_ID)
                        .header("X-Pod-ID", "national")
                        .header("X-Request-ID", UUID.randomUUID().toString())
                        .header("X-Correlation-ID", UUID.randomUUID().toString())
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mergeBody(survivorCrid, mergedCrid)))
                .andExpect(status().isOk());

        // Second request: same key, different body (different merged CRIDs)
        UUID differentMergedCrid = UUID.randomUUID();
        createClient(differentMergedCrid, IdentityStatus.ACTIVE);

        mockMvc.perform(post(MERGE_URL)
                        .with(jwt())
                        .header("X-Tenant-ID", TENANT_ID)
                        .header("X-Pod-ID", "national")
                        .header("X-Request-ID", UUID.randomUUID().toString())
                        .header("X-Correlation-ID", UUID.randomUUID().toString())
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mergeBody(survivorCrid, differentMergedCrid)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("IDENTITY_CONFLICT"))
                .andExpect(jsonPath("$.error.message").exists())
                .andExpect(jsonPath("$.error.details.idempotency_key").value(idempotencyKey));
    }

    // ----- Test E: Outbox v1.1 context columns from headers (Step 3) -----

    @Test
    @DisplayName("E) Outbox row includes v1.1 context columns populated from request headers")
    void outboxRow_hasV11ContextColumns_fromHeaders() throws Exception {
        String requestId = UUID.randomUUID().toString();
        String correlationId = UUID.randomUUID().toString();
        String idempotencyKey = "idem-outbox-" + UUID.randomUUID();

        mockMvc.perform(post(MERGE_URL)
                        .with(jwt())
                        .header("X-Tenant-ID", TENANT_ID)
                        .header("X-Pod-ID", "national")
                        .header("X-Request-ID", requestId)
                        .header("X-Correlation-ID", correlationId)
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mergeBody(survivorCrid, mergedCrid)))
                .andExpect(status().isOk());

        // Query the outbox row for this merge
        var outboxRows = jdbcTemplate.queryForList(
                "SELECT tenant_id, pod_id, correlation_id, idempotency_key, event_type, payload "
                        + "FROM vito.event_outbox WHERE aggregate_type = 'MERGE' ORDER BY id DESC");

        assertThat(outboxRows).isNotEmpty();

        var latestRow = outboxRows.get(0);

        // Assert v1.1 context columns match the headers exactly
        assertThat(latestRow.get("tenant_id")).isEqualTo(TENANT_ID);
        assertThat(latestRow.get("pod_id")).isEqualTo("national");
        assertThat(latestRow.get("correlation_id")).isEqualTo(correlationId);
        assertThat(latestRow.get("idempotency_key")).isEqualTo(idempotencyKey);

        // Assert legacy event type is present (MergeService writes "vito.merge.executed")
        assertThat((String) latestRow.get("event_type")).contains("merge");
    }

    // ----- Test: Missing Idempotency-Key on command endpoint => 400 -----

    @Test
    @DisplayName("Missing Idempotency-Key on POST v1.1 endpoint => 400 IDEMPOTENCY_KEY_REQUIRED")
    void missingIdempotencyKey_returns400() throws Exception {
        mockMvc.perform(post(MERGE_URL)
                        .with(jwt())
                        .header("X-Tenant-ID", TENANT_ID)
                        .header("X-Pod-ID", "national")
                        .header("X-Request-ID", UUID.randomUUID().toString())
                        .header("X-Correlation-ID", UUID.randomUUID().toString())
                        // deliberately NO Idempotency-Key
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mergeBody(survivorCrid, mergedCrid)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("IDEMPOTENCY_KEY_REQUIRED"));
    }
}
