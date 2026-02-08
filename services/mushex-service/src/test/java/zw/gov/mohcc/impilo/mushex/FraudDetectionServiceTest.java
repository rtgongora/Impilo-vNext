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
import zw.gov.mohcc.impilo.mushex.domain.entity.PaymentIntentEntity;
import zw.gov.mohcc.impilo.mushex.domain.enums.FraudKind;
import zw.gov.mohcc.impilo.mushex.domain.enums.FraudSeverity;
import zw.gov.mohcc.impilo.mushex.domain.enums.IntentStatus;
import zw.gov.mohcc.impilo.mushex.domain.enums.ReviewStatus;
import zw.gov.mohcc.impilo.mushex.domain.enums.SourceType;
import zw.gov.mohcc.impilo.mushex.domain.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.mushex.domain.repository.FraudFlagRepository;
import zw.gov.mohcc.impilo.mushex.domain.repository.OpsReviewRepository;
import zw.gov.mohcc.impilo.mushex.domain.repository.PaymentIntentRepository;
import zw.gov.mohcc.impilo.mushex.service.FraudDetectionService;
import zw.gov.mohcc.impilo.shared.auth.AccessMode;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link FraudDetectionService}.
 *
 * Validates duplicate payment detection, fraud flagging with OpsReview
 * creation, and paginated flag retrieval.
 */
@ExtendWith(MockitoExtension.class)
class FraudDetectionServiceTest {

    @Mock private FraudFlagRepository fraudFlagRepository;
    @Mock private OpsReviewRepository opsReviewRepository;
    @Mock private PaymentIntentRepository intentRepository;
    @Mock private EventOutboxRepository outboxRepository;
    @Mock private ObjectMapper objectMapper;

