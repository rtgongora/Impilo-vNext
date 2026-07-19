package zw.gov.mohcc.impilo.matcher.engine;

import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Face matcher — fail-closed until an ONNX face-embedding model is provided
 * (wave A2 wires ONNX Runtime + model at {@code matcher.face.model-path}). Until
 * then every operation returns UNAVAILABLE / no candidates, so no consumer ever
 * receives a fabricated face match. Care is never blocked on a face failure.
 */
@Component
public class FaceMatcherImpl implements ModalityMatcher {

    @Override
    public Modality modality() {
        return Modality.FACE;
    }

    @Override
    public boolean capable() {
        return false; // becomes true in A2 when a model is loaded
    }

    @Override
    public ExtractionResult extract(byte[] sampleImage, int width, int height, int dpi) {
        return ExtractionResult.unavailable("face_model_not_loaded");
    }

    @Override
    public VerificationResult verify(byte[] probeTemplate, byte[] enrolledTemplate) {
        return VerificationResult.unavailable("face_model_not_loaded");
    }

    @Override
    public List<Candidate> identify(byte[] probeTemplate, List<GalleryEntry> gallery, int maxCandidates) {
        return List.of();
    }
}
