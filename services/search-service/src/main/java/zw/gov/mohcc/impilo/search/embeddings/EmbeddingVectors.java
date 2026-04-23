package zw.gov.mohcc.impilo.search.embeddings;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Locale;

public final class EmbeddingVectors {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private EmbeddingVectors() {
    }

    public static String toJson(float[] v) {
        if (v == null || v.length == 0) {
            return null;
        }
        try {
            return MAPPER.writeValueAsString(v);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    public static float[] fromJson(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            JsonNode root = MAPPER.readTree(json);
            if (!root.isArray() || root.isEmpty()) {
                return null;
            }
            float[] v = new float[root.size()];
            int i = 0;
            for (JsonNode n : root) {
                v[i++] = (float) n.asDouble();
            }
            return v;
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    /** Cosine similarity after padding both vectors to the same length (L2-normalized). */
    public static double cosineAligned(float[] a, float[] b) {
        if (a == null || b == null) {
            return Double.NaN;
        }
        int dim = Math.max(a.length, b.length);
        float[] pa = padOrTruncate(a, dim);
        float[] pb = padOrTruncate(b, dim);
        if (pa == null || pb == null) {
            return Double.NaN;
        }
        return cosine(pa, pb);
    }

    /** Cosine similarity for L2-normalized vectors (dot product). */
    public static double cosine(float[] a, float[] b) {
        if (a == null || b == null || a.length != b.length || a.length == 0) {
            return Double.NaN;
        }
        double dot = 0.0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
        }
        return dot;
    }

    /**
     * Pads with zeros or truncates so vectors fit a fixed pgvector column width.
     * Returns L2-normalized copy (or {@code null} if unusable).
     */
    public static float[] padOrTruncate(float[] v, int dimension) {
        if (v == null || dimension < 1) {
            return null;
        }
        float[] out = new float[dimension];
        int n = Math.min(v.length, dimension);
        System.arraycopy(v, 0, out, 0, n);
        return l2Normalize(out);
    }

    public static float[] l2Normalize(float[] v) {
        if (v == null || v.length == 0) {
            return null;
        }
        double sumSq = 0.0;
        for (float x : v) {
            sumSq += (double) x * x;
        }
        double norm = Math.sqrt(sumSq);
        if (norm < 1e-12) {
            return null;
        }
        float[] out = new float[v.length];
        for (int i = 0; i < v.length; i++) {
            out[i] = (float) (v[i] / norm);
        }
        return out;
    }

    public static String truncate(String text, int maxChars) {
        if (text == null) {
            return "";
        }
        if (text.length() <= maxChars) {
            return text;
        }
        return text.substring(0, maxChars);
    }

    public static String normalizeWhitespace(String text) {
        if (text == null) {
            return "";
        }
        return text.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }
}
