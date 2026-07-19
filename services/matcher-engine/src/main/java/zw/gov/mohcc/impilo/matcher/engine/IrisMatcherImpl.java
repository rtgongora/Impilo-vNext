package zw.gov.mohcc.impilo.matcher.engine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Iris matcher — a real Daugman/Gabor iris-code implementation in pure JVM (no
 * model, no native libs). The template is a binary iris-code: at each sampling
 * point a complex 1-D Gabor response is quantised to 2 bits (sign of real and
 * imaginary parts). Verification is the fractional Hamming distance between two
 * codes; the same iris produces a low HD, a different iris a near-random HD.
 *
 * <p>Input to {@link #extract} is a NORMALISED iris strip (raw 8-bit grayscale,
 * {@code width×height}, angular × radial). Iris SEGMENTATION from a raw eye photo
 * (pupil/limbus localisation, unwrapping) is a follow-on — this engine matches
 * normalised strips, which is the honest boundary. Least-certain modality by
 * design; swappable for an iris ONNX model behind the same seam.</p>
 */
@Component
public class IrisMatcherImpl implements ModalityMatcher {

    private static final Logger log = LoggerFactory.getLogger(IrisMatcherImpl.class);

    // Sampling grid over the normalised strip and Gabor parameters.
    private static final int ROWS = 8;       // radial bands
    private static final int COLS = 128;     // angular samples
    private static final double WAVELENGTH = 12.0;
    private static final double SIGMA = 6.0;
    private static final int HALF = 12;      // Gabor window half-width (angular)

    private final double maxHammingForMatch;

    public IrisMatcherImpl(@Value("${matcher.iris.max-hamming:0.35}") double maxHammingForMatch) {
        this.maxHammingForMatch = maxHammingForMatch;
    }

    @Override
    public Modality modality() {
        return Modality.IRIS;
    }

    @Override
    public boolean capable() {
        return true; // pure-JVM Gabor matcher; no external model needed
    }

    @Override
    public ExtractionResult extract(byte[] strip, int width, int height, int dpi) {
        if (strip == null || width <= 0 || height <= 0 || strip.length < width * height) {
            return ExtractionResult.unavailable("iris_strip_required (raw grayscale width×height)");
        }
        try {
            byte[] code = encode(strip, width, height);
            return new ExtractionResult(true, code, 100.0, "gabor_iris_code");
        } catch (RuntimeException e) {
            log.warn("Iris extraction failed: {}", e.getMessage());
            return ExtractionResult.unavailable("extraction_failed: " + e.getMessage());
        }
    }

    @Override
    public VerificationResult verify(byte[] probeCode, byte[] enrolledCode) {
        if (probeCode == null || enrolledCode == null || probeCode.length == 0
                || probeCode.length != enrolledCode.length) {
            return VerificationResult.unavailable("missing_or_mismatched_iris_code");
        }
        double hd = fractionalHamming(probeCode, enrolledCode);
        boolean isMatch = hd <= maxHammingForMatch;
        // Lower HD = better; expose it as the "score" and a 0..1 confidence.
        return new VerificationResult(isMatch ? "MATCH" : "NO_MATCH", hd,
                confidence(hd), "hamming<=" + maxHammingForMatch);
    }

    @Override
    public List<Candidate> identify(byte[] probeCode, List<GalleryEntry> gallery, int maxCandidates) {
        if (probeCode == null || probeCode.length == 0 || gallery == null || gallery.isEmpty()) {
            return List.of();
        }
        List<Candidate> scored = new ArrayList<>();
        for (GalleryEntry g : gallery) {
            if (g.template() == null || g.template().length != probeCode.length) {
                continue;
            }
            double hd = fractionalHamming(probeCode, g.template());
            if (hd <= maxHammingForMatch) {
                scored.add(new Candidate(g.subjectRef(), hd, confidence(hd)));
            }
        }
        // lowest Hamming distance = best match first
        scored.sort(Comparator.comparingDouble(Candidate::score));
        int cap = maxCandidates > 0 ? Math.min(maxCandidates, scored.size()) : scored.size();
        return new ArrayList<>(scored.subList(0, cap));
    }

    // ── Gabor iris-code encoding ─────────────────────────────────────────
    private byte[] encode(byte[] strip, int w, int h) {
        // Precompute a 1-D even/odd Gabor kernel over the angular window.
        double[] gReal = new double[2 * HALF + 1], gImag = new double[2 * HALF + 1];
        for (int k = -HALF; k <= HALF; k++) {
            double env = Math.exp(-(k * k) / (2 * SIGMA * SIGMA));
            double phase = 2 * Math.PI * k / WAVELENGTH;
            gReal[k + HALF] = env * Math.cos(phase);
            gImag[k + HALF] = env * Math.sin(phase);
        }
        // 2 bits per (row, col) sample → code is ROWS*COLS*2 bits.
        int bits = ROWS * COLS * 2;
        byte[] code = new byte[(bits + 7) / 8];
        int bitIndex = 0;
        for (int r = 0; r < ROWS; r++) {
            int yy = (int) ((r + 0.5) / ROWS * h);
            for (int c = 0; c < COLS; c++) {
                int xc = (int) ((c + 0.5) / COLS * w);
                double re = 0, im = 0;
                for (int k = -HALF; k <= HALF; k++) {
                    int xx = Math.floorMod(xc + k, w); // wrap angular (iris is circular)
                    int px = strip[yy * w + xx] & 0xFF;
                    double v = (px - 128) / 128.0;
                    re += v * gReal[k + HALF];
                    im += v * gImag[k + HALF];
                }
                if (re >= 0) code[bitIndex >> 3] |= (byte) (1 << (bitIndex & 7));
                bitIndex++;
                if (im >= 0) code[bitIndex >> 3] |= (byte) (1 << (bitIndex & 7));
                bitIndex++;
            }
        }
        return code;
    }

    static double fractionalHamming(byte[] a, byte[] b) {
        int diff = 0, total = a.length * 8;
        for (int i = 0; i < a.length; i++) {
            diff += Integer.bitCount((a[i] ^ b[i]) & 0xFF);
        }
        return (double) diff / total;
    }

    private double confidence(double hd) {
        // HD 0 → 1.0, HD at threshold → ~0.5, HD 0.5 (random) → →0.
        double x = (maxHammingForMatch - hd) / Math.max(1e-6, maxHammingForMatch);
        return Math.max(0, Math.min(1, 0.5 + 0.5 * x));
    }
}
