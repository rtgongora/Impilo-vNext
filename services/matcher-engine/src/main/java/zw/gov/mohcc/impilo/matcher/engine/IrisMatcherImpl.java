package zw.gov.mohcc.impilo.matcher.engine;

import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Iris matcher — the least-certain modality. Fail-closed until an iris matcher
 * (Daugman/Gabor iris-code + Hamming distance, or an iris ONNX model) is provided
 * (wave A2). Returns UNAVAILABLE / no candidates so nothing is ever fabricated.
 */
@Component
public class IrisMatcherImpl implements ModalityMatcher {

    @Override
    public Modality modality() {
        return Modality.IRIS;
    }

    @Override
    public boolean capable() {
        return false; // becomes true in A2 if an iris matcher/model is sourced
    }

    @Override
    public ExtractionResult extract(byte[] sampleImage, int width, int height, int dpi) {
        return ExtractionResult.unavailable("iris_matcher_not_loaded");
    }

    @Override
    public VerificationResult verify(byte[] probeTemplate, byte[] enrolledTemplate) {
        return VerificationResult.unavailable("iris_matcher_not_loaded");
    }

    @Override
    public List<Candidate> identify(byte[] probeTemplate, List<GalleryEntry> gallery, int maxCandidates) {
        return List.of();
    }
}
