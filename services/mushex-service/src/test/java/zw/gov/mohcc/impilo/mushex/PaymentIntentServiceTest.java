package zw.gov.mohcc.impilo.mushex;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import zw.gov.mohcc.impilo.mushex.domain.enums.AdapterType;
import zw.gov.mohcc.impilo.mushex.domain.enums.PaymentIntentType;
import zw.gov.mohcc.impilo.mushex.domain.enums.RailSelectionReason;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.mushex.domain.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.mushex.domain.entity.PaymentIntentEntity;
import zw.gov.mohcc.impilo.mushex.domain.enums.IntentStatus;
import zw.gov.mohcc.impilo.mushex.domain.enums.SourceType;
import zw.gov.mohcc.impilo.mushex.domain.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.mushex.domain.repository.PaymentIntentRepository;
import zw.gov.mohcc.impilo.mushex.config.MushexProperties;
import zw.gov.mohcc.impilo.mushex.integration.CredentialVerificationClient;
import zw.gov.mohcc.impilo.mushex.integration.MusheWalletAdapter;
import zw.gov.mohcc.impilo.mushex.integration.ProviderContractClient;
import zw.gov.mohcc.impilo.mushex.service.PaymentIntentService;
import zw.gov.mohcc.impilo.mushex.service.ReceiptService;
import zw.gov.mohcc.impilo.mushex.service.adapter.AdapterRegistry;
import zw.gov.mohcc.impilo.mushex.service.adapter.AdapterResponse;
import zw.gov.mohcc.impilo.mushex.service.adapter.PaymentRailAdapter;
import zw.gov.mohcc.impilo.mushex.service.rail.DefaultRailSelectionPolicy;
import zw.gov.mohcc.impilo.mushex.service.rail.RailSelectionException;
import zw.gov.mohcc.impilo.mushex.service.rail.RailSelectionPolicy;
import zw.gov.mohcc.impilo.mushex.service.rail.RailSelectionRequest;
import zw.gov.mohcc.impilo.shared.auth.AccessMode;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link PaymentIntentService}.
 *
 * Validates intent creation with idempotency, state-machine transitions,
 * partial and full payment recording, receipt generation on full payment,
 * and cancellation logic.
 */
@ExtendWith(MockitoExtension.class)
class PaymentIntentServiceTest {

    @Mock private PaymentIntentRepository intentRepository;
    @Mock private EventOutboxRepository outboxRepository;
    @Mock private ReceiptService receiptService;
    @Mock private ObjectMapper objectMapper;
    @Mock private ProviderContractClient providerContractClient;
    @Mock private MusheWalletAdapter walletAdapter;

    private PaymentIntentService service;
    private MushexProperties mushexProperties;
    private RailSelectionPolicy railSelectionPolicy;

    private static final CredentialVerificationClient NOOP_CREDENTIALS =
            (tenantId, providerId) -> new CredentialVerificationClient.CredentialVerificationResult(
                    true, "VALID", "test-verification-ref", null);

