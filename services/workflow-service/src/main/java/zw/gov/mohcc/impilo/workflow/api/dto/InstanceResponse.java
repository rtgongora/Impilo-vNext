package zw.gov.mohcc.impilo.workflow.api.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record InstanceResponse(UUID instanceId, UUID definitionId, UUID tenantId, String status,
                                 String currentStep, Object context, String initiatorRef, String subjectRef,
                                 OffsetDateTime startedAt, OffsetDateTime completedAt,
                                 OffsetDateTime createdAt, OffsetDateTime updatedAt) {}
