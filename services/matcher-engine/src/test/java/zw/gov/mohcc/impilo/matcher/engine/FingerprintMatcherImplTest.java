package zw.gov.mohcc.impilo.matcher.engine;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Proves the fingerprint matcher does REAL discrimination, not hash-equality: a
 * matching finger scores far above the threshold, a different finger far below,
 * and the 1:N gallery returns the right subject. Uses synthesized ridge patterns
 * (seeded → distinct "fingers"); the property that matters is
 * self-score >> threshold >> cross-score, which a stub could never satisfy.
 */
class FingerprintMatcherImplTest {

    private final FingerprintMatcherImpl matcher = new FingerprintMatcherImpl(40, 500);

    /**
     * Raw 8-bit grayscale ridge-like pattern; same seed = same finger. Each seed
     * builds an UNCORRELATED ridge field from several random-direction/phase plane
     * waves plus seed-random minutiae dropouts, so two different seeds share almost
     * no minutiae structure (real distinct fingers score near-zero cross). Avoids
     * the shared concentric base that made naive synthetic prints falsely similar.
     */
    private byte[] print(long seed) {
        int w = 400, h = 400;
        byte[] px = new byte[w * h];
        Random r = new Random(seed);
        int waves = 4;
        double[] kx = new double[waves], ky = new double[waves], ph = new double[waves];
        for (int i = 0; i < waves; i++) {
            double dir = r.nextDouble() * Math.PI;                 // seed-random orientation
            double freq = 0.18 + r.nextDouble() * 0.14;            // seed-random ridge frequency
            kx[i] = Math.cos(dir) * freq;
            ky[i] = Math.sin(dir) * freq;
            ph[i] = r.nextDouble() * 2 * Math.PI;
        }
        // Seed-random low-frequency warp so ridge flow bends differently per finger.
        double wkx = (r.nextDouble() - 0.5) * 0.02, wky = (r.nextDouble() - 0.5) * 0.02, wph = r.nextDouble() * 6.28;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                double warp = 3.0 * Math.sin(x * wkx + y * wky + wph);
                double v = 0;
                for (int i = 0; i < waves; i++) {
                    v += Math.sin(x * kx[i] + y * ky[i] + ph[i] + warp);
                }
                int g = (int) (127 + 90 * Math.sin(v));
                px[y * w + x] = (byte) Math.max(0, Math.min(255, g));
            }
        }
        // Seed-random minutiae: small ridge breaks scattered uniquely per finger.
        int minutiae = 60;
        for (int i = 0; i < minutiae; i++) {
            int mx = 20 + r.nextInt(w - 40), my = 20 + r.nextInt(h - 40), rad = 3 + r.nextInt(4);
            byte val = (byte) (r.nextBoolean() ? 255 : 0);
            for (int yy = -rad; yy <= rad; yy++) {
                for (int xx = -rad; xx <= rad; xx++) {
                    if (xx * xx + yy * yy <= rad * rad) {
                        px[(my + yy) * w + (mx + xx)] = val;
                    }
                }
            }
        }
        return px;
    }

    private byte[] template(long seed) {
        ModalityMatcher.ExtractionResult r = matcher.extract(print(seed), 400, 400, 500);
        assertTrue(r.ok(), "extraction must succeed");
        assertTrue(r.template().length > 0);
        return r.template();
    }

    @Test
    @DisplayName("same finger → MATCH well above threshold; different finger → NO_MATCH")
    void discriminates() {
        byte[] enrolled = template(11);
        byte[] sameFinger = template(11);
        byte[] otherFinger = template(97);

        ModalityMatcher.VerificationResult self = matcher.verify(sameFinger, enrolled);
        ModalityMatcher.VerificationResult cross = matcher.verify(otherFinger, enrolled);

        assertEquals("MATCH", self.result(), "same finger must MATCH, score=" + self.score());
        assertEquals("NO_MATCH", cross.result(), "different finger must NOT match, score=" + cross.score());
        assertTrue(self.score() > cross.score(), "self must outscore cross");
        assertTrue(self.confidence() > 0.5 && cross.confidence() < 0.5);
    }

    @Test
    @DisplayName("1:N identify returns the correct subject as top candidate, none for a stranger")
    void identifyFindsTheEnrolledSubject() {
        List<ModalityMatcher.GalleryEntry> gallery = List.of(
                new ModalityMatcher.GalleryEntry("subject-A", template(11)),
                new ModalityMatcher.GalleryEntry("subject-B", template(97)),
                new ModalityMatcher.GalleryEntry("subject-C", template(53)));

        List<ModalityMatcher.Candidate> hits = matcher.identify(template(11), gallery, 5);
        assertFalse(hits.isEmpty(), "the enrolled subject must be found");
        assertEquals("subject-A", hits.get(0).subjectRef(), "top candidate must be the matching subject");

        List<ModalityMatcher.Candidate> stranger = matcher.identify(template(777), gallery, 5);
        assertTrue(stranger.isEmpty(), "a stranger must return no candidates");
    }

    @Test
    @DisplayName("missing/empty templates fail closed (UNAVAILABLE), never a fabricated match")
    void failsClosedOnMissingInput() {
        assertEquals("UNAVAILABLE", matcher.verify(new byte[0], template(11)).result());
        assertEquals("UNAVAILABLE", matcher.verify(template(11), null).result());
        assertTrue(matcher.identify(new byte[0], List.of(), 5).isEmpty());
    }
}
