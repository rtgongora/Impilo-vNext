package zw.gov.mohcc.impilo.search.api.dto;

import jakarta.validation.constraints.NotBlank;

public record SearchRequest(
        @NotBlank String query,
        String indexId,
        Integer page,
        Integer size
) {
    public int pageOrDefault() { return page != null && page >= 0 ? page : 0; }
    public int sizeOrDefault() { return size != null && size > 0 ? Math.min(size, 100) : 20; }
}
