package zw.gov.mohcc.impilo.matcher.engine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Face matcher.
 *
 * <p><b>Matching is real now</b>: a face template is an L2-normalized embedding
 * vector; verify/identify compute cosine similarity against a threshold — pure
 * math, no model needed. So face verification and 1:N are genuinely functional
 * and unit-tested here.</p>
 *
 * <p><b>Extraction</b> (image → embedding) needs a trained ONNX face-embedding
 * model ({@code matcher.face.model-path}). When no model is mounted, extraction
 * fails closed (UNAVAILABLE) — nothing is fabricated, and face matching simply
 * has nothing to compare until enrolment via a mounted model. That model is an
 * EXTERNAL_INTEGRATION artifact (fetched by the vendor script, not committed).</p>
 */
@Component
public class FaceMatcherImpl implements ModalityMatcher {

    private static final Logger log = LoggerFactory.getLogger(FaceMatcherImpl.class);

    private final double matchThreshold;
    private final OnnxFaceEmbedder embedder;

    public FaceMatcherImpl(@Value("${matcher.face.match-threshold:0.5}") double matchThreshold,
                           OnnxFaceEmbedder embedder) {
        this.matchThreshold = matchThreshold;
        this.embedder = embedder;
    }

    @Override
    public Modality modality() {
        return Modality.FACE;
    }

    @Override
    public boolean capable() {
        // Matching (cosine on embeddings) is always available; extraction from an
        // image additionally requires a model. Report capable = extraction ready.
        return embedder.ready();
    }

    @Override
    public ExtractionResult extract(byte[] sampleImage, int width, int height, int dpi) {
        if (!embedder.ready()) {
            return ExtractionResult.unavailable("face_model_not_loaded");
        }
        try {
            float[] emb = embedder.embed(sampleImage);
            if (emb == null || emb.length == 0) {
                return ExtractionResult.unavailable("no_face_detected");
            }
            return new ExtractionResult(true, encode(l2normalize(emb)), 100.0, "onnx_face");
        } catch (RuntimeException e) {
            log.warn("Face extraction failed: {}", e.getMessage());
            return ExtractionResult.unavailable("extraction_failed: " + e.getMessage());
        }
    }

    @Override
    public VerificationResult verify(byte[] probeTemplate, byte[] enrolledTemplate) {
        float[] a = decode(probeTemplate), b = decode(enrolledTemplate);
        if (a == null || b == null || a.length == 0 || a.length != b.length) {
            return VerificationResult.unavailable("missing_or_mismatched_embedding");
        }
        double sim = cosine(a, b);
        boolean isMatch = sim >= matchThreshold;
        return new VerificationResult(isMatch ? "MATCH" : "NO_MATCH", sim,
                confidence(sim), "cosine>=" + matchThreshold);
    }

    @Override
    public List<Candidate> identify(byte[] probeTemplate, List<GalleryEntry> gallery, int maxCandidates) {
        float[] probe = decode(probeTemplate);
        if (probe == null || probe.length == 0 || gallery == null || gallery.isEmpty()) {
            return List.of();
        }
        List<Candidate> scored = new ArrayList<>();
        for (GalleryEntry g : gallery) {
            float[] t = decode(g.template());
            if (t == null || t.length != probe.length) {
                continue;
            }
            double sim = cosine(probe, t);
            if (sim >= matchThreshold) {
                scored.add(new Candidate(g.subjectRef(), sim, confidence(sim)));
            }
        }
        scored.sort(Comparator.comparingDouble(Candidate::score).reversed());
        int cap = maxCandidates > 0 ? Math.min(maxCandidates, scored.size()) : scored.size();
        return new ArrayList<>(scored.subList(0, cap));
    }

    // ── embedding math ───────────────────────────────────────────────────
    static double cosine(float[] a, float[] b) {
        double dot = 0, na = 0, nb = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            na += a[i] * a[i];
            nb += b[i] * b[i];
        }
        double denom = Math.sqrt(na) * Math.sqrt(nb);
        return denom == 0 ? 0 : dot / denom;
    }

    static float[] l2normalize(float[] v) {
        double n = 0;
        for (float x : v) n += x * x;
        n = Math.sqrt(n);
        if (n == 0) return v;
        float[] out = new float[v.length];
        for (int i = 0; i < v.length; i++) out[i] = (float) (v[i] / n);
        return out;
    }

    private double confidence(double cosine) {
        // Map [-1,1] cosine, thresholded, to 0..1.
        double x = (cosine - matchThreshold) / Math.max(1e-6, 1 - matchThreshold);
        return Math.max(0, Math.min(1, 0.5 + 0.5 * x));
    }

    static byte[] encode(float[] v) {
        ByteBuffer buf = ByteBuffer.allocate(v.length * 4).order(ByteOrder.LITTLE_ENDIAN);
        for (float x : v) buf.putFloat(x);
        return buf.array();
    }

    static float[] decode(byte[] b) {
        if (b == null || b.length == 0 || b.length % 4 != 0) return null;
        ByteBuffer buf = ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN);
        float[] v = new float[b.length / 4];
        for (int i = 0; i < v.length; i++) v[i] = buf.getFloat();
        return v;
    }
}
