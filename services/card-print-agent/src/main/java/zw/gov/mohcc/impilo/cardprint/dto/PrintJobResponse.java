package zw.gov.mohcc.impilo.cardprint.dto;

import zw.gov.mohcc.impilo.cardprint.entity.PrintJobEntity;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Response payload for a print job.
 */
public record PrintJobResponse(
        UUID jobId,
        UUID tenantId,
        String jobType,
        String subjectType,
        String subjectId,
        String subjectName,
        String status,
        String templateName,
        UUID renderedPdfDocumentId,
        int priority,
        String requestedBy,
        OffsetDateTime printedAt,
        OffsetDateTime collectedAt,
        OffsetDateTime createdAt
) {
    /**
     * Maps a PrintJobEntity to a PrintJobResponse.
     */
    public static PrintJobResponse from(PrintJobEntity entity) {
        return new PrintJobResponse(
                entity.getJobId(),
                entity.getTenantId(),
                entity.getJobType().name(),
                entity.getSubjectType(),
                entity.getSubjectId(),
                entity.getSubjectName(),
                entity.getStatus().name(),
                entity.getTemplateName(),
                entity.getRenderedPdfDocumentId(),
                entity.getPriority(),
                entity.getRequestedBy(),
                entity.getPrintedAt(),
                entity.getCollectedAt(),
                entity.getCreatedAt()
        );
    }
}
