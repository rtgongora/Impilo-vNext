package zw.gov.mohcc.impilo.mushex;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.mushex.domain.entity.AdjudicationEntity;
import zw.gov.mohcc.impilo.mushex.domain.entity.ClaimEntity;
import zw.gov.mohcc.impilo.mushex.domain.entity.ClaimEventEntity;
import zw.gov.mohcc.impilo.mushex.domain.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.mushex.domain.enums.ClaimStatus;
import zw.gov.mohcc.impilo.mushex.domain.repository.AdjudicationRepository;
import zw.gov.mohcc.impilo.mushex.domain.repository.ClaimAttachmentRepository;
import zw.gov.mohcc.impilo.mushex.domain.repository.ClaimEventRepository;
import zw.gov.mohcc.impilo.mushex.domain.repository.ClaimRepository;
import zw.gov.mohcc.impilo.mushex.domain.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.mushex.domain.repository.PaymentIntentRepository;
import zw.gov.mohcc.impilo.mushex.domain.repository.ReceiptRepository;
import zw.gov.mohcc.impilo.mushex.integration.CoverageEligibilityClient;
import zw.gov.mohcc.impilo.mushex.service.ClaimService;
import zw.gov.mohcc.impilo.mushex.service.PaymentIntentService;
import zw.gov.mohcc.impilo.mushex.service.ReceiptService;
import zw.gov.mohcc.impilo.shared.auth.AccessMode;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.math.BigDecimal;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import java.util.Optional;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link ClaimService}.
 *
 * Validates claim creation, submission (DRAFT->SUBMITTED), adjudication
 * recording with patient residual intent creation, dispute handling
 * (ADJUDICATED->RESUBMIT_PENDING), and enforcement of invalid state
 * transitions via the claim state machine.
 */
@ExtendWith(MockitoExtension.class)
class ClaimServiceTest {

    @Mock private ClaimRepository claimRepository;
    @Mock private ClaimEventRepository claimEventRepository;
    @Mock private ClaimAttachmentRepository attachmentRepository;
    @Mock private AdjudicationRepository adjudicationRepository;
    @Mock private EventOutboxRepository outboxRepository;
    @Mock private PaymentIntentRepository paymentIntentRepository;
    @Mock private ReceiptRepository receiptRepository;
    @Mock private CoverageEligibilityClient coverageEligibilityClient;