    private FraudDetectionService service;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID facilityId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new FraudDetectionService(
            fraudFlagRepository, opsReviewRepository, intentRepository, outboxRepository, objectMapper
        );
        TrustContextHolder.set(new TrustContext(
            tenantId, "actor-1", "FACILITY_FINANCE", "BILLING",
            "device-1", UUID.randomUUID(), facilityId, null, null, AccessMode.INTERNAL
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
        PaymentIntentEntity existingIntent = new PaymentIntentEntity();
        existingIntent.setIntentId("INT-EXISTING");
        existingIntent.setTenantId(tenantId);
        existingIntent.setStatus(IntentStatus.PAID);
        existingIntent.setAmountTotal(new BigDecimal("100.00"));
        existingIntent.setSourceType(SourceType.COSTA_BILL);
        existingIntent.setSourceId("BILL-001");

        when(intentRepository.findBySourceTypeAndSourceId(SourceType.COSTA_BILL, "BILL-001"))
            .thenReturn(Optional.of(existingIntent));
        when(fraudFlagRepository.save(any(FraudFlagEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(opsReviewRepository.save(any(OpsReviewEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        boolean isDuplicate = service.checkDuplicatePayment(
            SourceType.COSTA_BILL, "BILL-001", "INT-NEW"
        );

        assertTrue(isDuplicate);

        // Verify fraud flag was created
        ArgumentCaptor<FraudFlagEntity> flagCaptor = ArgumentCaptor.forClass(FraudFlagEntity.class);
        verify(fraudFlagRepository).save(flagCaptor.capture());
        FraudFlagEntity flag = flagCaptor.getValue();
        assertEquals(FraudKind.DUPLICATE_PAYMENT, flag.getKind());
        assertEquals(tenantId, flag.getTenantId());
        assertEquals("PAYMENT_INTENT", flag.getEntityType());
        assertEquals("INT-NEW", flag.getEntityId());
        assertNotNull(flag.getId());
    }

    @Test
    void checkDuplicatePayment_noDuplicate_returnsFalse() {
        when(intentRepository.findBySourceTypeAndSourceId(SourceType.COSTA_BILL, "BILL-002"))
            .thenReturn(Optional.empty());

        boolean isDuplicate = service.checkDuplicatePayment(
            SourceType.COSTA_BILL, "BILL-002", "INT-NEW-2"
        );

        assertFalse(isDuplicate);
        verify(fraudFlagRepository, never()).save(any());
        verify(opsReviewRepository, never()).save(any());
    }

    @Test
    void checkDuplicatePayment_sameIntent_notDuplicate() {
        PaymentIntentEntity sameIntent = new PaymentIntentEntity();
        sameIntent.setIntentId("INT-SAME");
        sameIntent.setTenantId(tenantId);
        sameIntent.setStatus(IntentStatus.PENDING);

        when(intentRepository.findBySourceTypeAndSourceId(SourceType.ADHOC, "REF-001"))
            .thenReturn(Optional.of(sameIntent));

        boolean isDuplicate = service.checkDuplicatePayment(
            SourceType.ADHOC, "REF-001", "INT-SAME"
        );

        // Same intent found means it's the same intent, not a duplicate
        assertFalse(isDuplicate);
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

        service.flagFraud(
            tenantId, FraudKind.UNUSUAL_CLAIM_AMOUNT, FraudSeverity.HIGH,
            "CLAIM", "CLM-001", "{\"amount\":\"99999.99\"}"
        );

        // Verify fraud flag
        ArgumentCaptor<FraudFlagEntity> flagCaptor = ArgumentCaptor.forClass(FraudFlagEntity.class);
        verify(fraudFlagRepository).save(flagCaptor.capture());
        FraudFlagEntity flag = flagCaptor.getValue();
        assertNotNull(flag.getId());
        assertEquals(tenantId, flag.getTenantId());
        assertEquals(FraudKind.UNUSUAL_CLAIM_AMOUNT, flag.getKind());
        assertEquals(FraudSeverity.HIGH, flag.getSeverity());
        assertEquals("CLAIM", flag.getEntityType());
        assertEquals("CLM-001", flag.getEntityId());
        assertEquals("{\"amount\":\"99999.99\"}", flag.getEvidence());
        assertEquals("OPEN", flag.getStatus());

        // Verify ops review was created
        ArgumentCaptor<OpsReviewEntity> reviewCaptor = ArgumentCaptor.forClass(OpsReviewEntity.class);
        verify(opsReviewRepository).save(reviewCaptor.capture());
        OpsReviewEntity review = reviewCaptor.getValue();
        assertNotNull(review.getId());
        assertEquals(tenantId, review.getTenantId());
        assertEquals("FRAUD", review.getQueueType());
        assertEquals("FRAUD_FLAG", review.getEntityType());
        assertEquals(flag.getId(), review.getEntityId());
        assertEquals(ReviewStatus.PENDING, review.getStatus());
    }

    @Test
    void flagFraud_publishesOutboxEvent() throws Exception {
        when(fraudFlagRepository.save(any(FraudFlagEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(opsReviewRepository.save(any(OpsReviewEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        service.flagFraud(
            tenantId, FraudKind.VELOCITY_BREACH, FraudSeverity.CRITICAL,
            "PAYMENT_INTENT", "INT-999", "{}"
        );

        ArgumentCaptor<EventOutboxEntity> outboxCaptor = ArgumentCaptor.forClass(EventOutboxEntity.class);
        verify(outboxRepository).save(outboxCaptor.capture());
        assertEquals("FRAUD_FLAGGED", outboxCaptor.getValue().getEventType());
        assertEquals("FRAUD_FLAG", outboxCaptor.getValue().getAggregateType());
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
        when(fraudFlagRepository.findByTenantIdAndStatus(eq(tenantId), eq("OPEN"), any(Pageable.class)))
            .thenReturn(page);

        Page<FraudFlagEntity> result = service.getFlags(tenantId, "OPEN", PageRequest.of(0, 10));

        assertNotNull(result);
        assertEquals(2, result.getTotalElements());
        assertEquals(2, result.getContent().size());
        assertEquals("FF-001", result.getContent().get(0).getId());
        assertEquals("FF-002", result.getContent().get(1).getId());
    }

    @Test
    void getFlags_emptyPage_returnsEmptyPage() {
        Page<FraudFlagEntity> emptyPage = new PageImpl<>(List.of(), PageRequest.of(0, 10), 0);
        when(fraudFlagRepository.findByTenantIdAndStatus(eq(tenantId), eq("OPEN"), any(Pageable.class)))
            .thenReturn(emptyPage);

        Page<FraudFlagEntity> result = service.getFlags(tenantId, "OPEN", PageRequest.of(0, 10));

        assertNotNull(result);
        assertEquals(0, result.getTotalElements());
        assertTrue(result.getContent().isEmpty());
    }

    @Test
    void getFlags_respectsPagination() {
        Pageable pageable = PageRequest.of(2, 5);
        Page<FraudFlagEntity> emptyPage = new PageImpl<>(List.of(), pageable, 15);
        when(fraudFlagRepository.findByTenantIdAndStatus(eq(tenantId), eq("OPEN"), eq(pageable)))
            .thenReturn(emptyPage);

        service.getFlags(tenantId, "OPEN", pageable);

        verify(fraudFlagRepository).findByTenantIdAndStatus(tenantId, "OPEN", pageable);
    }

    // ---------------------------------------------------------------
    // getFlagsByEntity
    // ---------------------------------------------------------------

    @Test
    void getFlagsByEntity_returnsMatchingFlags() {
        FraudFlagEntity flag = buildFraudFlag("FF-010", FraudKind.DUPLICATE_PAYMENT, FraudSeverity.HIGH);
        when(fraudFlagRepository.findByEntityTypeAndEntityId("PAYMENT_INTENT", "INT-001"))
            .thenReturn(List.of(flag));

        List<FraudFlagEntity> result = service.getFlagsByEntity("PAYMENT_INTENT", "INT-001");

        assertEquals(1, result.size());
        assertEquals("FF-010", result.get(0).getId());
    }

    @Test
    void getFlagsByEntity_noResults_returnsEmptyList() {
        when(fraudFlagRepository.findByEntityTypeAndEntityId("CLAIM", "CLM-999"))
            .thenReturn(List.of());

        List<FraudFlagEntity> result = service.getFlagsByEntity("CLAIM", "CLM-999");

        assertNotNull(result);
        assertTrue(result.isEmpty());
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
