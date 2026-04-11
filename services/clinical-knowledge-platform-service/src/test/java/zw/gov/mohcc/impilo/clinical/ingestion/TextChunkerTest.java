package zw.gov.mohcc.impilo.clinical.ingestion;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TextChunkerTest {

    @Test
    void shortTextSingleChunk() {
        assertThat(TextChunker.chunk("hello", 100, 10)).containsExactly("hello");
    }

    @Test
    void splitsLongText() {
        String s = "a".repeat(2500);
        List<String> parts = TextChunker.chunk(s, 1000, 100);
        assertThat(parts.size()).isGreaterThan(1);
        assertThat(parts).allMatch(p -> p.length() <= 1000);
    }
}
