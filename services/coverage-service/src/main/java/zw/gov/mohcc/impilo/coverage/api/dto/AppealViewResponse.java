package zw.gov.mohcc.impilo.coverage.api.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AppealViewResponse(
        UUID id,
        String claimId,
        String reason,
        String status,
        String decision,
        String decisionNotes,
        OffsetDateTime createdAt) {}
