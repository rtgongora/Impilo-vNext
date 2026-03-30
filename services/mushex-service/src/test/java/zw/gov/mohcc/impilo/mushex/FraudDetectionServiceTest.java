package zw.gov.mohcc.impilo.mushex;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import zw.gov.mohcc.impilo.mushex.domain.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.mushex.domain.entity.FraudFlagEntity;
import zw.gov.mohcc.impilo.mushex.domain.entity.OpsReviewEntity;
import zw.gov.mohcc.impilo.mushex.domain.enums.FraudKind;
import zw.gov.mohcc.impilo.mushex.domain.enums.FraudSeverity;
import zw.gov.mohcc.impilo.mushex.domain.enums.IntentStatus;
import zw.gov.mohcc.impilo.mushex.domain.enums.ReviewStatus;
import zw.gov.mohcc.impilo.mushex.domain.enums.SourceType;
import zw.gov.mohcc.impilo.mushex.domain.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.mushex.domain.repository.FraudFlagRepository;
import zw.gov.mohcc.impilo.mushex.domain.repository.OpsReviewRepository;
import zw.gov.mohcc.impilo.mushex.domain.repository.PaymentIntentRepository;
import zw.gov.mohcc.impilo.mushex.domain.repository.RefundRepository;
import zw.gov.mohcc.impilo.mushex.service.FraudDetectionService;
import zw.gov.mohcc.impilo.shared.auth.AccessMode;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link FraudDetectionService}.
 *
 * Validates duplicate payment detection, repeated refund detection,
 * fraud flagging with OpsReview creation, and paginated flag retrieval.
 */
@ExtendWith(MockitoExtension.class)
class FraudDetectionServiceTest {

    @Mock private FraudFlagRepository fraudFlagRepository;
    @Mock private OpsReviewRepository opsReviewRepository;
    @Mock private PaymentIntentRepository intentRepository;
    @Mock private RefundRepository refundRepository;
    @Mock private EventOutboxRepository outboxRepository;
    @Mock private ObjectMapper objectMapper;

