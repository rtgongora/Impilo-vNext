package zw.gov.mohcc.impilo.integration.api.dto;

import java.time.OffsetDateTime;

public record DeadLetterResponse(
        String id,
        String dispatchAttemptId,
        String routeId,
        String method,
        String path,
        String errorReason,
        int retryCount,
        OffsetDateTime lastRetryAt,
        boolean resolved,
        OffsetDateTime createdAt
) {
}
