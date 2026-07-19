package zw.gov.mohcc.impilo.vito.core.proofing.engine;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * The real facial-comparison engine asserts a match ONLY from a genuine engine MATCH,
 * and fails closed (never fabricates) at every gap: missing ref, unfetchable image,
 * no face template (model absent), or an engine that's unavailable.
 */
@ExtendWith(MockitoExtension.class)
class MatcherEngineFacialComparisonEngineTest {

    @Mock ProofingObjectFetcher fetcher;
    @Mock MatcherEngineProofingClient engine;

    MatcherEngineFacialComparisonEngine subject;

    @BeforeEach
    void setUp() {
        subject = new MatcherEngineFacialComparisonEngine(fetcher, engine);
    }

    @Test
    @DisplayName("missing reference → INVALID_INPUT")
    void missingRef() {
        assertEquals(ProofingSignal.INVALID_INPUT, subject.compare(null, "doc").result());
        assertEquals(ProofingSignal.INVALID_INPUT, subject.compare("selfie", null).result());
    }

    @Test
    @DisplayName("unfetchable image → UNAVAILABLE (fail-closed)")
    void unfetchable() {
        when(fetcher.fetch("selfie")).thenReturn(null);
        lenient().when(fetcher.fetch("doc")).thenReturn(new byte[]{1});
        assertEquals(ProofingSignal.UNAVAILABLE, subject.compare("selfie", "doc").result());
    }

    @Test
    @DisplayName("no face template (model absent) → UNAVAILABLE (fail-closed)")
    void noTemplate() {
        when(fetcher.fetch(any())).thenReturn(new byte[]{1, 2, 3});
        when(engine.extractFaceTemplate(any())).thenReturn(Optional.empty());
        assertEquals(ProofingSignal.UNAVAILABLE, subject.compare("selfie", "doc").result());
    }

    @Test
    @DisplayName("engine MATCH → VERIFIED with confidence")
    void match() {
        when(fetcher.fetch(any())).thenReturn(new byte[]{1, 2, 3});
        when(engine.extractFaceTemplate(any())).thenReturn(Optional.of("tpl"));
        when(engine.verifyFace(any(), any()))
                .thenReturn(new MatcherEngineProofingClient.FaceVerdict("MATCH", 0.91));

        ProofingSignal signal = subject.compare("selfie", "doc");

        assertTrue(signal.isVerified());
        assertEquals(0.91, signal.confidence(), 1e-9);
    }

    @Test
    @DisplayName("engine NO_MATCH → REJECTED (genuine, not fabricated)")
    void noMatch() {
        when(fetcher.fetch(any())).thenReturn(new byte[]{1, 2, 3});
        when(engine.extractFaceTemplate(any())).thenReturn(Optional.of("tpl"));
        when(engine.verifyFace(any(), any()))
                .thenReturn(new MatcherEngineProofingClient.FaceVerdict("NO_MATCH", 0.05));

        assertTrue(subject.compare("selfie", "doc").isRejected());
    }

    @Test
    @DisplayName("engine UNAVAILABLE → UNAVAILABLE (fail-closed)")
    void engineUnavailable() {
        when(fetcher.fetch(any())).thenReturn(new byte[]{1, 2, 3});
        when(engine.extractFaceTemplate(any())).thenReturn(Optional.of("tpl"));
        when(engine.verifyFace(any(), any()))
                .thenReturn(MatcherEngineProofingClient.FaceVerdict.unavailable());

        assertEquals(ProofingSignal.UNAVAILABLE, subject.compare("selfie", "doc").result());
    }
}
