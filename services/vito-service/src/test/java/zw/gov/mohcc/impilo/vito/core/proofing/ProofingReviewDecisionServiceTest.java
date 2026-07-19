package zw.gov.mohcc.impilo.vito.core.proofing;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import zw.gov.mohcc.impilo.vito.core.ProofingMethod;
import zw.gov.mohcc.impilo.vito.persistence.entity.ClientVerificationReviewEntity;
import zw.gov.mohcc.impilo.vito.persistence.repository.ClientVerificationReviewRepository;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("ProofingReviewDecisionService")
class ProofingReviewDecisionServiceTest {

    private static final UUID TENANT = UUID.randomUUID();
    private static final UUID HID = UUID.randomUUID();
    private static final UUID REVIEW = UUID.randomUUID();

    private ClientVerificationReviewRepository reviewRepo;
    private ProofingService proofingService;
    private ProofingReviewDecisionService service;

    @BeforeEach
    void setUp() {
        reviewRepo = mock(ClientVerificationReviewRepository.class);
        proofingService = mock(ProofingService.class);
        service = new ProofingReviewDecisionService(reviewRepo, proofingService);
        when(reviewRepo.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    private void openReview() {
        ClientVerificationReviewEntity r = new ClientVerificationReviewEntity();
        r.setReviewId(REVIEW);
        r.setTenantId(TENANT);
        r.setClientHealthId(HID);
        r.setReviewType("IDENTITY_PROOFING");
        r.setStatus("OPEN");
        r.setNotes("opened");
        when(reviewRepo.findByReviewIdAndTenantId(REVIEW, TENANT)).thenReturn(Optional.of(r));
    }

    @Test
    @DisplayName("APPROVE with IN_PERSON_WITNESSED records LOA3 assurance under the officer")
    void approveRecordsWitnessedOutcome() {
        openReview();
        var res = service.decide(TENANT, REVIEW, "officer-3", "APPROVE",
                ProofingOutcome.IN_PERSON_WITNESSED, "inspected passport");
        assertThat(res.status()).isEqualTo("APPROVED");
        assertThat(res.assuranceLevel()).isEqualTo(3);
        verify(proofingService).recordEvent(eq(TENANT), eq(HID), eq(ProofingMethod.ASSISTED),
                eq(3), anyString(), eq("officer-3"), anyString());
    }

    @Test
    @DisplayName("REJECT records a failed event and never grants assurance")
    void rejectRecordsFailure() {
        openReview();
        var res = service.decide(TENANT, REVIEW, "officer-3", "REJECT", null, "forged");
        assertThat(res.status()).isEqualTo("REJECTED");
        assertThat(res.assuranceLevel()).isZero();
        verify(proofingService).recordFailedEvent(eq(TENANT), eq(HID), any(), eq("officer-3"), anyString());
        verify(proofingService, never()).recordEvent(any(), any(), any(), anyInt(), any(), any(), any());
    }

    @Test
    @DisplayName("an officer review cannot mint an authoritative (CRVS) or biometric outcome")
    void cannotGrantAuthoritativeOutcome() {
        openReview();
        assertThatThrownBy(() -> service.decide(TENANT, REVIEW, "officer-3", "APPROVE",
                ProofingOutcome.AUTHORITATIVELY_VERIFIED, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.decide(TENANT, REVIEW, "officer-3", "APPROVE",
                ProofingOutcome.BIOMETRICALLY_BOUND, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("an already-resolved review cannot be decided again")
    void doubleDecisionRejected() {
        ClientVerificationReviewEntity r = new ClientVerificationReviewEntity();
        r.setReviewId(REVIEW);
        r.setTenantId(TENANT);
        r.setClientHealthId(HID);
        r.setStatus("APPROVED");
        when(reviewRepo.findByReviewIdAndTenantId(REVIEW, TENANT)).thenReturn(Optional.of(r));
        assertThatThrownBy(() -> service.decide(TENANT, REVIEW, "officer-3", "APPROVE",
                ProofingOutcome.DOCUMENT_VERIFIED, null))
                .isInstanceOf(IllegalStateException.class);
    }
}
