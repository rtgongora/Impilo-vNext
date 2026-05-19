package zw.gov.mohcc.impilo.mushex;

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
import zw.gov.mohcc.impilo.mushex.api.dto.CreateIntentRequest;
import zw.gov.mohcc.impilo.mushex.domain.entity.PaymentIntentEntity;
import zw.gov.mohcc.impilo.mushex.domain.enums.IntentStatus;
import zw.gov.mohcc.impilo.mushex.domain.enums.SourceType;
import zw.gov.mohcc.impilo.mushex.domain.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.mushex.domain.repository.PaymentIntentRepository;
import zw.gov.mohcc.impilo.mushex.service.UlidGenerator;
import zw.gov.mohcc.impilo.shared.auth.AccessMode;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.math.BigDecimal;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for the Payment Intent HTTP API.
 *
 * Uses {@code @SpringBootTest} with an H2 in-memory database, Flyway disabled,
 * and Kafka/Redis auto-configuration excluded. Security filters are bypassed
 * via {@code addFilters = false}; {@link TrustContextHolder} is seeded in
 * {@code @BeforeEach} so controllers see the same context the trust headers represent.
 *
 * Response format is the {@code ApiResponse} envelope:
 * {@code {"success": true, "data": {...}, "correlationId": "...", "timestamp": "..."}}
 */
@SpringBootTest(properties = {
    "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration,org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration",
    "spring.security.oauth2.resourceserver.jwt.issuer-uri="
})
@ActiveProfiles("test")
@AutoConfigureMockMvc(addFilters = false)
class PaymentIntentIntegrationTest {

    @MockBean
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private PaymentIntentRepository intentRepository;
    @Autowired private EventOutboxRepository outboxRepository;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID facilityId = UUID.randomUUID();
    private final UUID correlationId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        outboxRepository.deleteAll();
        intentRepository.deleteAll();

