package zw.gov.mohcc.impilo.vito.core.proofing;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import zw.gov.mohcc.impilo.vito.core.ProofingMethod;
import zw.gov.mohcc.impilo.vito.core.proofing.engine.*;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("ProofingOrchestrator")
class ProofingOrchestratorTest {

    private static final UUID TENANT = UUID.randomUUID();
    private static final UUID HID = UUID.randomUUID();
    private static final String VALID_TD3 =
            "L898902C36UTO7408122F1204159ZE184226B<<<<<10";

    private ProofingService proofingService;
    private ProofingOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        proofingService = mock(ProofingService.class);
    }

    private DocumentEvidence validDoc() {
        return new DocumentEvidence("PASSPORT", List.of("P<UTO...", VALID_TD3), null, List.of("doc://1"));
    }

    private void wire(FacialComparisonEngine face, LivenessEngine liveness) {
        orchestrator = new ProofingOrchestrator(proofingService,
                new NativeDocumentVerificationEngine(), face, liveness, new ObjectMapper());
    }

    @Test
    @DisplayName("valid doc but fail-closed face/liveness → REVIEW_REQUIRED, no assurance recorded")
    void unattendedRoutesToReview() {
        wire(new FacialComparisonEngine.FailClosed(), new LivenessEngine.FailClosed());
        ProofingOrchestrator.ProofingDecision d = orchestrator.proveDocumentSelfie(
                TENANT, HID, validDoc(), "doc://selfie", "doc://face", "doc://live", "system");
        assertThat(d.disposition()).isEqualTo(ProofingOrchestrator.Disposition.REVIEW_REQUIRED);
        assertThat(d.assuranceLevel()).isZero();
        verify(proofingService, never()).recordEvent(any(), any(), any(), anyInt(), any(), any(), any());
    }

    @Test
    @DisplayName("rejected document → REJECTED + failed ledger event, never a completed one")
    void rejectedDocument() {
        wire(new FacialComparisonEngine.FailClosed(), new LivenessEngine.FailClosed());
        // Flip a check digit so the native engine rejects.
        DocumentEvidence bad = new DocumentEvidence("PASSPORT",
                List.of("P<UTO...", "L898902C36UTO7408123F1204159ZE184226B<<<<<10"), null, List.of("doc://1"));
        ProofingOrchestrator.ProofingDecision d = orchestrator.proveDocumentSelfie(
                TENANT, HID, bad, "doc://selfie", "doc://face", "doc://live", "system");
        assertThat(d.disposition()).isEqualTo(ProofingOrchestrator.Disposition.REJECTED);
        verify(proofingService).recordFailedEvent(eq(TENANT), eq(HID), eq(ProofingMethod.DOCUMENT), eq("system"), anyString());
        verify(proofingService, never()).recordEvent(any(), any(), any(), anyInt(), any(), any(), any());
    }

    @Test
    @DisplayName("all capabilities verified → DOCUMENT_VERIFIED (LOA2) recorded in the ledger")
    void allVerifiedRecordsAssurance() {
        FacialComparisonEngine face = (s, f) -> ProofingSignal.verified(FacialComparisonEngine.CAPABILITY, 0.98, "match");
        LivenessEngine liveness = c -> ProofingSignal.verified(LivenessEngine.CAPABILITY, 0.99, "live");
        wire(face, liveness);
        ProofingOrchestrator.ProofingDecision d = orchestrator.proveDocumentSelfie(
                TENANT, HID, validDoc(), "doc://selfie", "doc://face", "doc://live", "officer-7");
        assertThat(d.disposition()).isEqualTo(ProofingOrchestrator.Disposition.VERIFIED);
        assertThat(d.outcome()).isEqualTo(ProofingOutcome.DOCUMENT_VERIFIED);
        assertThat(d.assuranceLevel()).isEqualTo(2);
        verify(proofingService).recordEvent(eq(TENANT), eq(HID), eq(ProofingMethod.DOCUMENT),
                eq(2), anyString(), eq("officer-7"), anyString());
    }
}
