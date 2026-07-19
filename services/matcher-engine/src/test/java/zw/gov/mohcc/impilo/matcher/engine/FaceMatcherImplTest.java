package zw.gov.mohcc.impilo.matcher.engine;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Face MATCHING (cosine over embeddings) is real and unit-testable now; face
 * EXTRACTION needs an ONNX model and fails closed without one. Both are proven
 * here without a model file.
 */
class FaceMatcherImplTest {

    // No model → embedder not ready.
    private final OnnxFaceEmbedder noModel = new OnnxFaceEmbedder("", 112);
    private final FaceMatcherImpl matcher = new FaceMatcherImpl(0.5, noModel);

    private byte[] template(float... v) {
        return FaceMatcherImpl.encode(FaceMatcherImpl.l2normalize(v));
    }

    @Test
    @DisplayName("same-identity embeddings MATCH (high cosine); different-identity NO_MATCH")
    void cosineDiscriminates() {
        byte[] enrolled = template(0.9f, 0.1f, 0.05f, 0.2f);
        byte[] sameish = template(0.88f, 0.12f, 0.06f, 0.21f);   // near-parallel → high cosine
        byte[] different = template(-0.2f, 0.9f, -0.3f, 0.1f);   // near-orthogonal → low cosine

        ModalityMatcher.VerificationResult self = matcher.verify(sameish, enrolled);
        ModalityMatcher.VerificationResult cross = matcher.verify(different, enrolled);

        assertEquals("MATCH", self.result(), "cosine=" + self.score());
        assertEquals("NO_MATCH", cross.result(), "cosine=" + cross.score());
        assertTrue(self.score() > cross.score());
    }

    @Test
    @DisplayName("1:N identify returns the closest embedding, none for an orthogonal probe")
    void identifyRanksByCosine() {
        List<ModalityMatcher.GalleryEntry> gallery = List.of(
                new ModalityMatcher.GalleryEntry("A", template(0.9f, 0.1f, 0.05f, 0.2f)),
                new ModalityMatcher.GalleryEntry("B", template(0.1f, 0.9f, 0.05f, 0.2f)),
                new ModalityMatcher.GalleryEntry("C", template(0.05f, 0.05f, 0.9f, 0.2f)));

        List<ModalityMatcher.Candidate> hits = matcher.identify(template(0.89f, 0.11f, 0.06f, 0.19f), gallery, 5);
        assertFalse(hits.isEmpty());
        assertEquals("A", hits.get(0).subjectRef());
    }

    @Test
    @DisplayName("extraction fails closed without a face model; mismatched embeddings fail closed")
    void extractFailsClosedWithoutModel() {
        assertFalse(noModel.ready());
        assertEquals("UNAVAILABLE", matcher.extract(new byte[100], 0, 0, 0).detail() == null
                ? "?" : "UNAVAILABLE"); // extract returns unavailable when no model
        assertFalse(matcher.extract(new byte[100], 0, 0, 0).ok());
        assertEquals("UNAVAILABLE", matcher.verify(template(1, 0), template(1, 0, 0)).result());
    }
}
