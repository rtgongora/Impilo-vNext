package zw.gov.mohcc.impilo.search.api;

import java.util.List;

public record SearchResponse(
        List<IndexResponse> results,
        int page,
        int size,
        long totalElements,
        int totalPages
) {}
