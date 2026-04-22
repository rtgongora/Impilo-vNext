package zw.gov.mohcc.impilo.msika.api.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public final class GovernanceDtos {

    private GovernanceDtos() {}

    public record CreateGovernanceRecordRequest(
            UUID tenantId,
            String targetType,
            String targetId,
            String reviewType,
            String notes,
            Object evidenceRefs
    ) {}

    public record ReviewActionRequest(
            String status,
            String decision,
            String notes
    ) {}

    public record GovernanceRecordView(
            String recordId,
            UUID tenantId,
            String targetType,
            String targetId,
            String reviewType,
            String status,
            String reviewerRef,
            String decision,
            String notes,
            Object evidenceRefs,
            OffsetDateTime reviewedAt,
            String createdBy,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt
    ) {}
}

