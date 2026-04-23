package zw.gov.mohcc.impilo.search.api;

import java.util.List;

/**
 * @param rankModeApplied when non-null, describes server-side ranking applied:
 *                        {@code lexical};
 *                        {@code semantic-cosine} when query + documents have stored embeddings;
 *                        {@code semantic-proxy-lexical} when embeddings are unavailable or documents lack vectors;
 *                        {@code hybrid-cosine-lexical} / {@code hybrid-proxy-lexical} for {@code rankMode=hybrid};
 *                        {@code semantic-pgvector-ann} / {@code hybrid-pgvector-ann} when ANN recall is active.
 */
public record SearchResponse(
        List<IndexResponse> results,
        int page,
        int size,
        long totalElements,
        int totalPages,
        String rankModeApplied
) {
    public SearchResponse(List<IndexResponse> results, int page, int size, long totalElements, int totalPages) {
        this(results, page, size, totalElements, totalPages, null);
    }
}
