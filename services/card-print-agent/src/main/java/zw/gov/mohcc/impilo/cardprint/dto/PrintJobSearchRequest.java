package zw.gov.mohcc.impilo.cardprint.dto;

/**
 * Query parameters for searching print jobs.
 *
 * @param status      optional status filter
 * @param subjectType optional subject type filter
 * @param subjectId   optional subject id filter
 * @param page        page number (0-based, default 0)
 * @param size        page size (default 20)
 */
public record PrintJobSearchRequest(
        String status,
        String subjectType,
        String subjectId,
        Integer page,
        Integer size
) {
    public PrintJobSearchRequest {
        if (page == null) {
            page = 0;
        }
        if (size == null) {
            size = 20;
        }
    }
}
