package zw.gov.mohcc.impilo.matcher.engine;

import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Routes an engine request to the correct per-modality matcher. Unknown or
 * uncapable modalities fail closed (UNAVAILABLE / no candidates) — the engine
 * never fabricates a result for a modality it cannot actually match.
 */
@Service
public class MatchDispatcher {

    private final Map<Modality, ModalityMatcher> matchers = new EnumMap<>(Modality.class);

    public MatchDispatcher(List<ModalityMatcher> impls) {
        for (ModalityMatcher m : impls) {
            matchers.put(m.modality(), m);
        }
    }

    public Map<Modality, Boolean> capabilities() {
        Map<Modality, Boolean> caps = new EnumMap<>(Modality.class);
        for (Modality mod : Modality.values()) {
            ModalityMatcher m = matchers.get(mod);
            caps.put(mod, m != null && m.capable());
        }
        return caps;
    }

    public ModalityMatcher.ExtractionResult extract(Modality modality, byte[] sample, int w, int h, int dpi) {
        ModalityMatcher m = matchers.get(modality);
        return m == null ? ModalityMatcher.ExtractionResult.unavailable("no_matcher") : m.extract(sample, w, h, dpi);
    }

    public ModalityMatcher.VerificationResult verify(Modality modality, byte[] probe, byte[] enrolled) {
        ModalityMatcher m = matchers.get(modality);
        return m == null ? ModalityMatcher.VerificationResult.unavailable("no_matcher") : m.verify(probe, enrolled);
    }

    public List<ModalityMatcher.Candidate> identify(Modality modality, byte[] probe,
                                                    List<ModalityMatcher.GalleryEntry> gallery, int maxCandidates) {
        ModalityMatcher m = matchers.get(modality);
        return m == null ? List.of() : m.identify(probe, gallery, maxCandidates);
    }
}
