package zw.gov.mohcc.impilo.mushex;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.mushex.config.MushexProperties;
import zw.gov.mohcc.impilo.mushex.domain.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.mushex.domain.entity.PaymentAttemptEntity;
import zw.gov.mohcc.impilo.mushex.domain.entity.PaymentIntentEntity;
import zw.gov.mohcc.impilo.mushex.domain.enums.AdapterType;
import zw.gov.mohcc.impilo.mushex.domain.enums.AttemptStatus;
import zw.gov.mohcc.impilo.mushex.domain.enums.IntentStatus;
import zw.gov.mohcc.impilo.mushex.domain.enums.SourceType;
import zw.gov.mohcc.impilo.mushex.domain.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.mushex.domain.repository.PaymentAttemptRepository;
import zw.gov.mohcc.impilo.mushex.service.PaymentAttemptService;
import zw.gov.mohcc.impilo.mushex.service.PaymentIntentService;
import zw.gov.mohcc.impilo.mushex.service.adapter.AdapterRegistry;
import zw.gov.mohcc.impilo.mushex.service.adapter.AdapterResponse;
import zw.gov.mohcc.impilo.mushex.service.adapter.PaymentRailAdapter;
import zw.gov.mohcc.impilo.mushex.service.rail.RailEnforcement;
import zw.gov.mohcc.impilo.mushex.service.rail.RailEnforcementException;
import zw.gov.mohcc.impilo.shared.auth.AccessMode;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link PaymentAttemptService} covering the test matrix from
 * {@code docs/design/phase-3-attempt-time-rail-enforcement-implementation.md} §9.
 *
 * <p>Uses real {@link RailEnforcement} and a real {@link AdapterRegistry} composed of
 * counting stubs so adapter-call assertions are robust; mocks {@link PaymentIntentService}
 * and the repositories.
 */
@ExtendWith(MockitoExtension.class)
class PaymentAttemptServiceTest {

    @Mock private PaymentIntentService paymentIntentService;
    @Mock private PaymentAttemptRepository attemptRepository;
    @Mock private EventOutboxRepository outboxRepository;