    private FraudDetectionService service;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID facilityId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new FraudDetectionService(
            fraudFlagRepository, opsReviewRepository, intentRepository,
            refundRepository, outboxRepository, objectMapper
        );
        TrustContextHolder.set(new TrustContext(
            tenantId, "actor-1", "FACILITY_FINANCE", "BILLING",
            "device-1", UUID.randomUUID(), facilityId, null, null, "national", AccessMode.INTERNAL
        ));
    }

    @AfterEach
    void tearDown() {
        TrustContextHolder.clear();
    }

    // ---------------------------------------------------------------
    // checkDuplicatePayment
    // ---------------------------------------------------------------

    @Test
    void checkDuplicatePayment_flagsWhenDuplicateFound() throws Exception {
        when(intentRepository.countBySourceTypeAndSourceIdAndStatus(
            SourceType.COSTA_BILL, "BILL-001", IntentStatus.PAID)).thenReturn(1L);
        when(fraudFlagRepository.save(any(FraudFlagEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(opsReviewRepository.save(any(OpsReviewEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        boolean isDuplicate = service.checkDuplicatePayment(
            "INT-NEW", tenantId, SourceType.COSTA_BILL, "BILL-001"
        );

        assertTrue(isDuplicate);

        // Verify fraud flag was created
        ArgumentCaptor<FraudFlagEntity> flagCaptor = ArgumentCaptor.forClass(FraudFlagEntity.class);
        verify(fraudFlagRepository).save(flagCaptor.capture());
        FraudFlagEntity flag = flagCaptor.getValue();
        assertEquals(FraudKind.DUPLICATE_PAYMENT, flag.getKind());
        assertEquals(FraudSeverity.HIGH, flag.getSeverity());
        assertEquals(tenantId, flag.getTenantId());
        assertEquals("PAYMENT_INTENT", flag.getEntityType());
        assertEquals("INT-NEW", flag.getEntityId());
        assertEquals("OPEN", flag.getStatus());
        assertNotNull(flag.getId());

        // Verify ops review was created
        ArgumentCaptor<OpsReviewEntity> reviewCaptor = ArgumentCaptor.forClass(OpsReviewEntity.class);
        verify(opsReviewRepository).save(reviewCaptor.capture());
        OpsReviewEntity review = reviewCaptor.getValue();
        assertEquals(tenantId, review.getTenantId());
        assertEquals("FRAUD", review.getQueueType());
        assertEquals(ReviewStatus.PENDING, review.getStatus());
    }

    @Test
    void checkDuplicatePayment_noDuplicate_returnsFalse() {
        when(intentRepository.countBySourceTypeAndSourceIdAndStatus(
            SourceType.COSTA_BILL, "BILL-002", IntentStatus.PAID)).thenReturn(0L);

        boolean isDuplicate = service.checkDuplicatePayment(
            "INT-NEW-2", tenantId, SourceType.COSTA_BILL, "BILL-002"
        );

        assertFalse(isDuplicate);
        verify(fraudFlagRepository, never()).save(any());
        verify(opsReviewRepository, never()).save(any());
    }

    // ---------------------------------------------------------------
    // checkRepeatedRefund
    // ---------------------------------------------------------------

    @Test
    void checkRepeatedRefund_flagsWhenThresholdExceeded() throws Exception {
        when(refundRepository.countByIntentId("INT-100")).thenReturn(5L);
        when(fraudFlagRepository.save(any(FraudFlagEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(opsReviewRepository.save(any(OpsReviewEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        boolean flagged = service.checkRepeatedRefund("INT-100", tenantId);

        assertTrue(flagged);
        verify(fraudFlagRepository).save(any(FraudFlagEntity.class));
    }

    @Test
    void checkRepeatedRefund_belowThreshold_returnsFalse() {
        when(refundRepository.countByIntentId("INT-200")).thenReturn(1L);

        boolean flagged = service.checkRepeatedRefund("INT-200", tenantId);

        assertFalse(flagged);
        verify(fraudFlagRepository, never()).save(any());
    }

    // ---------------------------------------------------------------
    // flagFraud
    // ---------------------------------------------------------------

    @Test
    void flagFraud_createsFraudFlagAndOpsReview() throws Exception {
        when(fraudFlagRepository.save(any(FraudFlagEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(opsReviewRepository.save(any(OpsReviewEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        FraudFlagEntity flag = service.flagFraud(
            tenantId, FraudKind.ABNORMAL_FREQUENCY, FraudSeverity.HIGH,
            "FACILITY", "FAC-001", "{\"count\":999}"
        );

        assertNotNull(flag.getId());
        assertEquals(tenantId, flag.getTenantId());
        assertEquals(FraudKind.ABNORMAL_FREQUENCY, flag.getKind());
        assertEquals(FraudSeverity.HIGH, flag.getSeverity());
        assertEquals("FACILITY", flag.getEntityType());
        assertEquals("FAC-001", flag.getEntityId());
        assertEquals("{\"count\":999}", flag.getEvidence());
        assertEquals("OPEN", flag.getStatus());

        // Verify ops review
        ArgumentCaptor<OpsReviewEntity> reviewCaptor = ArgumentCaptor.forClass(OpsReviewEntity.class);
        verify(opsReviewRepository).save(reviewCaptor.capture());
        OpsReviewEntity review = reviewCaptor.getValue();
        assertNotNull(review.getId());
        assertEquals(tenantId, review.getTenantId());
        assertEquals("FRAUD", review.getQueueType());
        assertEquals(ReviewStatus.PENDING, review.getStatus());
    }

    @Test
    void flagFraud_publishesOutboxEvent() throws Exception {
        when(fraudFlagRepository.save(any(FraudFlagEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(opsReviewRepository.save(any(OpsReviewEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        service.flagFraud(
            tenantId, FraudKind.DUPLICATE_PAYMENT, FraudSeverity.CRITICAL,
            "PAYMENT_INTENT", "INT-999", "{}"
        );

        ArgumentCaptor<EventOutboxEntity> outboxCaptor = ArgumentCaptor.forClass(EventOutboxEntity.class);
        verify(outboxRepository).save(outboxCaptor.capture());
        assertEquals("FRAUD_FLAGGED", outboxCaptor.getValue().getEventType());
        assertEquals("FRAUD", outboxCaptor.getValue().getAggregateType());
        assertEquals(tenantId, outboxCaptor.getValue().getTenantId());
    }

    @Test
    void flagFraud_allSeverityLevels() throws Exception {
        when(fraudFlagRepository.save(any(FraudFlagEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(opsReviewRepository.save(any(OpsReviewEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        for (FraudSeverity severity : FraudSeverity.values()) {
            service.flagFraud(
                tenantId, FraudKind.ABNORMAL_FREQUENCY, severity,
                "PAYMENT_INTENT", "INT-SEV-" + severity.name(), null
            );
        }

        verify(fraudFlagRepository, times(FraudSeverity.values().length)).save(any(FraudFlagEntity.class));
        verify(opsReviewRepository, times(FraudSeverity.values().length)).save(any(OpsReviewEntity.class));
    }

    // ---------------------------------------------------------------
    // getFlags
    // ---------------------------------------------------------------

    @Test
    void getFlags_returnsPaginatedResults() {
        FraudFlagEntity flag1 = buildFraudFlag("FF-001", FraudKind.DUPLICATE_PAYMENT, FraudSeverity.MEDIUM);
        FraudFlagEntity flag2 = buildFraudFlag("FF-002", FraudKind.REPEATED_REFUND, FraudSeverity.HIGH);

        Page<FraudFlagEntity> page = new PageImpl<>(List.of(flag1, flag2), PageRequest.of(0, 10), 2);
        when(fraudFlagRepository.findByTenantId(eq(tenantId), any(Pageable.class)))
            .thenReturn(page);

        Page<FraudFlagEntity> result = service.getFlags(tenantId, PageRequest.of(0, 10));

        assertNotNull(result);
        assertEquals(2, result.getTotalElements());
        assertEquals(2, result.getContent().size());
        assertEquals("FF-001", result.getContent().get(0).getId());
        assertEquals("FF-002", result.getContent().get(1).getId());
    }

    @Test
    void getFlags_emptyPage_returnsEmptyPage() {
        Page<FraudFlagEntity> emptyPage = new PageImpl<>(List.of(), PageRequest.of(0, 10), 0);
        when(fraudFlagRepository.findByTenantId(eq(tenantId), any(Pageable.class)))
            .thenReturn(emptyPage);

        Page<FraudFlagEntity> result = service.getFlags(tenantId, PageRequest.of(0, 10));

        assertNotNull(result);
        assertEquals(0, result.getTotalElements());
        assertTrue(result.getContent().isEmpty());
    }

    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------

    private FraudFlagEntity buildFraudFlag(String id, FraudKind kind, FraudSeverity severity) {
        FraudFlagEntity flag = new FraudFlagEntity();
        flag.setId(id);
        flag.setTenantId(tenantId);
        flag.setKind(kind);
        flag.setSeverity(severity);
        flag.setEntityType("PAYMENT_INTENT");
        flag.setEntityId("INT-" + id);
        flag.setStatus("OPEN");
        return flag;
    }
}
