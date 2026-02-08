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
import zw.gov.mohcc.impilo.mushex.domain.repository.ClaimEventRepository;
import zw.gov.mohcc.impilo.mushex.domain.repository.ClaimRepository;
import zw.gov.mohcc.impilo.mushex.domain.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.mushex.service.ClaimService;
import zw.gov.mohcc.impilo.shared.auth.AccessMode;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link ClaimService}.
 *
 * Validates claim creation, submission (DRAFT->SUBMITTED), adjudication
 * recording, dispute handling (ADJUDICATED->RESUBMIT_PENDING), and
 * enforcement of invalid state transitions.
 */
@ExtendWith(MockitoExtension.class)
class ClaimServiceTest {

    @Mock private ClaimRepository claimRepository;
    @Mock private AdjudicationRepository adjudicationRepository;
    @Mock private ClaimEventRepository claimEventRepository;
    @Mock private EventOutboxRepository outboxRepository;
    @Mock private ObjectMapper objectMapper;

    private ClaimService service;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID facilityId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new ClaimService(
            claimRepository, adjudicationRepository, claimEventRepository, outboxRepository, objectMapper
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
    void createClaim_createsWithDraftStatus() throws Exception {
        when(claimRepository.save(any(ClaimEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

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
    }

    @Test
    void createClaim_generatesClaimId() throws Exception {
        when(claimRepository.save(any(ClaimEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        ClaimEntity result = service.createClaim("BILL-002", "INS-002", facilityId, null);

        assertNotNull(result.getClaimId());
        assertFalse(result.getClaimId().isBlank());
    }

    @Test
    void createClaim_doesNotSetSubmittedAt() throws Exception {
        when(claimRepository.save(any(ClaimEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

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
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        service.submitClaim("CLM-100");

        assertEquals(ClaimStatus.SUBMITTED, claim.getStatus());
        assertNotNull(claim.getSubmittedAt());
        verify(claimRepository).save(claim);
        verify(outboxRepository).save(any(EventOutboxEntity.class));
    }

    @Test
    void submitClaim_createsClaimEvent() throws Exception {
        ClaimEntity claim = buildClaim("CLM-101", ClaimStatus.DRAFT);
        when(claimRepository.findById("CLM-101")).thenReturn(Optional.of(claim));
        when(claimRepository.save(any(ClaimEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(claimEventRepository.save(any(ClaimEventEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

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
        ClaimEntity claim = buildClaim("CLM-102", ClaimStatus.SUBMITTED);
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
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        service.submitClaim("CLM-103");

        assertEquals(ClaimStatus.SUBMITTED, claim.getStatus());
    }

    @Test
    void submitClaim_publishesOutboxEvent() throws Exception {
        ClaimEntity claim = buildClaim("CLM-104", ClaimStatus.DRAFT);
        when(claimRepository.findById("CLM-104")).thenReturn(Optional.of(claim));
        when(claimRepository.save(any(ClaimEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(claimEventRepository.save(any(ClaimEventEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

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
        ClaimEntity claim = buildClaim("CLM-200", ClaimStatus.RECEIVED);
        when(claimRepository.findById("CLM-200")).thenReturn(Optional.of(claim));
        when(claimRepository.save(any(ClaimEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(adjudicationRepository.save(any(AdjudicationEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(claimEventRepository.save(any(ClaimEventEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        service.recordAdjudication("CLM-200", "{\"approved\":true}",
            new BigDecimal("50.00"), new BigDecimal("450.00"));

        // Verify adjudication entity was created
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
        ClaimEntity claim = buildClaim("CLM-201", ClaimStatus.RECEIVED);
        when(claimRepository.findById("CLM-201")).thenReturn(Optional.of(claim));
        when(claimRepository.save(any(ClaimEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(adjudicationRepository.save(any(AdjudicationEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(claimEventRepository.save(any(ClaimEventEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        service.recordAdjudication("CLM-201", null,
            new BigDecimal("100.00"), new BigDecimal("400.00"));

        assertEquals(ClaimStatus.ADJUDICATED, claim.getStatus());
        assertNotNull(claim.getAdjudicatedAt());
    }

    @Test
    void recordAdjudication_publishesOutboxEvent() throws Exception {
        ClaimEntity claim = buildClaim("CLM-202", ClaimStatus.RECEIVED);
        when(claimRepository.findById("CLM-202")).thenReturn(Optional.of(claim));
        when(claimRepository.save(any(ClaimEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(adjudicationRepository.save(any(AdjudicationEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(claimEventRepository.save(any(ClaimEventEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        service.recordAdjudication("CLM-202", null,
            BigDecimal.ZERO, new BigDecimal("500.00"));

        ArgumentCaptor<EventOutboxEntity> outboxCaptor = ArgumentCaptor.forClass(EventOutboxEntity.class);
        verify(outboxRepository).save(outboxCaptor.capture());
        assertEquals("CLAIM_ADJUDICATED", outboxCaptor.getValue().getEventType());
    }

    @Test
    void recordAdjudication_fromDraft_throws() {
        ClaimEntity claim = buildClaim("CLM-203", ClaimStatus.DRAFT);
        when(claimRepository.findById("CLM-203")).thenReturn(Optional.of(claim));

        assertThrows(IllegalStateException.class,
            () -> service.recordAdjudication("CLM-203", null,
                BigDecimal.ZERO, new BigDecimal("500.00")));
    }

    @Test
    void recordAdjudication_fromPaid_throws() {
        ClaimEntity claim = buildClaim("CLM-204", ClaimStatus.PAID);
        when(claimRepository.findById("CLM-204")).thenReturn(Optional.of(claim));

        assertThrows(IllegalStateException.class,
            () -> service.recordAdjudication("CLM-204", null,
                BigDecimal.ZERO, new BigDecimal("500.00")));
    }

    // ---------------------------------------------------------------
    // disputeClaim
    // ---------------------------------------------------------------

    @Test
    void disputeClaim_transitionsToResubmitPending() throws Exception {
        ClaimEntity claim = buildClaim("CLM-300", ClaimStatus.ADJUDICATED);
        when(claimRepository.findById("CLM-300")).thenReturn(Optional.of(claim));
        when(claimRepository.save(any(ClaimEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(claimEventRepository.save(any(ClaimEventEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        service.disputeClaim("CLM-300", "Incorrect adjudication amount");

        assertEquals(ClaimStatus.RESUBMIT_PENDING, claim.getStatus());
    }

    @Test
    void disputeClaim_createsEventWithReason() throws Exception {
        ClaimEntity claim = buildClaim("CLM-301", ClaimStatus.ADJUDICATED);
        when(claimRepository.findById("CLM-301")).thenReturn(Optional.of(claim));
        when(claimRepository.save(any(ClaimEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(claimEventRepository.save(any(ClaimEventEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        service.disputeClaim("CLM-301", "Missing line items in adjudication");

        ArgumentCaptor<ClaimEventEntity> captor = ArgumentCaptor.forClass(ClaimEventEntity.class);
        verify(claimEventRepository).save(captor.capture());
        ClaimEventEntity event = captor.getValue();
        assertEquals("CLM-301", event.getClaimId());
        assertEquals("ADJUDICATED", event.getFromState());
        assertEquals("RESUBMIT_PENDING", event.getToState());
        assertEquals("Missing line items in adjudication", event.getReason());
    }

    @Test
    void disputeClaim_fromRejected_transitionsToResubmitPending() throws Exception {
        ClaimEntity claim = buildClaim("CLM-302", ClaimStatus.REJECTED);
        when(claimRepository.findById("CLM-302")).thenReturn(Optional.of(claim));
        when(claimRepository.save(any(ClaimEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(claimEventRepository.save(any(ClaimEventEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        service.disputeClaim("CLM-302", "Rejection was in error");

        assertEquals(ClaimStatus.RESUBMIT_PENDING, claim.getStatus());
    }

    @Test
    void disputeClaim_fromDraft_throws() {
        ClaimEntity claim = buildClaim("CLM-303", ClaimStatus.DRAFT);
        when(claimRepository.findById("CLM-303")).thenReturn(Optional.of(claim));

        assertThrows(IllegalStateException.class,
            () -> service.disputeClaim("CLM-303", "Some reason"));
    }

    @Test
    void disputeClaim_fromSubmitted_throws() {
        ClaimEntity claim = buildClaim("CLM-304", ClaimStatus.SUBMITTED);
        when(claimRepository.findById("CLM-304")).thenReturn(Optional.of(claim));

        assertThrows(IllegalStateException.class,
            () -> service.disputeClaim("CLM-304", "Some reason"));
    }

    @Test
    void disputeClaim_fromPaid_throws() {
        ClaimEntity claim = buildClaim("CLM-305", ClaimStatus.PAID);
        when(claimRepository.findById("CLM-305")).thenReturn(Optional.of(claim));

        assertThrows(IllegalStateException.class,
            () -> service.disputeClaim("CLM-305", "Some reason"));
    }

    // ---------------------------------------------------------------
    // Invalid state transitions
    // ---------------------------------------------------------------

    @Test
    void submitClaim_fromRejected_throws() {
        ClaimEntity claim = buildClaim("CLM-400", ClaimStatus.REJECTED);
        when(claimRepository.findById("CLM-400")).thenReturn(Optional.of(claim));

        // REJECTED can only go to RESUBMIT_PENDING via dispute, not directly submitted
        assertThrows(IllegalStateException.class, () -> service.submitClaim("CLM-400"));
    }

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
}
