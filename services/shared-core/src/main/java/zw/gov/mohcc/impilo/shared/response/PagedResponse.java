package zw.gov.mohcc.impilo.shared.response;

import java.util.List;

/**
 * Paginated data wrapper used inside {@link ApiResponse#data()}.
 *
 * Usage: ApiResponse.ok(PagedResponse.of(items, page, size, totalElements), correlationId)
 */
public record PagedResponse<T>(
        List<T> items,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasNext
) {
    public static <T> PagedResponse<T> of(List<T> items, int page, int size, long totalElements) {
        int totalPages = size > 0 ? (int) Math.ceil((double) totalElements / size) : 0;
        boolean hasNext = page < totalPages - 1;
        return new PagedResponse<>(items, page, size, totalElements, totalPages, hasNext);
    }


}
