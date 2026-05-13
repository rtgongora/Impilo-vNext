package zw.gov.mohcc.impilo.mushex;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import zw.gov.mohcc.impilo.mushex.config.MushexProperties;
import zw.gov.mohcc.impilo.mushex.domain.entity.PaymentAttemptEntity;
import zw.gov.mohcc.impilo.mushex.domain.entity.PaymentIntentEntity;
import zw.gov.mohcc.impilo.mushex.domain.enums.AdapterType;
import zw.gov.mohcc.impilo.mushex.domain.enums.AttemptStatus;
import zw.gov.mohcc.impilo.mushex.domain.enums.IntentStatus;
import zw.gov.mohcc.impilo.mushex.domain.enums.SourceType;
import zw.gov.mohcc.impilo.mushex.domain.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.mushex.domain.repository.PaymentAttemptRepository;
import zw.gov.mohcc.impilo.mushex.domain.repository.PaymentIntentRepository;
import zw.gov.mohcc.impilo.mushex.service.UlidGenerator;
import zw.gov.mohcc.impilo.shared.auth.AccessMode;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for {@code POST /mushex/v1/payment-intents/{id}/attempts} —
 * Phase 3 attempt-time rail enforcement.
 *
 * <p>Test matrix from
 * {@code docs/design/phase-3-attempt-time-rail-enforcement-implementation.md} §9.
 */
@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration,org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration",
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=",
        "mushex.adapters.sandbox.simulate-delay-ms=0"
})
@ActiveProfiles("test")
@AutoConfigureMockMvc(addFilters = false)
class PaymentAttemptIntegrationTest {

    @MockBean
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private PaymentIntentRepository intentRepository;
    @Autowired private PaymentAttemptRepository attemptRepository;
    @Autowired private EventOutboxRepository outboxRepository;
    @Autowired private MushexProperties mushexProperties;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID facilityId = UUID.randomUUID();
    private final UUID correlationId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        outboxRepository.deleteAll();
        attemptRepository.deleteAll();
        intentRepository.deleteAll();

        mushexProperties.getAdapters().getMobileMoney().setEnabled(false);
        mushexProperties.getAdapters().getMobileMoney().setCredentialsConfigured(false);
        mushexProperties.getAdapters().getBankTransfer().setEnabled(false);
        mushexProperties.getAdapters().getBankTransfer().setCredentialsConfigured(false);
        mushexProperties.getAdapters().getCardGateway().setEnabled(false);
        mushexProperties.getAdapters().getCardGateway().setCredentialsConfigured(false);