        TrustContext ctx = new TrustContext(
                tenantId,
                "test-actor",
                "FACILITY_FINANCE",
                "BILLING",
                "test-device",
                correlationId,
                facilityId,
                null,
                null,
                AccessMode.INTERNAL);
        TrustContextHolder.set(ctx);
    }

    @AfterEach
    void tearDown() {
        TrustContextHolder.clear();
    }

    // ---------------------------------------------------------------
    // POST /mushex/v1/payment-intents
    // ---------------------------------------------------------------

    @Test
    void createIntent_returns201WithCreatedIntent() throws Exception {
        CreateIntentRequest request = new CreateIntentRequest(
            "COSTA_BILL", "BILL-IT-001",
            new BigDecimal("250.00"), "USD",
            facilityId.toString(), "idem-it-001", null
        );

        mockMvc.perform(post("/mushex/v1/payment-intents")
                .header("X-Tenant-Id", tenantId.toString())
                .header("X-Actor-Id", "test-actor")
                .header("X-Actor-Type", "FACILITY_FINANCE")
                .header("X-Purpose-Of-Use", "BILLING")
                .header("X-Device-Fingerprint", "test-device")
                .header("X-Correlation-Id", correlationId.toString())
                .header("X-Facility-Id", facilityId.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.intentId").isNotEmpty())
            .andExpect(jsonPath("$.data.status").value("CREATED"))
            .andExpect(jsonPath("$.data.amountTotal").value(250.00))
            .andExpect(jsonPath("$.data.amountPaid").value(0))
            .andExpect(jsonPath("$.data.currency").value("USD"))
            .andExpect(jsonPath("$.data.sourceType").value("COSTA_BILL"))
            .andExpect(jsonPath("$.data.sourceId").value("BILL-IT-001"));

        // Verify database state
        assertEquals(1, intentRepository.count());
        PaymentIntentEntity saved = intentRepository.findAll().get(0);
        assertEquals(IntentStatus.CREATED, saved.getStatus());
        assertEquals(new BigDecimal("250.00"), saved.getAmountTotal());
    }

    @Test
    void createIntent_idempotent_returnsSameEntity() throws Exception {
        CreateIntentRequest request = new CreateIntentRequest(
            "COSTA_BILL", "BILL-IT-002",
            new BigDecimal("100.00"), "USD",
            facilityId.toString(), "idem-it-duplicate", null
        );

        String json = objectMapper.writeValueAsString(request);

        // First request
        String response1 = mockMvc.perform(post("/mushex/v1/payment-intents")
                .header("X-Tenant-Id", tenantId.toString())
                .header("X-Actor-Id", "test-actor")
                .header("X-Actor-Type", "FACILITY_FINANCE")
                .header("X-Purpose-Of-Use", "BILLING")
                .header("X-Device-Fingerprint", "test-device")
                .header("X-Correlation-Id", UUID.randomUUID().toString())
                .header("X-Facility-Id", facilityId.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();

        // Second request with same idempotency key
        String response2 = mockMvc.perform(post("/mushex/v1/payment-intents")
                .header("X-Tenant-Id", tenantId.toString())
                .header("X-Actor-Id", "test-actor")
                .header("X-Actor-Type", "FACILITY_FINANCE")
                .header("X-Purpose-Of-Use", "BILLING")
                .header("X-Device-Fingerprint", "test-device")
                .header("X-Correlation-Id", UUID.randomUUID().toString())
                .header("X-Facility-Id", facilityId.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();

        // Both should return the same intent ID
        String id1 = objectMapper.readTree(response1).get("data").get("intentId").asText();
        String id2 = objectMapper.readTree(response2).get("data").get("intentId").asText();
        assertEquals(id1, id2);

        // Only one record in DB
        assertEquals(1, intentRepository.count());
    }

    // ---------------------------------------------------------------
    // GET /mushex/v1/payment-intents/{id}
    // ---------------------------------------------------------------

    @Test
    void listIntents_bySourceIds_returnsMatchingRows() throws Exception {
        CreateIntentRequest request = new CreateIntentRequest(
                "COSTA_BILL", "BILL-LIST-001",
                new BigDecimal("45.00"), "USD",
                facilityId.toString(), "idem-list-source-1", null
        );
        mockMvc.perform(post("/mushex/v1/payment-intents")
                        .header("X-Tenant-Id", tenantId.toString())
                        .header("X-Actor-Id", "test-actor")
                        .header("X-Actor-Type", "FACILITY_FINANCE")
                        .header("X-Purpose-Of-Use", "BILLING")
                        .header("X-Device-Fingerprint", "test-device")
                        .header("X-Correlation-Id", correlationId.toString())
                        .header("X-Facility-Id", facilityId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/mushex/v1/payment-intents")
                        .param("source_type", "COSTA_BILL")
                        .param("source_ids", "BILL-LIST-001")
                        .header("X-Tenant-Id", tenantId.toString())
                        .header("X-Actor-Id", "test-actor")
                        .header("X-Actor-Type", "FACILITY_FINANCE")
                        .header("X-Purpose-Of-Use", "BILLING")
                        .header("X-Device-Fingerprint", "test-device")
                        .header("X-Correlation-Id", correlationId.toString())
                        .header("X-Facility-Id", facilityId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].sourceType").value("COSTA_BILL"))
                .andExpect(jsonPath("$.data[0].sourceId").value("BILL-LIST-001"));
    }

    @Test
    void getIntent_existingId_returnsData() throws Exception {
        // Seed a payment intent directly
        PaymentIntentEntity intent = new PaymentIntentEntity();
        intent.setIntentId(UlidGenerator.generate());
        intent.setTenantId(tenantId);
        intent.setFacilityId(facilityId);
        intent.setSourceType(SourceType.MSIKA_ORDER);
        intent.setSourceId("ORD-IT-001");
        intent.setCurrency("USD");
        intent.setAmountTotal(new BigDecimal("500.00"));
        intent.setAmountPaid(BigDecimal.ZERO);
        intent.setStatus(IntentStatus.CREATED);
        intent.setIdempotencyKey("idem-get-test");
        intentRepository.save(intent);

        mockMvc.perform(get("/mushex/v1/payment-intents/" + intent.getIntentId())
                .header("X-Tenant-Id", tenantId.toString())
                .header("X-Actor-Id", "test-actor")
                .header("X-Actor-Type", "FACILITY_FINANCE")
                .header("X-Purpose-Of-Use", "BILLING")
                .header("X-Device-Fingerprint", "test-device")
                .header("X-Correlation-Id", correlationId.toString())
                .header("X-Facility-Id", facilityId.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.intentId").value(intent.getIntentId()))
            .andExpect(jsonPath("$.data.sourceType").value("MSIKA_ORDER"))
            .andExpect(jsonPath("$.data.sourceId").value("ORD-IT-001"))
            .andExpect(jsonPath("$.data.amountTotal").value(500.00))
            .andExpect(jsonPath("$.data.status").value("CREATED"));
    }

    // ---------------------------------------------------------------
    // POST /mushex/v1/payment-intents/{id}/cancel
    // ---------------------------------------------------------------

    @Test
    void cancelIntent_fromCreated_returns200AndCancelled() throws Exception {
        PaymentIntentEntity intent = new PaymentIntentEntity();
        intent.setIntentId(UlidGenerator.generate());
        intent.setTenantId(tenantId);
        intent.setFacilityId(facilityId);
        intent.setSourceType(SourceType.COSTA_BILL);
        intent.setSourceId("BILL-IT-CANCEL");
        intent.setCurrency("USD");
        intent.setAmountTotal(new BigDecimal("300.00"));
        intent.setAmountPaid(BigDecimal.ZERO);
        intent.setStatus(IntentStatus.CREATED);
        intent.setIdempotencyKey("idem-cancel-test");
        intentRepository.save(intent);

        mockMvc.perform(post("/mushex/v1/payment-intents/" + intent.getIntentId() + "/cancel")
                .header("X-Tenant-Id", tenantId.toString())
                .header("X-Actor-Id", "test-actor")
                .header("X-Actor-Type", "FACILITY_FINANCE")
                .header("X-Purpose-Of-Use", "BILLING")
                .header("X-Device-Fingerprint", "test-device")
                .header("X-Correlation-Id", correlationId.toString())
                .header("X-Facility-Id", facilityId.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.status").value("CANCELLED"));

        // Verify database state
        PaymentIntentEntity updated = intentRepository.findById(intent.getIntentId()).orElseThrow();
        assertEquals(IntentStatus.CANCELLED, updated.getStatus());
    }

    // ---------------------------------------------------------------
    // Outbox event generation verification
    // ---------------------------------------------------------------

    @Test
    void createIntent_generatesOutboxEvent() throws Exception {
        CreateIntentRequest request = new CreateIntentRequest(
            "ADHOC", "REF-IT-001",
            new BigDecimal("75.00"), "USD",
            facilityId.toString(), "idem-outbox-test", null
        );

        mockMvc.perform(post("/mushex/v1/payment-intents")
                .header("X-Tenant-Id", tenantId.toString())
                .header("X-Actor-Id", "test-actor")
                .header("X-Actor-Type", "FACILITY_FINANCE")
                .header("X-Purpose-Of-Use", "BILLING")
                .header("X-Device-Fingerprint", "test-device")
                .header("X-Correlation-Id", correlationId.toString())
                .header("X-Facility-Id", facilityId.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated());

        // Verify outbox event was created
        var outboxEvents = outboxRepository.findTop100ByPublishedAtIsNullOrderByCreatedAtAsc();
        assertFalse(outboxEvents.isEmpty());
        assertEquals("INTENT_CREATED", outboxEvents.get(0).getEventType());
        assertEquals("PAYMENT_INTENT", outboxEvents.get(0).getAggregateType());
    }

    // ---------------------------------------------------------------
    // G-4 rail-selection (docs/design/g4-rail-selection-policy.md)
    // ---------------------------------------------------------------

    @Test
    void createIntent_withPreferredRailAdapter_recordsItOnMetadataAndOutbox() throws Exception {
        CreateIntentRequest request = new CreateIntentRequest(
                "COSTA_BILL", "BILL-G4-IT-1",
                new BigDecimal("100.00"), "USD",
                facilityId.toString(), "idem-g4-it-1", null,
                "SANDBOX", null, null
        );

        mockMvc.perform(post("/mushex/v1/payment-intents")
                .header("X-Tenant-Id", tenantId.toString())
                .header("X-Actor-Id", "test-actor")
                .header("X-Actor-Type", "FACILITY_FINANCE")
                .header("X-Purpose-Of-Use", "BILLING")
                .header("X-Device-Fingerprint", "test-device")
                .header("X-Correlation-Id", correlationId.toString())
                .header("X-Facility-Id", facilityId.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.metadata", containsString("\"effective_rail\":\"SANDBOX\"")))
            .andExpect(jsonPath("$.data.metadata", containsString("\"reason\":\"EXPLICIT_PREFERRED\"")));

        var outboxEvents = outboxRepository.findTop100ByPublishedAtIsNullOrderByCreatedAtAsc();
        assertFalse(outboxEvents.isEmpty());
        String payload = outboxEvents.get(0).getPayload();
        assertTrue(payload.contains("\"effectiveRail\":\"SANDBOX\""),
                "INTENT_CREATED outbox payload must include effectiveRail");
        assertTrue(payload.contains("\"preferredRail\":\"SANDBOX\""),
                "INTENT_CREATED outbox payload must include preferredRail");
        assertTrue(payload.contains("\"railSelectionReason\":\"EXPLICIT_PREFERRED\""),
                "INTENT_CREATED outbox payload must include railSelectionReason");
    }

    @Test
    void createIntent_withUnknownRailAdapter_returns400WithStableErrorCode() throws Exception {
        CreateIntentRequest request = new CreateIntentRequest(
                "COSTA_BILL", "BILL-G4-IT-2",
                new BigDecimal("100.00"), "USD",
                facilityId.toString(), "idem-g4-it-2", null,
                "BITCOIN", null, null
        );

        mockMvc.perform(post("/mushex/v1/payment-intents")
                .header("X-Tenant-Id", tenantId.toString())
                .header("X-Actor-Id", "test-actor")
                .header("X-Actor-Type", "FACILITY_FINANCE")
                .header("X-Purpose-Of-Use", "BILLING")
                .header("X-Device-Fingerprint", "test-device")
                .header("X-Correlation-Id", correlationId.toString())
                .header("X-Facility-Id", facilityId.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.code").value("RAIL_ADAPTER_UNKNOWN"));

        assertEquals(0, intentRepository.count(),
                "A rejected rail selection must not persist any intent");
    }

    @Test
    void createIntent_withoutRailSelectionFields_remainsBackwardCompatible() throws Exception {
        // This test re-uses the legacy 7-arg constructor to confirm that callers who
        // have not yet adopted the G-4 fields still get a 201 and a valid response.
        CreateIntentRequest request = new CreateIntentRequest(
                "COSTA_BILL", "BILL-G4-IT-3",
                new BigDecimal("12.00"), "USD",
                facilityId.toString(), "idem-g4-it-3", null
        );

        mockMvc.perform(post("/mushex/v1/payment-intents")
                .header("X-Tenant-Id", tenantId.toString())
                .header("X-Actor-Id", "test-actor")
                .header("X-Actor-Type", "FACILITY_FINANCE")
                .header("X-Purpose-Of-Use", "BILLING")
                .header("X-Device-Fingerprint", "test-device")
                .header("X-Correlation-Id", correlationId.toString())
                .header("X-Facility-Id", facilityId.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.metadata", containsString("\"reason\":\"DEFAULT_NO_PREFERENCE\"")));
    }
}
