package zw.gov.mohcc.impilo.search.embeddings;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EmbeddingVectorsTest {

    @Test
    void padOrTruncate_preserves_short_vectors_in_larger_space() {
        float[] a = new float[] {1f, 0f, 0f};
        float[] p = EmbeddingVectors.padOrTruncate(a, 8);
        assertThat(p).hasSize(8);
        assertThat(EmbeddingVectors.cosine(p, p)).isGreaterThan(0.999);
    }

    @Test
    void cosineAligned_mismatched_lengths() {
        float[] a = new float[] {1f, 0f};
        float[] b = new float[] {1f, 0f, 0f, 0f};
        double c = EmbeddingVectors.cosineAligned(a, b);
        assertThat(c).isGreaterThan(0.99);
    }
}
