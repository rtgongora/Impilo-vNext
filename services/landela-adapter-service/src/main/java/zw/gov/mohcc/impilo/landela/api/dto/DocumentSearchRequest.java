package zw.gov.mohcc.impilo.landela.api.dto;

/**
 * Query parameters for searching documents.
 */
public record DocumentSearchRequest(
        String subjectType,
        String subjectId,
        String documentTypeCode,
        String lifecycleState,
        int page,
        int size
) {
    public DocumentSearchRequest {
        if (page < 0) page = 0;
        if (size <= 0 || size > 100) size = 20;
    }
}
