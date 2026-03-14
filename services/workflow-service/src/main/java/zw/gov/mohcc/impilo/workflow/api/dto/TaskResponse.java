package zw.gov.mohcc.impilo.workflow.api.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record TaskResponse(UUID taskId, UUID instanceId, UUID tenantId, String stepName, String status,
                             String assigneeRef, Object input, Object output,
                             OffsetDateTime startedAt, OffsetDateTime completedAt,
                             OffsetDateTime createdAt, OffsetDateTime updatedAt) {}