    private final UUID tenantId = UUID.randomUUID();
    private final UUID facilityId = UUID.randomUUID();
    private final UUID correlationId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        lenient().when(providerContractClient.hasActiveContract(any(), any(), any())).thenReturn(true);
        mushexProperties = new MushexProperties();
        railSelectionPolicy = new DefaultRailSelectionPolicy(
                stubRegistry(AdapterType.SANDBOX, AdapterType.MOBILE_MONEY), mushexProperties);
        service = new PaymentIntentService(intentRepository, outboxRepository, receiptService, objectMapper,
                NOOP_CREDENTIALS, providerContractClient, walletAdapter, mushexProperties, railSelectionPolicy);
        TrustContextHolder.set(new TrustContext(
            tenantId, "actor-1", "FACILITY_FINANCE", "BILLING",
            "device-1", correlationId, facilityId, null, null, AccessMode.INTERNAL
        ));
    }

    private static AdapterRegistry stubRegistry(AdapterType... types) {
        return new AdapterRegistry(java.util.Arrays.stream(types)
                .map(PaymentIntentServiceTest::stubAdapter)
                .toList());
    }

    private static PaymentRailAdapter stubAdapter(AdapterType type) {
        return new PaymentRailAdapter() {
            @Override public String adapterType() { return type.name(); }
            @Override public AdapterResponse initiatePayment(String i, BigDecimal a, String c, Map<String, String> cfg) {
                return new AdapterResponse("ref", "PENDING", "", Map.of());
            }
            @Override public AdapterResponse checkStatus(String r, Map<String, String> cfg) {
                return new AdapterResponse("ref", "PENDING", "", Map.of());
            }
            @Override public boolean verifyWebhook(String s, String p, Map<String, String> cfg) { return true; }
            @Override public AdapterResponse initiateRefund(String r, BigDecimal a, Map<String, String> cfg) {
                return new AdapterResponse("ref", "PENDING", "", Map.of());
            }
        };
    }

    @AfterEach
    void tearDown() {
        TrustContextHolder.clear();
    }

    // ---------------------------------------------------------------
    // createIntent
    // ---------------------------------------------------------------

    @Test
    void createIntent_shouldCreateWithCreatedStatus() throws Exception {
        when(intentRepository.findByIdempotencyKey(any())).thenReturn(Optional.empty());
        when(intentRepository.save(any(PaymentIntentEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        PaymentIntentEntity result = service.createIntent(
            SourceType.COSTA_BILL, "BILL-001", new BigDecimal("100.00"), "USD",
            facilityId, "idem-key-001", null
        );

        assertNotNull(result);
        assertEquals(IntentStatus.CREATED, result.getStatus());
        assertEquals(new BigDecimal("100.00"), result.getAmountTotal());
        assertEquals(BigDecimal.ZERO, result.getAmountPaid());
        assertEquals(SourceType.COSTA_BILL, result.getSourceType());
        assertEquals("BILL-001", result.getSourceId());
        assertEquals("USD", result.getCurrency());
        assertEquals(tenantId, result.getTenantId());
        assertEquals(facilityId, result.getFacilityId());
        assertEquals("idem-key-001", result.getIdempotencyKey());
        assertNotNull(result.getIntentId());
        assertNotNull(result.getExpiresAt());

        verify(intentRepository).save(any(PaymentIntentEntity.class));
        verify(outboxRepository).save(any(EventOutboxEntity.class));
    }

    @Test
    void createIntent_shouldAcceptProviderCouncilFeeSourceType() throws Exception {
        when(intentRepository.findByIdempotencyKey(any())).thenReturn(Optional.empty());
        when(intentRepository.save(any(PaymentIntentEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        String metadata = "{\"provider_id\":\"VA-00000000000001\"}";
        PaymentIntentEntity result = service.createIntent(
                SourceType.PROVIDER_COUNCIL_FEE, "OBL-42", new BigDecimal("25.00"), "USD",
                facilityId, "idem-council-001", metadata);

        assertEquals(SourceType.PROVIDER_COUNCIL_FEE, result.getSourceType());
        assertEquals("OBL-42", result.getSourceId());
        assertEquals(new BigDecimal("25.00"), result.getAmountTotal());
    }

    @Test
    void createIntent_shouldSetMetadataWhenProvided() throws Exception {
        String metadata = "{\"patient\":\"P-001\"}";
        when(intentRepository.findByIdempotencyKey(any())).thenReturn(Optional.empty());
        when(intentRepository.save(any(PaymentIntentEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        PaymentIntentEntity result = service.createIntent(
            SourceType.MSIKA_ORDER, "ORD-001", new BigDecimal("50.00"), "USD",
            facilityId, "idem-key-002", metadata
        );

        assertEquals(metadata, result.getMetadata());
    }

    @Test
    void createIntent_duplicateIdempotencyKey_returnsExisting() {
        PaymentIntentEntity existing = new PaymentIntentEntity();
        existing.setIntentId("EXISTING-ID");
        existing.setIdempotencyKey("idem-key-001");
        existing.setStatus(IntentStatus.CREATED);
        existing.setAmountTotal(new BigDecimal("100.00"));
        when(intentRepository.findByIdempotencyKey("idem-key-001")).thenReturn(Optional.of(existing));

        PaymentIntentEntity result = service.createIntent(
            SourceType.COSTA_BILL, "BILL-001", new BigDecimal("100.00"), "USD",
            facilityId, "idem-key-001", null
        );

        assertEquals("EXISTING-ID", result.getIntentId());
        verify(intentRepository, never()).save(any());
        verify(outboxRepository, never()).save(any());
    }

    @Test
    void createIntent_shouldPublishOutboxEvent() throws Exception {
        when(intentRepository.findByIdempotencyKey(any())).thenReturn(Optional.empty());
        when(intentRepository.save(any(PaymentIntentEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(objectMapper.writeValueAsString(any())).thenReturn("{\"intentId\":\"test\"}");

        service.createIntent(
            SourceType.COSTA_BILL, "BILL-001", new BigDecimal("200.00"), "USD",
            facilityId, "idem-key-003", null
        );

        ArgumentCaptor<EventOutboxEntity> outboxCaptor = ArgumentCaptor.forClass(EventOutboxEntity.class);
        verify(outboxRepository).save(outboxCaptor.capture());

        EventOutboxEntity outbox = outboxCaptor.getValue();
        assertEquals("PAYMENT_INTENT", outbox.getAggregateType());
        assertEquals("INTENT_CREATED", outbox.getEventType());
        assertEquals(tenantId, outbox.getTenantId());
        assertNotNull(outbox.getPayload());
    }

    @Test
    void createIntent_parsesIntentTypeFromMetadata_intentTypeKey() throws Exception {
        ObjectMapper realMapper = new ObjectMapper();
        PaymentIntentService svc = new PaymentIntentService(intentRepository, outboxRepository, receiptService, realMapper,
                NOOP_CREDENTIALS, providerContractClient, walletAdapter, mushexProperties, railSelectionPolicy);
        when(intentRepository.findByIdempotencyKey(any())).thenReturn(Optional.empty());
        when(intentRepository.save(any(PaymentIntentEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        String metadata = "{\"intent_type\":\"DEPOSIT\",\"provider_id\":\"VA-00000000000001\"}";
        PaymentIntentEntity result = svc.createIntent(
                SourceType.COSTA_BILL, "BILL-DEP", new BigDecimal("50.00"), "USD",
                facilityId, "idem-deposit-1", metadata);

        assertEquals(PaymentIntentType.DEPOSIT, result.getIntentType());
    }

    @Test
    void createIntent_parsesIntentTypeFromMetadata_paymentIntentTypeAlias() throws Exception {
        ObjectMapper realMapper = new ObjectMapper();
        PaymentIntentService svc = new PaymentIntentService(intentRepository, outboxRepository, receiptService, realMapper,
                NOOP_CREDENTIALS, providerContractClient, walletAdapter, mushexProperties, railSelectionPolicy);
        when(intentRepository.findByIdempotencyKey(any())).thenReturn(Optional.empty());
        when(intentRepository.save(any(PaymentIntentEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        String metadata = "{\"payment_intent_type\":\"PRE_SERVICE_PAYMENT\",\"provider_id\":\"VA-00000000000001\"}";
        PaymentIntentEntity result = svc.createIntent(
                SourceType.COSTA_BILL, "BILL-PRE", new BigDecimal("30.00"), "USD",
                facilityId, "idem-pre-1", metadata);

        assertEquals(PaymentIntentType.PRE_SERVICE_PAYMENT, result.getIntentType());
    }

    // ---------------------------------------------------------------
    // createIntent — rail selection (G-4)
    //
    // See docs/design/g4-rail-selection-policy.md §13. The pure-policy cases live
    // in DefaultRailSelectionPolicyTest; the cases below assert that the policy is
    // actually invoked from the service, that the result is merged into
    // payment_intents.metadata, that the outbox event carries the three new keys,
    // and that idempotency replay does not re-run selection.
    // ---------------------------------------------------------------

    @Test
    void createIntent_recordsRailSelectionInMetadata_andOutbox_explicitPreferred() throws Exception {
        ObjectMapper realMapper = new ObjectMapper();
        PaymentIntentService svc = new PaymentIntentService(intentRepository, outboxRepository, receiptService, realMapper,
                NOOP_CREDENTIALS, providerContractClient, walletAdapter, mushexProperties, railSelectionPolicy);
        when(intentRepository.findByIdempotencyKey(any())).thenReturn(Optional.empty());
        when(intentRepository.save(any(PaymentIntentEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        RailSelectionRequest selection = new RailSelectionRequest(
                "MOBILE_MONEY", null, null,
                SourceType.COSTA_BILL, new BigDecimal("75.00"), "USD", facilityId, false);

        PaymentIntentEntity result = svc.createIntent(
                SourceType.COSTA_BILL, "BILL-G4-1", new BigDecimal("75.00"), "USD",
                facilityId, "idem-g4-1", "{\"patient\":\"P-001\"}", selection);

        JsonNode mergedMeta = realMapper.readTree(result.getMetadata());
        assertEquals("P-001", mergedMeta.get("patient").asText(),
                "Existing metadata keys must survive the rail_selection merge");
        JsonNode rs = mergedMeta.get("rail_selection");
        assertNotNull(rs, "metadata.rail_selection must be present after createIntent");
        assertEquals("MOBILE_MONEY", rs.get("effective_rail").asText());
        assertEquals("MOBILE_MONEY", rs.get("preferred_rail").asText());
        assertFalse(rs.get("fallback_applied").asBoolean());
        assertEquals(RailSelectionReason.EXPLICIT_PREFERRED.name(), rs.get("reason").asText());
        assertEquals("1", rs.get("selection_version").asText());

        ArgumentCaptor<EventOutboxEntity> outboxCaptor = ArgumentCaptor.forClass(EventOutboxEntity.class);
        verify(outboxRepository).save(outboxCaptor.capture());
        String payload = outboxCaptor.getValue().getPayload();
        JsonNode parsed = realMapper.readTree(payload);
        assertEquals("MOBILE_MONEY", parsed.get("effectiveRail").asText());
        assertEquals("MOBILE_MONEY", parsed.get("preferredRail").asText());
        assertEquals(RailSelectionReason.EXPLICIT_PREFERRED.name(), parsed.get("railSelectionReason").asText());
    }

    @Test
    void createIntent_recordsFallback_whenPreferredUnregistered_andFallbackAllowed() throws Exception {
        ObjectMapper realMapper = new ObjectMapper();
        RailSelectionPolicy sandboxOnly = new DefaultRailSelectionPolicy(
                stubRegistry(AdapterType.SANDBOX), mushexProperties);
        PaymentIntentService svc = new PaymentIntentService(intentRepository, outboxRepository, receiptService, realMapper,
                NOOP_CREDENTIALS, providerContractClient, walletAdapter, mushexProperties, sandboxOnly);
        when(intentRepository.findByIdempotencyKey(any())).thenReturn(Optional.empty());
        when(intentRepository.save(any(PaymentIntentEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        RailSelectionRequest selection = new RailSelectionRequest(
                "CARD_GATEWAY", true, null,
                SourceType.COSTA_BILL, new BigDecimal("50.00"), "USD", facilityId, false);

        PaymentIntentEntity result = svc.createIntent(
                SourceType.COSTA_BILL, "BILL-G4-2", new BigDecimal("50.00"), "USD",
                facilityId, "idem-g4-2", null, selection);

        JsonNode rs = realMapper.readTree(result.getMetadata()).get("rail_selection");
        assertEquals("SANDBOX", rs.get("effective_rail").asText());
        assertEquals("CARD_GATEWAY", rs.get("preferred_rail").asText());
        assertTrue(rs.get("fallback_applied").asBoolean());
        assertEquals(RailSelectionReason.PREFERRED_UNAVAILABLE_FALLBACK.name(),
                rs.get("reason").asText());
    }

    @Test
    void createIntent_rejects_whenPreferredUnregistered_andFallbackDisallowed() {
        ObjectMapper realMapper = new ObjectMapper();
        RailSelectionPolicy sandboxOnly = new DefaultRailSelectionPolicy(
                stubRegistry(AdapterType.SANDBOX), mushexProperties);
        PaymentIntentService svc = new PaymentIntentService(intentRepository, outboxRepository, receiptService, realMapper,
                NOOP_CREDENTIALS, providerContractClient, walletAdapter, mushexProperties, sandboxOnly);
        when(intentRepository.findByIdempotencyKey(any())).thenReturn(Optional.empty());

        RailSelectionRequest selection = new RailSelectionRequest(
                "CARD_GATEWAY", false, null,
                SourceType.COSTA_BILL, new BigDecimal("50.00"), "USD", facilityId, false);

        RailSelectionException ex = assertThrows(RailSelectionException.class,
                () -> svc.createIntent(SourceType.COSTA_BILL, "BILL-G4-3", new BigDecimal("50.00"), "USD",
                        facilityId, "idem-g4-3", null, selection));
        assertEquals("RAIL_ADAPTER_UNAVAILABLE", ex.getErrorCode());

        verify(intentRepository, never()).save(any());
        verify(outboxRepository, never()).save(any());
    }

    @Test
    void createIntent_rejects_unknownRailString() {
        ObjectMapper realMapper = new ObjectMapper();
        PaymentIntentService svc = new PaymentIntentService(intentRepository, outboxRepository, receiptService, realMapper,
                NOOP_CREDENTIALS, providerContractClient, walletAdapter, mushexProperties, railSelectionPolicy);
        when(intentRepository.findByIdempotencyKey(any())).thenReturn(Optional.empty());

        RailSelectionRequest selection = new RailSelectionRequest(
                "BITCOIN", null, null,
                SourceType.COSTA_BILL, new BigDecimal("50.00"), "USD", facilityId, false);

        RailSelectionException ex = assertThrows(RailSelectionException.class,
                () -> svc.createIntent(SourceType.COSTA_BILL, "BILL-G4-4", new BigDecimal("50.00"), "USD",
                        facilityId, "idem-g4-4", null, selection));
        assertEquals("RAIL_ADAPTER_UNKNOWN", ex.getErrorCode());

        verify(intentRepository, never()).save(any());
        verify(outboxRepository, never()).save(any());
    }

    @Test
    void createIntent_legacy7ArgOverload_recordsDefaultNoPreference() throws Exception {
        ObjectMapper realMapper = new ObjectMapper();
        PaymentIntentService svc = new PaymentIntentService(intentRepository, outboxRepository, receiptService, realMapper,
                NOOP_CREDENTIALS, providerContractClient, walletAdapter, mushexProperties, railSelectionPolicy);
        when(intentRepository.findByIdempotencyKey(any())).thenReturn(Optional.empty());
        when(intentRepository.save(any(PaymentIntentEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        PaymentIntentEntity result = svc.createIntent(
                SourceType.COSTA_BILL, "BILL-G4-5", new BigDecimal("20.00"), "USD",
                facilityId, "idem-g4-5", null);

        JsonNode rs = realMapper.readTree(result.getMetadata()).get("rail_selection");
        assertEquals("SANDBOX", rs.get("effective_rail").asText());
        assertEquals(RailSelectionReason.DEFAULT_NO_PREFERENCE.name(), rs.get("reason").asText());
        assertFalse(rs.get("direct_gateway_requested").asBoolean());

        ArgumentCaptor<EventOutboxEntity> outboxCaptor = ArgumentCaptor.forClass(EventOutboxEntity.class);
        verify(outboxRepository).save(outboxCaptor.capture());
        JsonNode parsed = realMapper.readTree(outboxCaptor.getValue().getPayload());
        assertEquals("SANDBOX", parsed.get("effectiveRail").asText());
        assertEquals("NONE", parsed.get("preferredRail").asText());
    }

    @Test
    void createIntent_impiloSimulationFlag_forcesSandboxRegardlessOfPreference() throws Exception {
        ObjectMapper realMapper = new ObjectMapper();
        PaymentIntentService svc = new PaymentIntentService(intentRepository, outboxRepository, receiptService, realMapper,
                NOOP_CREDENTIALS, providerContractClient, walletAdapter, mushexProperties, railSelectionPolicy);
        when(intentRepository.findByIdempotencyKey(any())).thenReturn(Optional.empty());
        when(intentRepository.save(any(PaymentIntentEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        RailSelectionRequest selection = new RailSelectionRequest(
                "MOBILE_MONEY", null, true,
                SourceType.COSTA_BILL, new BigDecimal("10.00"), "USD", facilityId, false);

        PaymentIntentEntity result = svc.createIntent(
                SourceType.COSTA_BILL, "BILL-G4-6", new BigDecimal("10.00"), "USD",
                facilityId, "idem-g4-6", "{\"impilo_simulation\":true}", selection);

        JsonNode rs = realMapper.readTree(result.getMetadata()).get("rail_selection");
        assertEquals("SANDBOX", rs.get("effective_rail").asText());
        assertEquals(RailSelectionReason.SAFETY_SWITCH_FORCED_SANDBOX.name(),
                rs.get("reason").asText());
    }

    @Test
    void createIntent_idempotencyReplay_returnsExisting_withoutRerunningSelection() {
        ObjectMapper realMapper = new ObjectMapper();
        PaymentIntentService svc = new PaymentIntentService(intentRepository, outboxRepository, receiptService, realMapper,
                NOOP_CREDENTIALS, providerContractClient, walletAdapter, mushexProperties, railSelectionPolicy);

        PaymentIntentEntity existing = new PaymentIntentEntity();
        existing.setIntentId("EXISTING-G4");
        existing.setIdempotencyKey("idem-g4-replay");
        existing.setStatus(IntentStatus.CREATED);
        existing.setAmountTotal(new BigDecimal("99.00"));
        existing.setMetadata("{\"rail_selection\":{\"effective_rail\":\"MOBILE_MONEY\"}}");
        when(intentRepository.findByIdempotencyKey("idem-g4-replay")).thenReturn(Optional.of(existing));

        RailSelectionRequest secondSelection = new RailSelectionRequest(
                "CARD_GATEWAY", false, null,
                SourceType.COSTA_BILL, new BigDecimal("99.00"), "USD", facilityId, false);

        PaymentIntentEntity result = svc.createIntent(
                SourceType.COSTA_BILL, "BILL-G4-REPLAY", new BigDecimal("99.00"), "USD",
                facilityId, "idem-g4-replay", "{\"different\":\"metadata\"}", secondSelection);

        assertEquals("EXISTING-G4", result.getIntentId());
        assertEquals("{\"rail_selection\":{\"effective_rail\":\"MOBILE_MONEY\"}}", result.getMetadata(),
                "Idempotent replay must return the original intent with its original metadata");
        verify(intentRepository, never()).save(any());
        verify(outboxRepository, never()).save(any());
    }

    @Test
    void createIntent_outboxKeysAreStable_evenWithoutCallerSuppliedPreference() throws Exception {
        ObjectMapper realMapper = new ObjectMapper();
        PaymentIntentService svc = new PaymentIntentService(intentRepository, outboxRepository, receiptService, realMapper,
                NOOP_CREDENTIALS, providerContractClient, walletAdapter, mushexProperties, railSelectionPolicy);
        when(intentRepository.findByIdempotencyKey(any())).thenReturn(Optional.empty());
        when(intentRepository.save(any(PaymentIntentEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        svc.createIntent(SourceType.COSTA_BILL, "BILL-G4-7", new BigDecimal("5.00"), "USD",
                facilityId, "idem-g4-7", null);

        ArgumentCaptor<EventOutboxEntity> outboxCaptor = ArgumentCaptor.forClass(EventOutboxEntity.class);
        verify(outboxRepository).save(outboxCaptor.capture());
        JsonNode parsed = realMapper.readTree(outboxCaptor.getValue().getPayload());
        List<String> requiredKeys = List.of(
                "intentId", "sourceType", "sourceId", "amount", "currency",
                "facilityId", "status", "effectiveRail", "preferredRail", "railSelectionReason");
        for (String key : requiredKeys) {
            assertTrue(parsed.has(key), "Outbox payload must include key " + key);
        }
    }

    // ---------------------------------------------------------------
    // cancelIntent
    // ---------------------------------------------------------------

    @Test
    void cancelIntent_fromCreated_succeeds() throws Exception {
        PaymentIntentEntity intent = buildIntent("INT-001", IntentStatus.CREATED, "100.00", "0.00");
        when(intentRepository.findById("INT-001")).thenReturn(Optional.of(intent));
        when(intentRepository.save(any(PaymentIntentEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        PaymentIntentEntity result = service.cancelIntent("INT-001");

        assertEquals(IntentStatus.CANCELLED, result.getStatus());
        verify(intentRepository).save(intent);
        verify(outboxRepository).save(any(EventOutboxEntity.class));
    }

    @Test
    void cancelIntent_fromPending_succeeds() throws Exception {
        PaymentIntentEntity intent = buildIntent("INT-002", IntentStatus.PENDING, "100.00", "0.00");
        when(intentRepository.findById("INT-002")).thenReturn(Optional.of(intent));
        when(intentRepository.save(any(PaymentIntentEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        PaymentIntentEntity result = service.cancelIntent("INT-002");

        assertEquals(IntentStatus.CANCELLED, result.getStatus());
    }

    @Test
    void cancelIntent_fromPaid_throws() {
        PaymentIntentEntity intent = buildIntent("INT-003", IntentStatus.PAID, "100.00", "100.00");
        when(intentRepository.findById("INT-003")).thenReturn(Optional.of(intent));

        assertThrows(IllegalStateException.class, () -> service.cancelIntent("INT-003"));
        verify(intentRepository, never()).save(any());
    }

    @Test
    void cancelIntent_fromRefunded_throws() {
        PaymentIntentEntity intent = buildIntent("INT-004", IntentStatus.REFUNDED, "100.00", "0.00");
        when(intentRepository.findById("INT-004")).thenReturn(Optional.of(intent));

        assertThrows(IllegalStateException.class, () -> service.cancelIntent("INT-004"));
    }

    @Test
    void cancelIntent_fromAuthorized_throws() {
        // cancelIntent only allows CREATED and PENDING (not AUTHORIZED)
        PaymentIntentEntity intent = buildIntent("INT-005", IntentStatus.AUTHORIZED, "100.00", "0.00");
        when(intentRepository.findById("INT-005")).thenReturn(Optional.of(intent));

        assertThrows(IllegalStateException.class, () -> service.cancelIntent("INT-005"));
    }

    @Test
    void cancelIntent_notFound_throws() {
        when(intentRepository.findById("INT-MISSING")).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> service.cancelIntent("INT-MISSING"));
    }

    // ---------------------------------------------------------------
    // recordPayment
    // ---------------------------------------------------------------

    @Test
    void recordPayment_fullAmount_transitionsToPaid() throws Exception {
        PaymentIntentEntity intent = buildIntent("INT-010", IntentStatus.PENDING, "100.00", "0.00");
        when(intentRepository.findById("INT-010")).thenReturn(Optional.of(intent));
        when(intentRepository.save(any(PaymentIntentEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        PaymentIntentEntity result = service.recordPayment("INT-010", new BigDecimal("100.00"));

        assertEquals(new BigDecimal("100.00"), result.getAmountPaid());
        assertEquals(IntentStatus.PAID, result.getStatus());
        verify(receiptService).generateReceipt("INT-010");
    }

    @Test
    void recordPayment_partialAmount_staysSameStatus() throws Exception {
        PaymentIntentEntity intent = buildIntent("INT-011", IntentStatus.PENDING, "100.00", "0.00");
        when(intentRepository.findById("INT-011")).thenReturn(Optional.of(intent));
        when(intentRepository.save(any(PaymentIntentEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        PaymentIntentEntity result = service.recordPayment("INT-011", new BigDecimal("50.00"));

        assertEquals(new BigDecimal("50.00"), result.getAmountPaid());
        assertEquals(IntentStatus.PENDING, result.getStatus());
        verify(receiptService, never()).generateReceipt(any());
    }

    @Test
    void recordPayment_accumulatedAmount_transitionsToPaid() throws Exception {
        PaymentIntentEntity intent = buildIntent("INT-012", IntentStatus.PENDING, "100.00", "60.00");
        when(intentRepository.findById("INT-012")).thenReturn(Optional.of(intent));
        when(intentRepository.save(any(PaymentIntentEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        PaymentIntentEntity result = service.recordPayment("INT-012", new BigDecimal("40.00"));

        assertEquals(new BigDecimal("100.00"), result.getAmountPaid());
        assertEquals(IntentStatus.PAID, result.getStatus());
        verify(receiptService).generateReceipt("INT-012");
    }

    @Test
    void recordPayment_fromCreated_succeeds() throws Exception {
        PaymentIntentEntity intent = buildIntent("INT-013", IntentStatus.CREATED, "100.00", "0.00");
        when(intentRepository.findById("INT-013")).thenReturn(Optional.of(intent));
        when(intentRepository.save(any(PaymentIntentEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        PaymentIntentEntity result = service.recordPayment("INT-013", new BigDecimal("100.00"));

        assertEquals(IntentStatus.PAID, result.getStatus());
    }

    @Test
    void recordPayment_fromCancelled_throws() {
        PaymentIntentEntity intent = buildIntent("INT-014", IntentStatus.CANCELLED, "100.00", "0.00");
        when(intentRepository.findById("INT-014")).thenReturn(Optional.of(intent));

        assertThrows(IllegalStateException.class,
            () -> service.recordPayment("INT-014", new BigDecimal("100.00")));
    }

    @Test
    void recordPayment_publishesOutboxEvent() throws Exception {
        PaymentIntentEntity intent = buildIntent("INT-016", IntentStatus.PENDING, "100.00", "0.00");
        when(intentRepository.findById("INT-016")).thenReturn(Optional.of(intent));
        when(intentRepository.save(any(PaymentIntentEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        service.recordPayment("INT-016", new BigDecimal("100.00"));

        ArgumentCaptor<EventOutboxEntity> outboxCaptor = ArgumentCaptor.forClass(EventOutboxEntity.class);
        verify(outboxRepository).save(outboxCaptor.capture());
        assertEquals("STATUS_CHANGED", outboxCaptor.getValue().getEventType());
    }

    // ---------------------------------------------------------------
    // transitionStatus (state machine)
    // ---------------------------------------------------------------

    @Test
    void transitionStatus_createdToPending_succeeds() throws Exception {
        PaymentIntentEntity intent = buildIntent("INT-020", IntentStatus.CREATED, "100.00", "0.00");
        when(intentRepository.findById("INT-020")).thenReturn(Optional.of(intent));
        when(intentRepository.save(any(PaymentIntentEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        PaymentIntentEntity result = service.transitionStatus("INT-020", IntentStatus.PENDING);

        assertEquals(IntentStatus.PENDING, result.getStatus());
    }

    @Test
    void transitionStatus_pendingToAuthorized_succeeds() throws Exception {
        PaymentIntentEntity intent = buildIntent("INT-021", IntentStatus.PENDING, "100.00", "0.00");
        when(intentRepository.findById("INT-021")).thenReturn(Optional.of(intent));
        when(intentRepository.save(any(PaymentIntentEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        PaymentIntentEntity result = service.transitionStatus("INT-021", IntentStatus.AUTHORIZED);

        assertEquals(IntentStatus.AUTHORIZED, result.getStatus());
    }

    @Test
    void transitionStatus_paidToRefundPending_succeeds() throws Exception {
        PaymentIntentEntity intent = buildIntent("INT-022", IntentStatus.PAID, "100.00", "100.00");
        when(intentRepository.findById("INT-022")).thenReturn(Optional.of(intent));
        when(intentRepository.save(any(PaymentIntentEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        PaymentIntentEntity result = service.transitionStatus("INT-022", IntentStatus.REFUND_PENDING);

        assertEquals(IntentStatus.REFUND_PENDING, result.getStatus());
    }

    @Test
    void transitionStatus_cancelledToAnything_throws() {
        PaymentIntentEntity intent = buildIntent("INT-023", IntentStatus.CANCELLED, "100.00", "0.00");
        when(intentRepository.findById("INT-023")).thenReturn(Optional.of(intent));

        assertThrows(IllegalStateException.class,
            () -> service.transitionStatus("INT-023", IntentStatus.PAID));
    }

    @Test
    void transitionStatus_refundedToAnything_throws() {
        PaymentIntentEntity intent = buildIntent("INT-024", IntentStatus.REFUNDED, "100.00", "0.00");
        when(intentRepository.findById("INT-024")).thenReturn(Optional.of(intent));

        assertThrows(IllegalStateException.class,
            () -> service.transitionStatus("INT-024", IntentStatus.PENDING));
    }

    @Test
    void transitionStatus_createdToPaid_throws() {
        // CREATED can only go to PENDING or CANCELLED
        PaymentIntentEntity intent = buildIntent("INT-025", IntentStatus.CREATED, "100.00", "0.00");
        when(intentRepository.findById("INT-025")).thenReturn(Optional.of(intent));

        assertThrows(IllegalStateException.class,
            () -> service.transitionStatus("INT-025", IntentStatus.PAID));
    }

    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------

    private PaymentIntentEntity buildIntent(String id, IntentStatus status,
                                            String total, String paid) {
        PaymentIntentEntity intent = new PaymentIntentEntity();
        intent.setIntentId(id);
        intent.setTenantId(tenantId);
        intent.setFacilityId(facilityId);
        intent.setStatus(status);
        intent.setAmountTotal(new BigDecimal(total));
        intent.setAmountPaid(new BigDecimal(paid));
        intent.setCurrency("USD");
        intent.setSourceType(SourceType.COSTA_BILL);
        intent.setSourceId("BILL-" + id);
        return intent;
    }
}
