package zw.gov.mohcc.impilo.search.embeddings;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

/**
 * Deterministic embedding from character/word features — useful for CI and environments
 * without an external embedding API. Not a substitute for trained sentence models in production.
 */
public final class HashEmbeddingModel implements EmbeddingModel {

    private final int dimension;

    public HashEmbeddingModel(int dimension) {
        if (dimension < 8) {
            throw new IllegalArgumentException("dimension must be >= 8");
        }
        this.dimension = dimension;
    }

    @Override
    public float[] embed(String text) {
        String norm = EmbeddingVectors.normalizeWhitespace(text);
        if (norm.isEmpty()) {
            return null;
        }
        float[] v = new float[dimension];
        for (String gram : grams(norm)) {
            long h = fnv1a64(gram.getBytes(StandardCharsets.UTF_8));
            for (int d = 0; d < dimension; d++) {
                double t = (h + (long) (d + 1) * 0x9E3779B97F4A7C15L) * (Math.PI / dimension);
                v[d] += (float) Math.sin(t);
            }
        }
        return EmbeddingVectors.l2Normalize(v);
    }

    private static List<String> grams(String norm) {
        List<String> out = new ArrayList<>();
        var tok = new StringTokenizer(norm, " ");
        while (tok.hasMoreTokens()) {
            out.add(tok.nextToken());
        }
        if (out.isEmpty()) {
            return List.of(norm);
        }
        for (int i = 0; i < norm.length() - 2; i++) {
            out.add(norm.substring(i, Math.min(i + 4, norm.length())));
        }
        return out;
    }

    private static long fnv1a64(byte[] data) {
        long hash = 0xcbf29ce484222325L;
        for (byte b : data) {
            hash ^= b & 0xff;
            hash *= 0x100000001b3L;
        }
        return hash;
    }
}
