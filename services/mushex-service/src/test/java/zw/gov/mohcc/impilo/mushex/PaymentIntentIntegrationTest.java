package zw.gov.mohcc.impilo.mushex;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import zw.gov.mohcc.impilo.mushex.api.dto.CreateIntentRequest;
import zw.gov.mohcc.impilo.mushex.domain.entity.PaymentIntentEntity;
import zw.gov.mohcc.impilo.mushex.domain.enums.IntentStatus;
import zw.gov.mohcc.impilo.mushex.domain.enums.SourceType;
import zw.gov.mohcc.impilo.mushex.domain.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.mushex.domain.repository.PaymentIntentRepository;
import zw.gov.mohcc.impilo.mushex.service.UlidGenerator;
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
 * Uses {@link SpringBootTest} with an H2 in-memory database, Flyway disabled,
 * and Kafka/Redis auto-configuration excluded. Security filters are bypassed
 * via {@code addFilters = false} to test the service layer end-to-end without
 * requiring a real OAuth2 provider.
 *
 * Trust headers are sent on every request to populate the TrustContext
 * via the TrustContextFilter.
 */
@SpringBootTest(properties = {
    "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration,org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration",
    "spring.security.oauth2.resourceserver.jwt.issuer-uri="
})
@ActiveProfiles("test")
@AutoConfigureMockMvc(addFilters = false)
class PaymentIntentIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private PaymentIntentRepository intentRepository;
    @Autowired private EventOutboxRepository outboxRepository;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID facilityId = UUID.randomUUID();
    private final UUID correlationId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        // Clean up before each test
        outboxRepository.deleteAll();
        intentRepository.deleteAll();
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
            .andExpect(jsonPath("$.intentId").isNotEmpty())
            .andExpect(jsonPath("$.status").value("CREATED"))
            .andExpect(jsonPath("$.amountTotal").value(250.00))
            .andExpect(jsonPath("$.amountPaid").value(0))
            .andExpect(jsonPath("$.currency").value("USD"))
            .andExpect(jsonPath("$.sourceType").value("COSTA_BILL"))
            .andExpect(jsonPath("$.sourceId").value("BILL-IT-001"));

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
        String id1 = objectMapper.readTree(response1).get("intentId").asText();
        String id2 = objectMapper.readTree(response2).get("intentId").asText();
        assertEquals(id1, id2);

        // Only one record in DB
        assertEquals(1, intentRepository.count());
    }

    // ---------------------------------------------------------------
    // GET /mushex/v1/payment-intents/{id}
    // ---------------------------------------------------------------

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
            .andExpect(jsonPath("$.intentId").value(intent.getIntentId()))
            .andExpect(jsonPath("$.sourceType").value("MSIKA_ORDER"))
            .andExpect(jsonPath("$.sourceId").value("ORD-IT-001"))
            .andExpect(jsonPath("$.amountTotal").value(500.00))
            .andExpect(jsonPath("$.status").value("CREATED"));
    }

    @Test
    void getIntent_nonExistentId_returns404() throws Exception {
        mockMvc.perform(get("/mushex/v1/payment-intents/NONEXISTENT")
                .header("X-Tenant-Id", tenantId.toString())
                .header("X-Actor-Id", "test-actor")
                .header("X-Actor-Type", "FACILITY_FINANCE")
                .header("X-Purpose-Of-Use", "BILLING")
                .header("X-Device-Fingerprint", "test-device")
                .header("X-Correlation-Id", correlationId.toString())
                .header("X-Facility-Id", facilityId.toString()))
            .andExpect(status().isNotFound());
    }

    // ---------------------------------------------------------------
    // POST /mushex/v1/payment-intents/{id}/cancel
    // ---------------------------------------------------------------

    @Test
    void cancelIntent_fromCreated_returns200AndCancelled() throws Exception {
        // Seed a CREATED intent
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
            .andExpect(jsonPath("$.status").value("CANCELLED"));

        // Verify database state
        PaymentIntentEntity updated = intentRepository.findById(intent.getIntentId()).orElseThrow();
        assertEquals(IntentStatus.CANCELLED, updated.getStatus());
    }

    @Test
    void cancelIntent_fromPaid_returns409() throws Exception {
        // Seed a PAID intent
        PaymentIntentEntity intent = new PaymentIntentEntity();
        intent.setIntentId(UlidGenerator.generate());
        intent.setTenantId(tenantId);
        intent.setFacilityId(facilityId);
        intent.setSourceType(SourceType.COSTA_BILL);
        intent.setSourceId("BILL-IT-PAID");
        intent.setCurrency("USD");
        intent.setAmountTotal(new BigDecimal("200.00"));
        intent.setAmountPaid(new BigDecimal("200.00"));
        intent.setStatus(IntentStatus.PAID);
        intent.setIdempotencyKey("idem-cancel-paid");
        intentRepository.save(intent);

        mockMvc.perform(post("/mushex/v1/payment-intents/" + intent.getIntentId() + "/cancel")
                .header("X-Tenant-Id", tenantId.toString())
                .header("X-Actor-Id", "test-actor")
                .header("X-Actor-Type", "FACILITY_FINANCE")
                .header("X-Purpose-Of-Use", "BILLING")
                .header("X-Device-Fingerprint", "test-device")
                .header("X-Correlation-Id", correlationId.toString())
                .header("X-Facility-Id", facilityId.toString()))
            .andExpect(status().isConflict());
    }

    @Test
    void cancelIntent_nonExistent_returns404() throws Exception {
        mockMvc.perform(post("/mushex/v1/payment-intents/NONEXISTENT/cancel")
                .header("X-Tenant-Id", tenantId.toString())
                .header("X-Actor-Id", "test-actor")
                .header("X-Actor-Type", "FACILITY_FINANCE")
                .header("X-Purpose-Of-Use", "BILLING")
                .header("X-Device-Fingerprint", "test-device")
                .header("X-Correlation-Id", correlationId.toString())
                .header("X-Facility-Id", facilityId.toString()))
            .andExpect(status().isNotFound());
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
    // Validation
    // ---------------------------------------------------------------

    @Test
    void createIntent_missingRequiredFields_returns400() throws Exception {
        String invalidJson = "{}";

        mockMvc.perform(post("/mushex/v1/payment-intents")
                .header("X-Tenant-Id", tenantId.toString())
                .header("X-Actor-Id", "test-actor")
                .header("X-Actor-Type", "FACILITY_FINANCE")
                .header("X-Purpose-Of-Use", "BILLING")
                .header("X-Device-Fingerprint", "test-device")
                .header("X-Correlation-Id", correlationId.toString())
                .header("X-Facility-Id", facilityId.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidJson))
            .andExpect(status().isBadRequest());
    }

    @Test
    void createIntent_zeroAmount_returns400() throws Exception {
        CreateIntentRequest request = new CreateIntentRequest(
            "COSTA_BILL", "BILL-ZERO",
            BigDecimal.ZERO, "USD",
            facilityId.toString(), "idem-zero", null
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
            .andExpect(status().isBadRequest());
    }
}
