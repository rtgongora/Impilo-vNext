package zw.gov.mohcc.impilo.search.embeddings;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HashEmbeddingModelTest {

    @Test
    void embed_produces_normalized_vectors_with_stable_cosine() {
        HashEmbeddingModel m = new HashEmbeddingModel(64);
        float[] a = m.embed("patient diabetes follow-up");
        float[] b = m.embed("patient diabetes follow-up");
        float[] c = m.embed("facility inventory spreadsheet");

        assertThat(a).isNotNull();
        assertThat(b).isNotNull();
        assertThat(c).isNotNull();
        assertThat(a.length).isEqualTo(64);

        double ab = EmbeddingVectors.cosine(a, b);
        double ac = EmbeddingVectors.cosine(a, c);
        assertThat(ab).isGreaterThan(0.999);
        assertThat(ac).isLessThan(0.999);
    }
}
