package zw.gov.mohcc.impilo.mushex;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.mushex.domain.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.mushex.domain.entity.PaymentIntentEntity;
import zw.gov.mohcc.impilo.mushex.domain.enums.IntentStatus;
import zw.gov.mohcc.impilo.mushex.domain.enums.SourceType;
import zw.gov.mohcc.impilo.mushex.domain.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.mushex.domain.repository.PaymentIntentRepository;
import zw.gov.mohcc.impilo.mushex.service.PaymentIntentService;
import zw.gov.mohcc.impilo.mushex.service.ReceiptService;
import zw.gov.mohcc.impilo.mushex.service.LedgerService;
import zw.gov.mohcc.impilo.mushex.service.FraudDetectionService;
import zw.gov.mohcc.impilo.shared.auth.AccessMode;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link PaymentIntentService}.
 *
 * Validates intent creation, idempotency, state-machine transitions,
 * partial/full payment recording, and cancellation logic.
 */
@ExtendWith(MockitoExtension.class)
class PaymentIntentServiceTest {

    @Mock private PaymentIntentRepository intentRepository;
    @Mock private EventOutboxRepository outboxRepository;
    @Mock private ReceiptService receiptService;
    @Mock private LedgerService ledgerService;
    @Mock private FraudDetectionService fraudService;
    @Mock private ObjectMapper objectMapper;

    @InjectMocks
    private PaymentIntentService service;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID facilityId = UUID.randomUUID();
    private final UUID correlationId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        TrustContextHolder.set(new TrustContext(
            tenantId, "actor-1", "FACILITY_FINANCE", "BILLING",
            "device-1", correlationId, facilityId, null, null, AccessMode.INTERNAL
        ));
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

        verify(intentRepository).save(any(PaymentIntentEntity.class));
        verify(outboxRepository).save(any(EventOutboxEntity.class));
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

        assertNotNull(result);
        assertEquals(metadata, result.getMetadata());
    }

    @Test
    void createIntent_duplicateIdempotencyKey_returnsExisting() throws Exception {
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
        assertEquals(IntentStatus.CREATED, result.getStatus());
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

    // ---------------------------------------------------------------
    // cancelIntent
    // ---------------------------------------------------------------

    @Test
    void cancelIntent_fromCreated_succeeds() throws Exception {
        PaymentIntentEntity intent = buildIntent("INT-001", IntentStatus.CREATED, "100.00", "0.00");
        when(intentRepository.findById("INT-001")).thenReturn(Optional.of(intent));
        when(intentRepository.save(any(PaymentIntentEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        service.cancelIntent("INT-001");

        assertEquals(IntentStatus.CANCELLED, intent.getStatus());
        verify(intentRepository).save(intent);
        verify(outboxRepository).save(any(EventOutboxEntity.class));
    }

    @Test
    void cancelIntent_fromPending_succeeds() throws Exception {
        PaymentIntentEntity intent = buildIntent("INT-002", IntentStatus.PENDING, "100.00", "0.00");
        when(intentRepository.findById("INT-002")).thenReturn(Optional.of(intent));
        when(intentRepository.save(any(PaymentIntentEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        service.cancelIntent("INT-002");

        assertEquals(IntentStatus.CANCELLED, intent.getStatus());
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
    void cancelIntent_alreadyCancelled_throws() {
        PaymentIntentEntity intent = buildIntent("INT-005", IntentStatus.CANCELLED, "100.00", "0.00");
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

        service.recordPayment("INT-010", new BigDecimal("100.00"));

        assertEquals(new BigDecimal("100.00"), intent.getAmountPaid());
        assertEquals(IntentStatus.PAID, intent.getStatus());
        verify(receiptService).generateReceipt("INT-010");
        verify(ledgerService).postPayment(eq(tenantId), eq("INT-010"), eq(new BigDecimal("100.00")), eq("USD"));
    }

    @Test
    void recordPayment_partialAmount_staysPending() throws Exception {
        PaymentIntentEntity intent = buildIntent("INT-011", IntentStatus.PENDING, "100.00", "0.00");
        when(intentRepository.findById("INT-011")).thenReturn(Optional.of(intent));
        when(intentRepository.save(any(PaymentIntentEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        service.recordPayment("INT-011", new BigDecimal("50.00"));

        assertEquals(new BigDecimal("50.00"), intent.getAmountPaid());
        assertEquals(IntentStatus.PENDING, intent.getStatus());
        verify(receiptService, never()).generateReceipt(any());
    }

    @Test
    void recordPayment_accumulatedAmount_transitionsToPaid() throws Exception {
        PaymentIntentEntity intent = buildIntent("INT-012", IntentStatus.PENDING, "100.00", "60.00");
        when(intentRepository.findById("INT-012")).thenReturn(Optional.of(intent));
        when(intentRepository.save(any(PaymentIntentEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        service.recordPayment("INT-012", new BigDecimal("40.00"));

        assertEquals(new BigDecimal("100.00"), intent.getAmountPaid());
        assertEquals(IntentStatus.PAID, intent.getStatus());
        verify(receiptService).generateReceipt("INT-012");
    }

    @Test
    void recordPayment_overpayment_throws() {
        PaymentIntentEntity intent = buildIntent("INT-013", IntentStatus.PENDING, "100.00", "90.00");
        when(intentRepository.findById("INT-013")).thenReturn(Optional.of(intent));

        assertThrows(IllegalArgumentException.class,
            () -> service.recordPayment("INT-013", new BigDecimal("20.00")));
    }

    @Test
    void recordPayment_negativeAmount_throws() {
        PaymentIntentEntity intent = buildIntent("INT-014", IntentStatus.PENDING, "100.00", "0.00");
        when(intentRepository.findById("INT-014")).thenReturn(Optional.of(intent));

        assertThrows(IllegalArgumentException.class,
            () -> service.recordPayment("INT-014", new BigDecimal("-10.00")));
    }

    @Test
    void recordPayment_zeroAmount_throws() {
        PaymentIntentEntity intent = buildIntent("INT-015", IntentStatus.PENDING, "100.00", "0.00");
        when(intentRepository.findById("INT-015")).thenReturn(Optional.of(intent));

        assertThrows(IllegalArgumentException.class,
            () -> service.recordPayment("INT-015", BigDecimal.ZERO));
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

        service.transitionStatus("INT-020", IntentStatus.PENDING);

        assertEquals(IntentStatus.PENDING, intent.getStatus());
    }

    @Test
    void transitionStatus_pendingToAuthorized_succeeds() throws Exception {
        PaymentIntentEntity intent = buildIntent("INT-021", IntentStatus.PENDING, "100.00", "0.00");
        when(intentRepository.findById("INT-021")).thenReturn(Optional.of(intent));
        when(intentRepository.save(any(PaymentIntentEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        service.transitionStatus("INT-021", IntentStatus.AUTHORIZED);

        assertEquals(IntentStatus.AUTHORIZED, intent.getStatus());
    }

    @Test
    void transitionStatus_paidToRefundPending_succeeds() throws Exception {
        PaymentIntentEntity intent = buildIntent("INT-022", IntentStatus.PAID, "100.00", "100.00");
        when(intentRepository.findById("INT-022")).thenReturn(Optional.of(intent));
        when(intentRepository.save(any(PaymentIntentEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        service.transitionStatus("INT-022", IntentStatus.REFUND_PENDING);

        assertEquals(IntentStatus.REFUND_PENDING, intent.getStatus());
    }

    @Test
    void transitionStatus_cancelledToPaid_throws() {
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
