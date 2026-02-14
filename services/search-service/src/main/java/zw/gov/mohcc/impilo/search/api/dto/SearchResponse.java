package zw.gov.mohcc.impilo.search.api.dto;

import java.util.List;

public record SearchResponse(
        List<SearchHit> hits,
        long totalHits,
        int page,
        int size
) {
    public record SearchHit(
            String documentId,
            String indexId,
            String externalId,
            String title,
            String snippet,
            double score,
            Object metadata
    ) {}
}
