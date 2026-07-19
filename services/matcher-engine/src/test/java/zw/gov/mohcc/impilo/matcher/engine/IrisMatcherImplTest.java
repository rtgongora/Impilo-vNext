package zw.gov.mohcc.impilo.matcher.engine;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Iris matching is real (Gabor iris-code + fractional Hamming). Proves the
 * discrimination property on synthetic normalised strips: the same iris (same
 * seed) yields a low Hamming distance (MATCH), a different iris a near-random HD
 * (NO_MATCH), and 1:N returns the right subject. A stub could not do this.
 */
class IrisMatcherImplTest {

    private final IrisMatcherImpl matcher = new IrisMatcherImpl(0.35);

    /** Synthetic normalised iris strip; same seed = same iris. Textured + seed noise. */
    private byte[] strip(long seed) {
        int w = 256, h = 32;
        byte[] px = new byte[w * h];
        Random r = new Random(seed);
        int bands = 5;
        double[] f = new double[bands], ph = new double[bands];
        for (int i = 0; i < bands; i++) {
            f[i] = 0.05 + r.nextDouble() * 0.25;
            ph[i] = r.nextDouble() * 6.28;
        }
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                double v = 0;
                for (int i = 0; i < bands; i++) {
                    v += Math.sin(x * f[i] + ph[i] + y * 0.1);
                }
                int g = (int) (128 + 60 * Math.sin(v));
                px[y * w + x] = (byte) Math.max(0, Math.min(255, g));
            }
        }
        return px;
    }

    private byte[] code(long seed) {
        ModalityMatcher.ExtractionResult r = matcher.extract(strip(seed), 256, 32, 0);
        assertTrue(r.ok(), "iris extraction must succeed");
        return r.template();
    }

    @Test
    @DisplayName("same iris → low Hamming (MATCH); different iris → high Hamming (NO_MATCH)")
    void discriminates() {
        byte[] enrolled = code(11);
        ModalityMatcher.VerificationResult self = matcher.verify(code(11), enrolled);
        ModalityMatcher.VerificationResult cross = matcher.verify(code(97), enrolled);

        assertEquals("MATCH", self.result(), "same-iris HD=" + self.score());
        assertEquals("NO_MATCH", cross.result(), "diff-iris HD=" + cross.score());
        assertTrue(self.score() < cross.score(), "same-iris HD must be lower than diff-iris HD");
    }

    @Test
    @DisplayName("1:N identify returns the enrolled iris; a stranger returns none")
    void identifyFindsSubject() {
        List<ModalityMatcher.GalleryEntry> gallery = List.of(
                new ModalityMatcher.GalleryEntry("A", code(11)),
                new ModalityMatcher.GalleryEntry("B", code(97)),
                new ModalityMatcher.GalleryEntry("C", code(53)));

        List<ModalityMatcher.Candidate> hits = matcher.identify(code(11), gallery, 5);
        assertFalse(hits.isEmpty());
        assertEquals("A", hits.get(0).subjectRef());

        assertTrue(matcher.identify(code(4242), gallery, 5).isEmpty(), "stranger → no candidates");
    }

    @Test
    @DisplayName("missing/mismatched iris codes fail closed")
    void failsClosed() {
        assertEquals("UNAVAILABLE", matcher.verify(new byte[0], code(11)).result());
        assertFalse(matcher.extract(null, 0, 0, 0).ok());
    }
}