    private MushexProperties properties;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final UUID tenantId = UUID.randomUUID();
    private final UUID facilityId = UUID.randomUUID();
    private final UUID correlationId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        properties = new MushexProperties();
        TrustContextHolder.set(new TrustContext(
                tenantId, "actor-1", "FACILITY_FINANCE", "BILLING",
                "device-1", correlationId, facilityId, null, null, AccessMode.INTERNAL
        ));
    }

    @AfterEach
    void tearDown() {
        TrustContextHolder.clear();
    }

    private PaymentAttemptService buildService(AdapterRegistry registry) {
        RailEnforcement enforcement = new RailEnforcement(objectMapper);
        return new PaymentAttemptService(
                paymentIntentService, attemptRepository, outboxRepository,
                registry, enforcement, properties, objectMapper);
    }

    private CountingAdapter sandbox(String status) {
        return new CountingAdapter(AdapterType.SANDBOX, status);
    }

    private CountingAdapter momo(String status) {
        return new CountingAdapter(AdapterType.MOBILE_MONEY, status);
    }

    private AdapterRegistry registryOf(PaymentRailAdapter... adapters) {
        return new AdapterRegistry(List.of(adapters));
    }

    private PaymentIntentEntity intent(IntentStatus status, String metadata) {
        PaymentIntentEntity intent = new PaymentIntentEntity();
        intent.setIntentId("01INTENT");
        intent.setTenantId(tenantId);
        intent.setFacilityId(facilityId);
        intent.setSourceType(SourceType.COSTA_BILL);
        intent.setSourceId("source-1");
        intent.setAmountTotal(new BigDecimal("100.00"));
        intent.setAmountPaid(BigDecimal.ZERO);
        intent.setCurrency("USD");
        intent.setStatus(status);
        intent.setMetadata(metadata);
        return intent;
    }

    /** Adapter that records each call so the test can assert it was (or was not) called. */
    static final class CountingAdapter implements PaymentRailAdapter {
        private final AdapterType type;
        private final String returnStatus;
        private final boolean liveCapable;
        final AtomicInteger initiatePaymentCalls = new AtomicInteger(0);

        CountingAdapter(AdapterType type, String returnStatus) {
            this(type, returnStatus, false);
        }

        CountingAdapter(AdapterType type, String returnStatus, boolean liveCapable) {
            this.type = type;
            this.returnStatus = returnStatus;
            this.liveCapable = liveCapable;
        }

        @Override public String adapterType() { return type.name(); }

        @Override public boolean liveCapable() { return liveCapable; }

        @Override
        public AdapterResponse initiatePayment(String intentId, BigDecimal amount, String currency,
                                               Map<String, String> config) {
            initiatePaymentCalls.incrementAndGet();
            return new AdapterResponse(
                    type.name() + "-REF-" + initiatePaymentCalls.get(),
                    returnStatus,
                    "ok",
                    Map.of("intentId", intentId, "channel", type.name()));
        }

        @Override
        public AdapterResponse checkStatus(String adapterRef, Map<String, String> config) {
            return new AdapterResponse(adapterRef, returnStatus, "", Map.of());
        }

        @Override public boolean verifyWebhook(String s, String p, Map<String, String> c) { return true; }

        @Override
        public AdapterResponse initiateRefund(String adapterRef, BigDecimal amount, Map<String, String> c) {
            return new AdapterResponse(adapterRef + "-REFUND", "PENDING", "", Map.of());
        }
    }

    private CountingAdapter liveMomo(String status) {
        return new CountingAdapter(AdapterType.MOBILE_MONEY, status, true);
    }

    @Test
    void sandboxRailHappyPathCallsAdapterRecordsPaymentAndEmitsOutbox() {
        CountingAdapter sb = sandbox("SUCCESS");
        AdapterRegistry registry = registryOf(sb);
        PaymentIntentEntity i = intent(IntentStatus.PENDING,
                "{\"rail_selection\":{\"effective_rail\":\"SANDBOX\"}}");

        when(paymentIntentService.getIntent("01INTENT")).thenReturn(i);
        when(attemptRepository.save(any(PaymentAttemptEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        PaymentAttemptEntity attempt = buildService(registry).initiateAttempt("01INTENT", "idem-1");

        assertEquals(1, sb.initiatePaymentCalls.get());
        assertEquals(AttemptStatus.SUCCEEDED, attempt.getStatus());
        assertEquals(AdapterType.SANDBOX, attempt.getAdapterType());
        assertEquals("idem-1", attempt.getIdempotencyKey());
        assertNotNull(attempt.getAdapterRef());
        assertNotNull(attempt.getEnforcementMetadata());
        assertTrue(attempt.getEnforcementMetadata().contains("\"safety_gate\":\"OK\""));
        verify(paymentIntentService).recordPayment("01INTENT", new BigDecimal("100.00"));

        ArgumentCaptor<EventOutboxEntity> outbox = ArgumentCaptor.forClass(EventOutboxEntity.class);
        verify(outboxRepository).save(outbox.capture());
        assertEquals("ATTEMPT_INITIATED", outbox.getValue().getEventType());
        assertTrue(outbox.getValue().getPayload().contains("\"effectiveRail\":\"SANDBOX\""));
        assertTrue(outbox.getValue().getPayload().contains("\"safetyGate\":\"OK\""));
    }

    @Test
    void realRailWithSafetyGateOffPersistsAttemptWithoutAdapterCall() {
        CountingAdapter mm = momo("PENDING");
        AdapterRegistry registry = registryOf(sandbox("SUCCESS"), mm);
        PaymentIntentEntity i = intent(IntentStatus.PENDING,
                "{\"rail_selection\":{\"effective_rail\":\"MOBILE_MONEY\"}}");

        when(paymentIntentService.getIntent("01INTENT")).thenReturn(i);
        when(attemptRepository.save(any(PaymentAttemptEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        PaymentAttemptEntity attempt = buildService(registry).initiateAttempt("01INTENT", null);

        assertEquals(0, mm.initiatePaymentCalls.get());
        assertEquals(AttemptStatus.INITIATED, attempt.getStatus());
        assertEquals(AdapterType.MOBILE_MONEY, attempt.getAdapterType());
        assertNull(attempt.getAdapterRef());
        assertNull(attempt.getRawSummary());
        assertTrue(attempt.getEnforcementMetadata().contains("\"safety_gate\":\"BLOCKED_PRE_LIVE\""));
        verify(paymentIntentService, never()).recordPayment(anyString(), any());

        ArgumentCaptor<EventOutboxEntity> outbox = ArgumentCaptor.forClass(EventOutboxEntity.class);
        verify(outboxRepository).save(outbox.capture());
        assertEquals("ATTEMPT_INITIATED", outbox.getValue().getEventType());
        assertTrue(outbox.getValue().getPayload().contains("\"safetyGate\":\"BLOCKED_PRE_LIVE\""));
    }

    @Test
    void realRailWithBothFlagsOnButNotLiveCapableIsStillBlocked() {
        CountingAdapter mm = momo("PENDING"); // liveCapable defaults to false
        AdapterRegistry registry = registryOf(sandbox("SUCCESS"), mm);
        properties.getAdapters().getMobileMoney().setEnabled(true);
        properties.getAdapters().getMobileMoney().setCredentialsConfigured(true);

        PaymentIntentEntity i = intent(IntentStatus.PENDING,
                "{\"rail_selection\":{\"effective_rail\":\"MOBILE_MONEY\"}}");

        when(paymentIntentService.getIntent("01INTENT")).thenReturn(i);
        when(attemptRepository.save(any(PaymentAttemptEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        PaymentAttemptEntity attempt = buildService(registry).initiateAttempt("01INTENT", null);

        assertEquals(0, mm.initiatePaymentCalls.get());
        assertEquals(AttemptStatus.INITIATED, attempt.getStatus());
        assertEquals(AdapterType.MOBILE_MONEY, attempt.getAdapterType());
        assertNull(attempt.getAdapterRef());
        assertTrue(attempt.getEnforcementMetadata().contains("\"safety_gate\":\"BLOCKED_NOT_LIVE_CAPABLE\""));
        verify(paymentIntentService, never()).recordPayment(anyString(), any());
    }

    @Test
    void realRailWithBothFlagsAndLiveCapableAdapterCallsAdapter() {
        CountingAdapter mm = liveMomo("PENDING");
        AdapterRegistry registry = registryOf(sandbox("SUCCESS"), mm);
        properties.getAdapters().getMobileMoney().setEnabled(true);
        properties.getAdapters().getMobileMoney().setCredentialsConfigured(true);

        PaymentIntentEntity i = intent(IntentStatus.PENDING,
                "{\"rail_selection\":{\"effective_rail\":\"MOBILE_MONEY\"}}");

        when(paymentIntentService.getIntent("01INTENT")).thenReturn(i);
        when(attemptRepository.save(any(PaymentAttemptEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        PaymentAttemptEntity attempt = buildService(registry).initiateAttempt("01INTENT", null);

        assertEquals(1, mm.initiatePaymentCalls.get());
        assertEquals(AttemptStatus.PROCESSING, attempt.getStatus());
        assertEquals(AdapterType.MOBILE_MONEY, attempt.getAdapterType());
        assertNotNull(attempt.getAdapterRef());
        assertNotNull(attempt.getRawSummary());
        assertTrue(attempt.getEnforcementMetadata().contains("\"safety_gate\":\"OK\""));
        verify(paymentIntentService, never()).recordPayment(anyString(), any());
    }

    @Test
    void idempotencyReplayReturnsExistingAttemptWithNoSideEffects() {
        CountingAdapter sb = sandbox("SUCCESS");
        AdapterRegistry registry = registryOf(sb);
        PaymentAttemptEntity existing = new PaymentAttemptEntity();
        existing.setId("PREVIOUS-01");
        existing.setIntentId("01INTENT");
        existing.setIdempotencyKey("idem-1");

        when(paymentIntentService.getIntent("01INTENT"))
                .thenReturn(intent(IntentStatus.PENDING,
                        "{\"rail_selection\":{\"effective_rail\":\"SANDBOX\"}}"));
        when(attemptRepository.findByIntentIdAndIdempotencyKey("01INTENT", "idem-1"))
                .thenReturn(Optional.of(existing));

        PaymentAttemptEntity result = buildService(registry).initiateAttempt("01INTENT", "idem-1");

        assertSame(existing, result);
        assertEquals(0, sb.initiatePaymentCalls.get());
        verify(attemptRepository, never()).save(any());
        verify(outboxRepository, never()).save(any());
        verify(paymentIntentService, never()).recordPayment(anyString(), any());
    }

    @Test
    void intentInTerminalStatusReturnsAttemptNotPayableAndEmitsFailedEvent() {
        AdapterRegistry registry = registryOf(sandbox("SUCCESS"));
        PaymentIntentEntity i = intent(IntentStatus.CANCELLED,
                "{\"rail_selection\":{\"effective_rail\":\"SANDBOX\"}}");
        when(paymentIntentService.getIntent("01INTENT")).thenReturn(i);

        PaymentAttemptService service = buildService(registry);

        assertThrows(PaymentAttemptService.AttemptNotPayableException.class,
                () -> service.initiateAttempt("01INTENT", "idem-1"));

        ArgumentCaptor<EventOutboxEntity> outbox = ArgumentCaptor.forClass(EventOutboxEntity.class);
        verify(outboxRepository).save(outbox.capture());
        assertEquals("ATTEMPT_FAILED_PRE_INITIATION", outbox.getValue().getEventType());
        assertTrue(outbox.getValue().getPayload().contains("INTENT_NOT_PAYABLE"));
        verify(attemptRepository, never()).save(any());
    }

    @Test
    void legacyIntentWithoutRailMetadataUsesConfigDefault() {
        CountingAdapter sb = sandbox("SUCCESS");
        AdapterRegistry registry = registryOf(sb);
        PaymentIntentEntity i = intent(IntentStatus.PENDING, null);

        when(paymentIntentService.getIntent("01INTENT")).thenReturn(i);
        when(attemptRepository.save(any(PaymentAttemptEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        PaymentAttemptEntity attempt = buildService(registry).initiateAttempt("01INTENT", null);

        assertEquals(AdapterType.SANDBOX, attempt.getAdapterType());
        assertTrue(attempt.getEnforcementMetadata().contains("\"reason\":\"LEGACY_FALLBACK\""));
        assertTrue(attempt.getEnforcementMetadata().contains("\"legacy_intent\":true"));
    }

    @Test
    void impiloSimulationForcesSandboxEvenWhenMetadataNamesRealRail() {
        CountingAdapter sb = sandbox("SUCCESS");
        CountingAdapter mm = momo("PENDING");
        AdapterRegistry registry = registryOf(sb, mm);
        properties.getAdapters().getMobileMoney().setEnabled(true);
        properties.getAdapters().getMobileMoney().setCredentialsConfigured(true);

        PaymentIntentEntity i = intent(IntentStatus.PENDING,
                "{\"impilo_simulation\":true,\"rail_selection\":{\"effective_rail\":\"MOBILE_MONEY\"}}");

        when(paymentIntentService.getIntent("01INTENT")).thenReturn(i);
        when(attemptRepository.save(any(PaymentAttemptEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        PaymentAttemptEntity attempt = buildService(registry).initiateAttempt("01INTENT", null);

        assertEquals(AdapterType.SANDBOX, attempt.getAdapterType());
        assertEquals(1, sb.initiatePaymentCalls.get());
        assertEquals(0, mm.initiatePaymentCalls.get());
        assertTrue(attempt.getEnforcementMetadata().contains("\"reason\":\"SIMULATION_LOCK\""));
    }

    @Test
    void unregisteredAdapterAtAttemptTimeThrowsAndEmitsFailedEvent() {
        AdapterRegistry registry = registryOf(sandbox("SUCCESS"));
        PaymentIntentEntity i = intent(IntentStatus.PENDING,
                "{\"rail_selection\":{\"effective_rail\":\"MOBILE_MONEY\"}}");
        when(paymentIntentService.getIntent("01INTENT")).thenReturn(i);

        PaymentAttemptService service = buildService(registry);

        RailEnforcementException ex = assertThrows(RailEnforcementException.class,
                () -> service.initiateAttempt("01INTENT", null));
        assertEquals(RailEnforcementException.CODE_RAIL_UNAVAILABLE, ex.getErrorCode());

        ArgumentCaptor<EventOutboxEntity> outbox = ArgumentCaptor.forClass(EventOutboxEntity.class);
        verify(outboxRepository).save(outbox.capture());
        assertEquals("ATTEMPT_FAILED_PRE_INITIATION", outbox.getValue().getEventType());
        assertTrue(outbox.getValue().getPayload().contains(
                RailEnforcementException.CODE_RAIL_UNAVAILABLE));
        verify(attemptRepository, never()).save(any());
    }

    @Test
    void adapterStatusSuccessMapsToSucceededAndCallsRecordPayment() {
        CountingAdapter sb = sandbox("SUCCESS");
        AdapterRegistry registry = registryOf(sb);
        PaymentIntentEntity i = intent(IntentStatus.AUTHORIZED,
                "{\"rail_selection\":{\"effective_rail\":\"SANDBOX\"}}");

        when(paymentIntentService.getIntent("01INTENT")).thenReturn(i);
        when(attemptRepository.save(any(PaymentAttemptEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        PaymentAttemptEntity attempt = buildService(registry).initiateAttempt("01INTENT", null);

        assertEquals(AttemptStatus.SUCCEEDED, attempt.getStatus());
        verify(paymentIntentService).recordPayment("01INTENT", new BigDecimal("100.00"));
    }

    @Test
    void adapterStatusFailedMapsToFailedAndDoesNotCallRecordPayment() {
        CountingAdapter sb = sandbox("FAILED");
        AdapterRegistry registry = registryOf(sb);
        PaymentIntentEntity i = intent(IntentStatus.PENDING,
                "{\"rail_selection\":{\"effective_rail\":\"SANDBOX\"}}");

        when(paymentIntentService.getIntent("01INTENT")).thenReturn(i);
        when(attemptRepository.save(any(PaymentAttemptEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        PaymentAttemptEntity attempt = buildService(registry).initiateAttempt("01INTENT", null);

        assertEquals(AttemptStatus.FAILED, attempt.getStatus());
        verify(paymentIntentService, never()).recordPayment(anyString(), any());
    }

    @Test
    void concurrentReplay_dataIntegrityViolation_returnsWinnerAttempt() {
        // Race scenario: two concurrent requests with the same idempotency key both pass the
        // application-level pre-check (each sees no existing attempt), one inserts first,
        // the second's INSERT hits the partial unique index and throws DataIntegrityViolation.
        // The service must catch, re-read by (intentId, idempotencyKey), and return the
        // winner's attempt. H2 in tests does not enforce the partial unique index — we
        // simulate the violation directly on the mock so this code path stays covered.
        CountingAdapter sb = sandbox("PENDING");
        AdapterRegistry registry = registryOf(sb);
        PaymentIntentEntity i = intent(IntentStatus.PENDING,
                "{\"rail_selection\":{\"effective_rail\":\"SANDBOX\"}}");

        PaymentAttemptEntity winner = new PaymentAttemptEntity();
        winner.setId("WINNER-01");
        winner.setIntentId("01INTENT");
        winner.setIdempotencyKey("race-key");

        when(paymentIntentService.getIntent("01INTENT")).thenReturn(i);
        when(attemptRepository.findByIntentIdAndIdempotencyKey("01INTENT", "race-key"))
                .thenReturn(Optional.empty())          // first call: pre-check sees nothing
                .thenReturn(Optional.of(winner));      // recovery call after DataIntegrityViolation
        when(attemptRepository.save(any(PaymentAttemptEntity.class)))
                .thenThrow(new org.springframework.dao.DataIntegrityViolationException(
                        "unique constraint idx_mushex_attempts_intent_idempotency"));

        PaymentAttemptEntity result =
                buildService(registry).initiateAttempt("01INTENT", "race-key");

        assertSame(winner, result);
        // The adapter was still called by the losing thread (it had passed the pre-check).
        // That is acceptable — provider-side idempotency keys on the real adapter implementation
        // are the second line of defence; the application-side guarantee is "at most one persisted
        // attempt per (intent, idempotency key)", which this test pins.
        assertEquals(1, sb.initiatePaymentCalls.get());
        verify(attemptRepository, times(2)).findByIntentIdAndIdempotencyKey("01INTENT", "race-key");
    }

    @Test
    void blankIdempotencyKeyIsNormalisedToNullAndDoesNotConsultIndex() {
        CountingAdapter sb = sandbox("PENDING");
        AdapterRegistry registry = registryOf(sb);
        PaymentIntentEntity i = intent(IntentStatus.PENDING,
                "{\"rail_selection\":{\"effective_rail\":\"SANDBOX\"}}");

        when(paymentIntentService.getIntent("01INTENT")).thenReturn(i);
        when(attemptRepository.save(any(PaymentAttemptEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        PaymentAttemptEntity attempt = buildService(registry).initiateAttempt("01INTENT", "   ");

        verify(attemptRepository, never()).findByIntentIdAndIdempotencyKey(anyString(), anyString());
        assertNull(attempt.getIdempotencyKey());
        assertEquals(1, sb.initiatePaymentCalls.get());
    }
}
