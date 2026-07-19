package zw.gov.mohcc.impilo.matcher.engine;

import com.machinezoo.sourceafis.FingerprintImage;
import com.machinezoo.sourceafis.FingerprintImageOptions;
import com.machinezoo.sourceafis.FingerprintMatcher;
import com.machinezoo.sourceafis.FingerprintTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Real fingerprint matcher — SourceAFIS minutiae extraction + matching (Apache-2.0,
 * pure JVM, no native libs, no model file). This is genuine 1:1/1:N matching, not a
 * hash-equality placeholder: it extracts minutiae from a capture image and scores
 * similarity, so a matching finger scores far above a non-matching one.
 *
 * <p>Template bytes are SourceAFIS's own serialized {@link FingerprintTemplate}
 * (versioned CBOR). ABIS stores these (encrypted) and passes them back per request.</p>
 *
 * <p>The match SCORE is compared against a configured threshold
 * ({@code matcher.fingerprint.match-threshold}, default 40 — SourceAFIS's documented
 * FMR&lt;0.01% operating point). {@code confidence} normalizes the score to 0..1.</p>
 */
@Component
public class FingerprintMatcherImpl implements ModalityMatcher {

    private static final Logger log = LoggerFactory.getLogger(FingerprintMatcherImpl.class);

    private final double matchThreshold;
    private final int defaultDpi;

    public FingerprintMatcherImpl(
            @Value("${matcher.fingerprint.match-threshold:40}") double matchThreshold,
            @Value("${matcher.fingerprint.default-dpi:500}") int defaultDpi) {
        this.matchThreshold = matchThreshold;
        this.defaultDpi = defaultDpi;
    }

    @Override
    public Modality modality() {
        return Modality.FINGERPRINT;
    }

    @Override
    public boolean capable() {
        return true; // SourceAFIS is always available (no external model).
    }

    @Override
    public ExtractionResult extract(byte[] sampleImage, int width, int height, int dpi) {
        try {
            int useDpi = dpi > 0 ? dpi : defaultDpi;
            FingerprintImage image = (width > 0 && height > 0 && sampleImage.length == width * height)
                    // raw 8-bit grayscale pixels
                    ? new FingerprintImage(width, height, sampleImage, new FingerprintImageOptions().dpi(useDpi))
                    // encoded image (PNG/JPEG/WSQ etc.) — SourceAFIS decodes standard formats
                    : new FingerprintImage(sampleImage, new FingerprintImageOptions().dpi(useDpi));
            FingerprintTemplate template = new FingerprintTemplate(image);
            byte[] bytes = template.toByteArray();
            // SourceAFIS does not expose an NFIQ score; use minutiae-derived template size as a
            // coarse quality proxy (bounded 0..100). Real NFIQ2 is an EXTERNAL_INTEGRATION follow-on.
            double quality = Math.min(100.0, bytes.length / 20.0);
            return new ExtractionResult(true, bytes, quality, "sourceafis");
        } catch (RuntimeException e) {
            log.warn("Fingerprint extraction failed: {}", e.getMessage());
            return ExtractionResult.unavailable("extraction_failed: " + e.getMessage());
        }
    }

    @Override
    public VerificationResult verify(byte[] probeTemplate, byte[] enrolledTemplate) {
        if (probeTemplate == null || enrolledTemplate == null
                || probeTemplate.length == 0 || enrolledTemplate.length == 0) {
            return VerificationResult.unavailable("missing_template");
        }
        try {
            FingerprintMatcher matcher = new FingerprintMatcher(new FingerprintTemplate(enrolledTemplate));
            double score = matcher.match(new FingerprintTemplate(probeTemplate));
            boolean isMatch = score >= matchThreshold;
            return new VerificationResult(isMatch ? "MATCH" : "NO_MATCH", score, confidence(score),
                    "threshold=" + matchThreshold);
        } catch (RuntimeException e) {
            log.warn("Fingerprint verify failed: {}", e.getMessage());
            return VerificationResult.unavailable("verify_failed: " + e.getMessage());
        }
    }

    @Override
    public List<Candidate> identify(byte[] probeTemplate, List<GalleryEntry> gallery, int maxCandidates) {
        if (probeTemplate == null || probeTemplate.length == 0 || gallery == null || gallery.isEmpty()) {
            return List.of();
        }
        try {
            // One matcher built from the probe, scored across the gallery (SourceAFIS's
            // recommended 1:N shape: probe is the "candidate", each gallery template a "query").
            FingerprintMatcher matcher = new FingerprintMatcher(new FingerprintTemplate(probeTemplate));
            List<Candidate> scored = new ArrayList<>();
            for (GalleryEntry entry : gallery) {
                if (entry.template() == null || entry.template().length == 0) {
                    continue;
                }
                double score = matcher.match(new FingerprintTemplate(entry.template()));
                if (score >= matchThreshold) {
                    scored.add(new Candidate(entry.subjectRef(), score, confidence(score)));
                }
            }
            scored.sort(Comparator.comparingDouble(Candidate::score).reversed());
            int cap = maxCandidates > 0 ? Math.min(maxCandidates, scored.size()) : scored.size();
            return new ArrayList<>(scored.subList(0, cap));
        } catch (RuntimeException e) {
            log.warn("Fingerprint identify failed: {}", e.getMessage());
            return List.of();
        }
    }

    /** Squashes an unbounded SourceAFIS score to 0..1 around the threshold (logistic-ish). */
    private double confidence(double score) {
        // At the threshold → ~0.5; well above → →1; below → →0.
        double x = (score - matchThreshold) / Math.max(1.0, matchThreshold);
        return 1.0 / (1.0 + Math.exp(-x));
    }
}
