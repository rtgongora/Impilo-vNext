package zw.gov.mohcc.impilo.datagovernance.api.dto;

import zw.gov.mohcc.impilo.datagovernance.domain.DataSubjectRequestEntity;

/** Canonical view of a data-subject rights request. */
public record DataSubjectRequestResponse(
        String requestId,
        String subjectId,
        String requestType,
        String status,
        String reason,
        String requestedAt,
        String updatedAt,
        String cancelledAt,
        String completedAt
) {
    public static DataSubjectRequestResponse from(DataSubjectRequestEntity e) {
        return new DataSubjectRequestResponse(
                e.getRequestId().toString(),
                e.getSubjectId(),
                e.getRequestType(),
                e.getStatus(),
                e.getReason(),
                e.getRequestedAt().toString(),
                e.getUpdatedAt().toString(),
                e.getCancelledAt() != null ? e.getCancelledAt().toString() : null,
                e.getCompletedAt() != null ? e.getCompletedAt().toString() : null);
    }
}