        TrustContext ctx = new TrustContext(
                tenantId, "test-actor", "FACILITY_FINANCE", "BILLING",
                "test-device", correlationId, facilityId, null, null, AccessMode.INTERNAL);
        TrustContextHolder.set(ctx);
    }

    @AfterEach
    void tearDown() {
        TrustContextHolder.clear();
    }

    private PaymentIntentEntity seedIntent(IntentStatus status, String metadata) {
        PaymentIntentEntity intent = new PaymentIntentEntity();
        intent.setIntentId(UlidGenerator.generate());
        intent.setTenantId(tenantId);
        intent.setFacilityId(facilityId);
        intent.setSourceType(SourceType.COSTA_BILL);
        intent.setSourceId("BILL-ATT-" + intent.getIntentId());
        intent.setCurrency("USD");
        intent.setAmountTotal(new BigDecimal("75.00"));
        intent.setAmountPaid(BigDecimal.ZERO);
        intent.setStatus(status);
        intent.setIdempotencyKey("idem-int-" + intent.getIntentId());
        intent.setMetadata(metadata);
        return intentRepository.save(intent);
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder attemptRequest(String intentId, String body) {
        var req = post("/mushex/v1/payment-intents/" + intentId + "/attempts")
                .header("X-Tenant-Id", tenantId.toString())
                .header("X-Actor-Id", "test-actor")
                .header("X-Actor-Type", "FACILITY_FINANCE")
                .header("X-Purpose-Of-Use", "BILLING")
                .header("X-Device-Fingerprint", "test-device")
                .header("X-Correlation-Id", correlationId.toString())
                .header("X-Facility-Id", facilityId.toString())
                .contentType(MediaType.APPLICATION_JSON);
        if (body != null) {
            req = req.content(body);
        }
        return req;
    }

    @Test
    void initiateAttempt_sandboxRail_returns201AndPersistsSucceededAttempt() throws Exception {
        PaymentIntentEntity intent = seedIntent(IntentStatus.PENDING,
                "{\"rail_selection\":{\"effective_rail\":\"SANDBOX\"}}");

        mockMvc.perform(attemptRequest(intent.getIntentId(), "{\"idempotencyKey\":\"att-1\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.intentId").value(intent.getIntentId()))
                .andExpect(jsonPath("$.data.adapterType").value("SANDBOX"))
                .andExpect(jsonPath("$.data.idempotencyKey").value("att-1"));

        assertEquals(1, attemptRepository.count());
        PaymentAttemptEntity saved = attemptRepository.findAll().get(0);
        assertEquals(AttemptStatus.SUCCEEDED, saved.getStatus());
        assertEquals(AdapterType.SANDBOX, saved.getAdapterType());
        assertNotNull(saved.getEnforcementMetadata());
        assertTrue(saved.getEnforcementMetadata().contains("\"safety_gate\":\"OK\""));

        PaymentIntentEntity updated = intentRepository.findById(intent.getIntentId()).orElseThrow();
        assertEquals(IntentStatus.PAID, updated.getStatus());
    }

    @Test
    void initiateAttempt_idempotencyReplayReturnsExistingAttempt() throws Exception {
        PaymentIntentEntity intent = seedIntent(IntentStatus.PENDING,
                "{\"rail_selection\":{\"effective_rail\":\"SANDBOX\"}}");

        String body = "{\"idempotencyKey\":\"att-replay\"}";
        String first = mockMvc.perform(attemptRequest(intent.getIntentId(), body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String second = mockMvc.perform(attemptRequest(intent.getIntentId(), body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        JsonNode firstId = objectMapper.readTree(first).get("data").get("id");
        JsonNode secondId = objectMapper.readTree(second).get("data").get("id");
        assertEquals(firstId.asText(), secondId.asText());
        assertEquals(1, attemptRepository.count());
    }

    @Test
    void initiateAttempt_idempotencyViaHeaderAlsoReplays() throws Exception {
        PaymentIntentEntity intent = seedIntent(IntentStatus.PENDING,
                "{\"rail_selection\":{\"effective_rail\":\"SANDBOX\"}}");

        String first = mockMvc.perform(attemptRequest(intent.getIntentId(), "{}")
                        .header("X-Idempotency-Key", "att-header"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String second = mockMvc.perform(attemptRequest(intent.getIntentId(), "{}")
                        .header("X-Idempotency-Key", "att-header"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        assertEquals(
                objectMapper.readTree(first).get("data").get("id").asText(),
                objectMapper.readTree(second).get("data").get("id").asText());
        assertEquals(1, attemptRepository.count());
    }

    @Test
    void initiateAttempt_notFoundIntent_returns404() throws Exception {
        mockMvc.perform(attemptRequest("01DOESNOTEXIST", "{}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("INTENT_NOT_FOUND"));
    }

    @Test
    void getIntent_notFound_returns404NotFound() throws Exception {
        // Phase 3 follow-on: previously this returned a 500 because IllegalArgumentException
        // was unhandled. With the IntentNotFoundException + @ExceptionHandler refactor it is
        // a clean 404 INTENT_NOT_FOUND across every endpoint on the controller.
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/mushex/v1/payment-intents/01DOESNOTEXIST")
                        .header("X-Tenant-Id", tenantId.toString())
                        .header("X-Actor-Id", "test-actor")
                        .header("X-Actor-Type", "FACILITY_FINANCE")
                        .header("X-Purpose-Of-Use", "BILLING")
                        .header("X-Device-Fingerprint", "test-device")
                        .header("X-Correlation-Id", correlationId.toString())
                        .header("X-Facility-Id", facilityId.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("INTENT_NOT_FOUND"));
    }

    @Test
    void initiateAttempt_notPayableIntent_returns422() throws Exception {
        PaymentIntentEntity intent = seedIntent(IntentStatus.CANCELLED,
                "{\"rail_selection\":{\"effective_rail\":\"SANDBOX\"}}");

        mockMvc.perform(attemptRequest(intent.getIntentId(), "{}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("INTENT_NOT_PAYABLE"));
    }

    @Test
    void initiateAttempt_realRailWithSafetyGateOff_persistsBlockedAttempt() throws Exception {
        PaymentIntentEntity intent = seedIntent(IntentStatus.PENDING,
                "{\"rail_selection\":{\"effective_rail\":\"MOBILE_MONEY\"}}");

        mockMvc.perform(attemptRequest(intent.getIntentId(), "{}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.adapterType").value("MOBILE_MONEY"))
                .andExpect(jsonPath("$.data.status").value("INITIATED"));

        PaymentAttemptEntity saved = attemptRepository.findAll().get(0);
        assertTrue(saved.getEnforcementMetadata().contains("\"safety_gate\":\"BLOCKED_PRE_LIVE\""));
    }

    @Test
    void initiateAttempt_realRailWithBothFlagsButNotLiveCapable_isStillBlocked() throws Exception {
        // Even with both config flags on, the third leg of the safety gate
        // (PaymentRailAdapter.liveCapable()) prevents the stub adapter from being called.
        // All four adapters in this repository today default to liveCapable()=false; this
        // test pins that contract end-to-end.
        mushexProperties.getAdapters().getMobileMoney().setEnabled(true);
        mushexProperties.getAdapters().getMobileMoney().setCredentialsConfigured(true);

        PaymentIntentEntity intent = seedIntent(IntentStatus.PENDING,
                "{\"rail_selection\":{\"effective_rail\":\"MOBILE_MONEY\"}}");

        mockMvc.perform(attemptRequest(intent.getIntentId(), "{}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.adapterType").value("MOBILE_MONEY"))
                .andExpect(jsonPath("$.data.status").value("INITIATED"));

        PaymentAttemptEntity saved = attemptRepository.findAll().get(0);
        assertTrue(saved.getEnforcementMetadata().contains("\"safety_gate\":\"BLOCKED_NOT_LIVE_CAPABLE\""));
    }

    @Test
    void initiateAttempt_legacyIntentNoMetadata_fallsBackToSandbox() throws Exception {
        PaymentIntentEntity intent = seedIntent(IntentStatus.PENDING, null);

        mockMvc.perform(attemptRequest(intent.getIntentId(), "{}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.adapterType").value("SANDBOX"));

        PaymentAttemptEntity saved = attemptRepository.findAll().get(0);
        assertTrue(saved.getEnforcementMetadata().contains("\"reason\":\"LEGACY_FALLBACK\""));
        assertTrue(saved.getEnforcementMetadata().contains("\"legacy_intent\":true"));
    }

    @Test
    void initiateAttempt_unregisteredEffectiveRail_returns422() throws Exception {
        PaymentIntentEntity intent = seedIntent(IntentStatus.PENDING,
                "{\"rail_selection\":{\"effective_rail\":\"GARBAGE\"}}");

        mockMvc.perform(attemptRequest(intent.getIntentId(), "{}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("RAIL_ADAPTER_UNAVAILABLE_AT_ATTEMPT"));
    }

    @Test
    void initiateAttempt_emitsAttemptInitiatedOutboxEvent() throws Exception {
        PaymentIntentEntity intent = seedIntent(IntentStatus.PENDING,
                "{\"rail_selection\":{\"effective_rail\":\"SANDBOX\"}}");

        mockMvc.perform(attemptRequest(intent.getIntentId(), "{\"idempotencyKey\":\"att-evt\"}"))
                .andExpect(status().isCreated());

        long attemptEvents = outboxRepository.findAll().stream()
                .filter(e -> "ATTEMPT_INITIATED".equals(e.getEventType()))
                .count();
        assertEquals(1L, attemptEvents);
    }

    @Test
    void listAttempts_unknownIntent_returns404() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/mushex/v1/payment-intents/01DOESNOTEXIST/attempts")
                        .header("X-Tenant-Id", tenantId.toString())
                        .header("X-Actor-Id", "test-actor")
                        .header("X-Actor-Type", "FACILITY_FINANCE")
                        .header("X-Purpose-Of-Use", "BILLING")
                        .header("X-Device-Fingerprint", "test-device")
                        .header("X-Correlation-Id", correlationId.toString())
                        .header("X-Facility-Id", facilityId.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("INTENT_NOT_FOUND"));
    }

    @Test
    void listAttempts_existingIntentNoAttempts_returnsEmptyList() throws Exception {
        PaymentIntentEntity intent = seedIntent(IntentStatus.PENDING,
                "{\"rail_selection\":{\"effective_rail\":\"SANDBOX\"}}");

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/mushex/v1/payment-intents/" + intent.getIntentId() + "/attempts")
                        .header("X-Tenant-Id", tenantId.toString())
                        .header("X-Actor-Id", "test-actor")
                        .header("X-Actor-Type", "FACILITY_FINANCE")
                        .header("X-Purpose-Of-Use", "BILLING")
                        .header("X-Device-Fingerprint", "test-device")
                        .header("X-Correlation-Id", correlationId.toString())
                        .header("X-Facility-Id", facilityId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void listAttempts_afterInitiatingAttempt_returnsThatAttempt() throws Exception {
        PaymentIntentEntity intent = seedIntent(IntentStatus.PENDING,
                "{\"rail_selection\":{\"effective_rail\":\"SANDBOX\"}}");
        mockMvc.perform(attemptRequest(intent.getIntentId(), "{\"idempotencyKey\":\"att-list-1\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/mushex/v1/payment-intents/" + intent.getIntentId() + "/attempts")
                        .header("X-Tenant-Id", tenantId.toString())
                        .header("X-Actor-Id", "test-actor")
                        .header("X-Actor-Type", "FACILITY_FINANCE")
                        .header("X-Purpose-Of-Use", "BILLING")
                        .header("X-Device-Fingerprint", "test-device")
                        .header("X-Correlation-Id", correlationId.toString())
                        .header("X-Facility-Id", facilityId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].adapterType").value("SANDBOX"))
                .andExpect(jsonPath("$.data[0].idempotencyKey").value("att-list-1"));
    }

    @Test
    void reselect_unknownIntent_returns404() throws Exception {
        mockMvc.perform(post("/mushex/v1/payment-intents/01MISSING/attempts/reselect")
                        .header("X-Tenant-Id", tenantId.toString())
                        .header("X-Actor-Id", "test-actor")
                        .header("X-Actor-Type", "FACILITY_FINANCE")
                        .header("X-Purpose-Of-Use", "BILLING")
                        .header("X-Device-Fingerprint", "test-device")
                        .header("X-Correlation-Id", correlationId.toString())
                        .header("X-Facility-Id", facilityId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"x\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("INTENT_NOT_FOUND"));
    }

    @Test
    void reselect_updatesMetadataAndEmitsAttemptReselectedEvent() throws Exception {
        PaymentIntentEntity intent = seedIntent(IntentStatus.PENDING,
                "{\"rail_selection\":{\"effective_rail\":\"MOBILE_MONEY\",\"reason\":\"EXPLICIT_PREFERRED\"}}");

        String response = mockMvc.perform(post(
                "/mushex/v1/payment-intents/" + intent.getIntentId() + "/attempts/reselect")
                        .header("X-Tenant-Id", tenantId.toString())
                        .header("X-Actor-Id", "test-actor")
                        .header("X-Actor-Type", "FACILITY_FINANCE")
                        .header("X-Purpose-Of-Use", "BILLING")
                        .header("X-Device-Fingerprint", "test-device")
                        .header("X-Correlation-Id", correlationId.toString())
                        .header("X-Facility-Id", facilityId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"provider-outage\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        // The HTTP envelope returns the intent with metadata as a string (jsonb). To make
        // the assertions readable we re-load the intent from the DB and parse its metadata.
        assertNotNull(response);
        PaymentIntentEntity updated = intentRepository.findById(intent.getIntentId()).orElseThrow();
        JsonNode meta = objectMapper.readTree(updated.getMetadata());
        assertTrue(meta.get("rail_selection").get("reselected").asBoolean());
        assertTrue(meta.get("rail_selection").get("history").isArray());
        assertEquals(1, meta.get("rail_selection").get("history").size());

        long reselectedEvents = outboxRepository.findAll().stream()
                .filter(e -> "ATTEMPT_RESELECTED".equals(e.getEventType()))
                .count();
        assertEquals(1L, reselectedEvents);
    }
}
