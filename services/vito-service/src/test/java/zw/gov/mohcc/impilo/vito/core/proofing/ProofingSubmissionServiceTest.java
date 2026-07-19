package zw.gov.mohcc.impilo.vito.core.proofing;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import zw.gov.mohcc.impilo.vito.core.proofing.engine.*;
import zw.gov.mohcc.impilo.vito.persistence.entity.ClientVerificationReviewEntity;
import zw.gov.mohcc.impilo.vito.persistence.repository.ClientVerificationReviewRepository;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DisplayName("ProofingSubmissionService")
class ProofingSubmissionServiceTest {

    private static final String VALID_TD3 = "L898902C36UTO7408122F1204159ZE184226B<<<<<10";

    private final ProofingService proofingService = mock(ProofingService.class);
    private final ClientVerificationReviewRepository reviewRepo = mock(ClientVerificationReviewRepository.class);

    private ProofingSubmissionService service() {
        ProofingOrchestrator orch = new ProofingOrchestrator(proofingService,
                new NativeDocumentVerificationEngine(),
                new FacialComparisonEngine.FailClosed(),
                new LivenessEngine.FailClosed(),
                new ObjectMapper());
        return new ProofingSubmissionService(orch, reviewRepo);
    }

    @Test
    @DisplayName("unattended attempt opens an IDENTITY_PROOFING review and returns its id")
    void opensReviewOnEscalation() {
        when(reviewRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        DocumentEvidence doc = new DocumentEvidence("PASSPORT", List.of("P<UTO...", VALID_TD3), null, List.of("doc://1"));

        ProofingSubmissionService.SubmissionResult r = service().submitDocumentSelfie(
                UUID.randomUUID(), UUID.randomUUID(), doc, "doc://s", "doc://f", "doc://l", "system");

        assertThat(r.outcome().disposition()).isEqualTo(ProofingOrchestrator.Disposition.REVIEW_REQUIRED);
        assertThat(r.reviewId()).isNotNull();
        ArgumentCaptor<ClientVerificationReviewEntity> captor =
                ArgumentCaptor.forClass(ClientVerificationReviewEntity.class);
        verify(reviewRepo).save(captor.capture());
        assertThat(captor.getValue().getReviewType()).isEqualTo("IDENTITY_PROOFING");
        assertThat(captor.getValue().getStatus()).isEqualTo("OPEN");
    }
}
