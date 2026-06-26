package zw.gov.mohcc.impilo.patientsafety.api.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Compact case row for the MCAZ incoming / priority queues. */
public record CaseSummary(
        UUID id,
        String caseReference,
        String status,
        String priority,
        String reportType,
        boolean serious,
        String causalityAssessment,
        String assignedReviewerId,
        String reportReference,
        String subjectInitials,
        String primaryProductName,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {}
