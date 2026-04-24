package zw.gov.mohcc.impilo.search.pgvector;

import org.junit.jupiter.api.Test;
import zw.gov.mohcc.impilo.search.embeddings.EmbeddingVectors;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PgvectorAnnSupportTest {

    @Test
    void mergeAnnThenLexical_preserves_ann_order_then_appends() {
        List<String> ann = List.of("a", "b", "c");
        List<String> lex = List.of("b", "d");
        List<String> merged = PgvectorAnnSupport.mergeAnnThenLexical(ann, lex);
        assertThat(merged).containsExactly("a", "b", "c", "d");
    }

    @Test
    void toVectorLiteral_produces_pgvector_bracket_syntax() {
        float[] v = EmbeddingVectors.padOrTruncate(new float[] {0.5f, -0.25f}, 3);
        String lit = PgvectorAnnSupport.toVectorLiteral(v);
        assertThat(lit).startsWith("[").endsWith("]");
        assertThat(lit).contains(",");
    }
}
