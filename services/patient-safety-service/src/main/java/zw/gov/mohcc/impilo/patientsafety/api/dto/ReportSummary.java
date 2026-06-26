package zw.gov.mohcc.impilo.patientsafety.api.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Compact report row for list views (my-reports, facility dashboard). */
public record ReportSummary(
        UUID id,
        String referenceCode,
        String reportType,
        String reporterKind,
        String status,
        String subjectCpid,
        String subjectInitials,
        boolean serious,
        String primaryProductName,
        String primaryReactionTerm,
        LocalDate onsetDate,
        OffsetDateTime createdAt,
        OffsetDateTime submittedAt
) {}