    private ClaimService service;
    private RecordingPaymentIntentService intentService;
    private ObjectMapper objectMapper;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID facilityId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        intentService = new RecordingPaymentIntentService(paymentIntentRepository, outboxRepository, receiptRepository, objectMapper);
        service = new ClaimService(
            claimRepository, claimEventRepository, attachmentRepository,
            adjudicationRepository, intentService, outboxRepository, objectMapper, coverageEligibilityClient
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
    // createClaim
    // ---------------------------------------------------------------

    @Test
    void createClaim_createsWithDraftStatus() {
        when(claimRepository.save(any(ClaimEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(claimEventRepository.save(any(ClaimEventEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        ClaimEntity result = service.createClaim("BILL-001", "INS-001", facilityId, "{\"total\":\"500.00\"}");

        assertNotNull(result);
        assertEquals(ClaimStatus.DRAFT, result.getStatus());
        assertEquals("BILL-001", result.getBillId());
        assertEquals("INS-001", result.getInsurerId());
        assertEquals(tenantId, result.getTenantId());
        assertEquals(facilityId, result.getFacilityId());
        assertEquals("{\"total\":\"500.00\"}", result.getTotals());
        assertNotNull(result.getClaimId());

        verify(claimRepository).save(any(ClaimEntity.class));
        verify(claimEventRepository).save(any(ClaimEventEntity.class));
    }

    @Test
    void createClaim_doesNotSetSubmittedAt() {
        when(claimRepository.save(any(ClaimEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(claimEventRepository.save(any(ClaimEventEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        ClaimEntity result = service.createClaim("BILL-003", "INS-003", facilityId, null);

        assertNull(result.getSubmittedAt());
        assertNull(result.getAdjudicatedAt());
    }

    // ---------------------------------------------------------------
    // submitClaim
    // ---------------------------------------------------------------

    @Test
    void submitClaim_transitionsDraftToSubmitted() throws Exception {
        ClaimEntity claim = buildClaim("CLM-100", ClaimStatus.DRAFT);
        when(claimRepository.findById("CLM-100")).thenReturn(Optional.of(claim));
        when(claimRepository.save(any(ClaimEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(claimEventRepository.save(any(ClaimEventEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        ClaimEntity result = service.submitClaim("CLM-100");

        assertEquals(ClaimStatus.SUBMITTED, result.getStatus());
        assertNotNull(result.getSubmittedAt());
        verify(claimRepository).save(claim);
        verify(outboxRepository).save(any(EventOutboxEntity.class));
    }

    @Test
    void submitClaim_createsClaimEvent() throws Exception {
        ClaimEntity claim = buildClaim("CLM-101", ClaimStatus.DRAFT);
        when(claimRepository.findById("CLM-101")).thenReturn(Optional.of(claim));
        when(claimRepository.save(any(ClaimEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(claimEventRepository.save(any(ClaimEventEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        service.submitClaim("CLM-101");

        ArgumentCaptor<ClaimEventEntity> captor = ArgumentCaptor.forClass(ClaimEventEntity.class);
        verify(claimEventRepository).save(captor.capture());
        ClaimEventEntity event = captor.getValue();
        assertEquals("CLM-101", event.getClaimId());
        assertEquals("DRAFT", event.getFromState());
        assertEquals("SUBMITTED", event.getToState());
    }

    @Test
    void submitClaim_fromNonDraft_throws() {
        ClaimEntity claim = buildClaim("CLM-102", ClaimStatus.RECEIVED);
        when(claimRepository.findById("CLM-102")).thenReturn(Optional.of(claim));

        assertThrows(IllegalStateException.class, () -> service.submitClaim("CLM-102"));
        verify(claimRepository, never()).save(any());
    }

    @Test
    void submitClaim_fromResubmitPending_succeeds() throws Exception {
        ClaimEntity claim = buildClaim("CLM-103", ClaimStatus.RESUBMIT_PENDING);
        when(claimRepository.findById("CLM-103")).thenReturn(Optional.of(claim));
        when(claimRepository.save(any(ClaimEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(claimEventRepository.save(any(ClaimEventEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        ClaimEntity result = service.submitClaim("CLM-103");

        assertEquals(ClaimStatus.SUBMITTED, result.getStatus());
    }

    @Test
    void submitClaim_publishesOutboxEvent() throws Exception {
        ClaimEntity claim = buildClaim("CLM-104", ClaimStatus.DRAFT);
        when(claimRepository.findById("CLM-104")).thenReturn(Optional.of(claim));
        when(claimRepository.save(any(ClaimEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(claimEventRepository.save(any(ClaimEventEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        service.submitClaim("CLM-104");

        ArgumentCaptor<EventOutboxEntity> captor = ArgumentCaptor.forClass(EventOutboxEntity.class);
        verify(outboxRepository).save(captor.capture());
        assertEquals("CLAIM_SUBMITTED", captor.getValue().getEventType());
        assertEquals("CLAIM", captor.getValue().getAggregateType());
    }

    // ---------------------------------------------------------------
    // recordAdjudication
    // ---------------------------------------------------------------

    @Test
    void recordAdjudication_createsAdjudicationEntity() throws Exception {
        ClaimEntity claim = buildClaim("CLM-200", ClaimStatus.PREAUTHORIZED);
        when(claimRepository.findById("CLM-200")).thenReturn(Optional.of(claim));
        when(claimRepository.save(any(ClaimEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(adjudicationRepository.save(any(AdjudicationEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(claimEventRepository.save(any(ClaimEventEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        service.recordAdjudication("CLM-200", "{\"approved\":true}",
            new BigDecimal("50.00"), new BigDecimal("450.00"));

        ArgumentCaptor<AdjudicationEntity> adjCaptor = ArgumentCaptor.forClass(AdjudicationEntity.class);
        verify(adjudicationRepository).save(adjCaptor.capture());
        AdjudicationEntity adj = adjCaptor.getValue();
        assertEquals("CLM-200", adj.getClaimId());
        assertEquals(new BigDecimal("50.00"), adj.getPatientResidual());
        assertEquals(new BigDecimal("450.00"), adj.getInsurerPayable());
        assertEquals("{\"approved\":true}", adj.getDecision());
    }

    @Test
    void recordAdjudication_transitionsClaimToAdjudicated() throws Exception {
        ClaimEntity claim = buildClaim("CLM-201", ClaimStatus.PREAUTHORIZED);
        when(claimRepository.findById("CLM-201")).thenReturn(Optional.of(claim));
        when(claimRepository.save(any(ClaimEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(adjudicationRepository.save(any(AdjudicationEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(claimEventRepository.save(any(ClaimEventEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        ClaimEntity result = service.recordAdjudication("CLM-201", null,
            new BigDecimal("100.00"), new BigDecimal("400.00"));

        assertEquals(ClaimStatus.ADJUDICATED, result.getStatus());
        assertNotNull(result.getAdjudicatedAt());
    }

    @Test
    void recordAdjudication_withPatientResidual_createsPaymentIntent() throws Exception {
        ClaimEntity claim = buildClaim("CLM-202", ClaimStatus.PREAUTHORIZED);
        when(claimRepository.findById("CLM-202")).thenReturn(Optional.of(claim));
        when(claimRepository.save(any(ClaimEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(adjudicationRepository.save(any(AdjudicationEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(claimEventRepository.save(any(ClaimEventEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        service.recordAdjudication("CLM-202", null,
            new BigDecimal("150.00"), new BigDecimal("350.00"));

        // Patient residual > 0 should trigger intent creation
        assertEquals(1, intentService.createIntentCalls);
        assertEquals("BILL-CLM-202", intentService.lastSourceId);
        assertEquals(new BigDecimal("150.00"), intentService.lastAmount);
        assertEquals("USD", intentService.lastCurrency);
        assertEquals(facilityId, intentService.lastFacilityId);
        assertEquals("CLAIM_RESIDUAL_CLM-202", intentService.lastIdempotencyKey);
    }

    @Test
    void recordAdjudication_zeroResidual_doesNotCreateIntent() throws Exception {
        ClaimEntity claim = buildClaim("CLM-203", ClaimStatus.PREAUTHORIZED);
        when(claimRepository.findById("CLM-203")).thenReturn(Optional.of(claim));
        when(claimRepository.save(any(ClaimEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(adjudicationRepository.save(any(AdjudicationEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(claimEventRepository.save(any(ClaimEventEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        service.recordAdjudication("CLM-203", null,
            BigDecimal.ZERO, new BigDecimal("500.00"));

        assertEquals(0, intentService.createIntentCalls);
    }

    @Test
    void recordAdjudication_fromDraft_throws() {
        ClaimEntity claim = buildClaim("CLM-204", ClaimStatus.DRAFT);
        when(claimRepository.findById("CLM-204")).thenReturn(Optional.of(claim));

        assertThrows(IllegalStateException.class,
            () -> service.recordAdjudication("CLM-204", null,
                BigDecimal.ZERO, new BigDecimal("500.00")));
    }

    @Test
    void recordAdjudication_fromPaid_throws() {
        ClaimEntity claim = buildClaim("CLM-205", ClaimStatus.PAID);
        when(claimRepository.findById("CLM-205")).thenReturn(Optional.of(claim));

        assertThrows(IllegalStateException.class,
            () -> service.recordAdjudication("CLM-205", null,
                BigDecimal.ZERO, new BigDecimal("500.00")));
    }

    // ---------------------------------------------------------------
    // disputeClaim
    // ---------------------------------------------------------------

    @Test
    void disputeClaim_fromAdjudicated_transitionsToResubmitPending() throws Exception {
        ClaimEntity claim = buildClaim("CLM-300", ClaimStatus.ADJUDICATED);
        when(claimRepository.findById("CLM-300")).thenReturn(Optional.of(claim));
        when(claimRepository.save(any(ClaimEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(claimEventRepository.save(any(ClaimEventEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        ClaimEntity result = service.disputeClaim("CLM-300", "Incorrect adjudication amount", "actor-2");

        assertEquals(ClaimStatus.RESUBMIT_PENDING, result.getStatus());
    }

    @Test
    void disputeClaim_createsEventWithReason() throws Exception {
        ClaimEntity claim = buildClaim("CLM-301", ClaimStatus.ADJUDICATED);
        when(claimRepository.findById("CLM-301")).thenReturn(Optional.of(claim));
        when(claimRepository.save(any(ClaimEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(claimEventRepository.save(any(ClaimEventEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        service.disputeClaim("CLM-301", "Missing line items", "actor-2");

        ArgumentCaptor<ClaimEventEntity> captor = ArgumentCaptor.forClass(ClaimEventEntity.class);
        verify(claimEventRepository).save(captor.capture());
        ClaimEventEntity event = captor.getValue();
        assertEquals("CLM-301", event.getClaimId());
        assertEquals("ADJUDICATED", event.getFromState());
        assertEquals("RESUBMIT_PENDING", event.getToState());
        assertTrue(event.getReason().contains("Missing line items"));
    }

    @Test
    void disputeClaim_fromRejected_transitionsToResubmitPending() throws Exception {
        ClaimEntity claim = buildClaim("CLM-302", ClaimStatus.REJECTED);
        when(claimRepository.findById("CLM-302")).thenReturn(Optional.of(claim));
        when(claimRepository.save(any(ClaimEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(claimEventRepository.save(any(ClaimEventEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        ClaimEntity result = service.disputeClaim("CLM-302", "Rejection was in error", null);

        assertEquals(ClaimStatus.RESUBMIT_PENDING, result.getStatus());
    }

    @Test
    void disputeClaim_fromDraft_throws() {
        ClaimEntity claim = buildClaim("CLM-303", ClaimStatus.DRAFT);
        when(claimRepository.findById("CLM-303")).thenReturn(Optional.of(claim));

        assertThrows(IllegalStateException.class,
            () -> service.disputeClaim("CLM-303", "Some reason", "actor-1"));
    }

    @Test
    void disputeClaim_fromSubmitted_throws() {
        ClaimEntity claim = buildClaim("CLM-304", ClaimStatus.SUBMITTED);
        when(claimRepository.findById("CLM-304")).thenReturn(Optional.of(claim));

        assertThrows(IllegalStateException.class,
            () -> service.disputeClaim("CLM-304", "Some reason", "actor-1"));
    }

    @Test
    void disputeClaim_fromPaid_throws() {
        ClaimEntity claim = buildClaim("CLM-305", ClaimStatus.PAID);
        when(claimRepository.findById("CLM-305")).thenReturn(Optional.of(claim));

        assertThrows(IllegalStateException.class,
            () -> service.disputeClaim("CLM-305", "Some reason", "actor-1"));
    }

    // ---------------------------------------------------------------
    // Invalid state transitions
    // ---------------------------------------------------------------

    @Test
    void submitClaim_fromPaid_throws() {
        ClaimEntity claim = buildClaim("CLM-401", ClaimStatus.PAID);
        when(claimRepository.findById("CLM-401")).thenReturn(Optional.of(claim));

        assertThrows(IllegalStateException.class, () -> service.submitClaim("CLM-401"));
    }

    @Test
    void claimNotFound_throws() {
        when(claimRepository.findById("CLM-MISSING")).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> service.submitClaim("CLM-MISSING"));
    }

    @Test
    void listClaims_withStatusAndInsurer_usesCombinedRepositoryFilter() {
        var pageable = PageRequest.of(0, 20);
        ClaimEntity claim = buildClaim("CLM-500", ClaimStatus.SUBMITTED);
        when(claimRepository.findByTenantIdAndInsurerIdAndStatus(tenantId, "INS-001", ClaimStatus.SUBMITTED, pageable))
                .thenReturn(new PageImpl<>(List.of(claim), pageable, 1));

        var result = service.listClaims(tenantId, ClaimStatus.SUBMITTED, "INS-001", pageable);

        assertEquals(1, result.getTotalElements());
        assertEquals("CLM-500", result.getContent().get(0).getClaimId());
        verify(claimRepository).findByTenantIdAndInsurerIdAndStatus(tenantId, "INS-001", ClaimStatus.SUBMITTED, pageable);
    }

    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------

    private ClaimEntity buildClaim(String claimId, ClaimStatus status) {
        ClaimEntity claim = new ClaimEntity();
        claim.setClaimId(claimId);
        claim.setTenantId(tenantId);
        claim.setFacilityId(facilityId);
        claim.setBillId("BILL-" + claimId);
        claim.setInsurerId("INS-001");
        claim.setStatus(status);
        claim.setTotals("{\"total\":\"500.00\"}");
        return claim;
    }

    private static final class RecordingPaymentIntentService extends PaymentIntentService {
        private int createIntentCalls;
        private String lastSourceId;
        private BigDecimal lastAmount;
        private String lastCurrency;
        private UUID lastFacilityId;
        private String lastIdempotencyKey;

        private RecordingPaymentIntentService(
                PaymentIntentRepository paymentIntentRepository,
                EventOutboxRepository outboxRepository,
                ReceiptRepository receiptRepository,
                ObjectMapper objectMapper
        ) {
            super(paymentIntentRepository, outboxRepository, new ReceiptService(receiptRepository, paymentIntentRepository, objectMapper), objectMapper,
                    (tenantId, facilityId) -> { });
        }

        @Override
        public zw.gov.mohcc.impilo.mushex.domain.entity.PaymentIntentEntity createIntent(
                zw.gov.mohcc.impilo.mushex.domain.enums.SourceType sourceType,
                String sourceId,
                BigDecimal amount,
                String currency,
                UUID facilityId,
                String idempotencyKey,
                String metadata
        ) {
            createIntentCalls++;
            lastSourceId = sourceId;
            lastAmount = amount;
            lastCurrency = currency;
            lastFacilityId = facilityId;
            lastIdempotencyKey = idempotencyKey;
            return new zw.gov.mohcc.impilo.mushex.domain.entity.PaymentIntentEntity();
        }
    }
}
