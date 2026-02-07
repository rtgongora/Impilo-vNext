package zw.gov.mohcc.impilo.oros.api.dto;

import zw.gov.mohcc.impilo.oros.domain.WorkstepStatus;
import zw.gov.mohcc.impilo.oros.domain.WorkstepType;
import zw.gov.mohcc.impilo.oros.persistence.entity.WorkstepEntity;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * DTO for workstep data in API responses.
 */
public record WorkstepDto(
        UUID workstepId,
        String orderId,
        WorkstepType stepType,
        WorkstepStatus status,
        String assignedTo,
        OffsetDateTime startedAt,
        OffsetDateTime completedAt,
        String notes,
        OffsetDateTime createdAt
) {
    /**
     * Factory method to create a DTO from a workstep entity.
     */
    public static WorkstepDto from(WorkstepEntity entity) {
        return new WorkstepDto(
                entity.getWorkstepId(),
                entity.getOrderId(),
                entity.getStepType(),
                entity.getStatus(),
                entity.getAssignedTo(),
                entity.getStartedAt(),
                entity.getCompletedAt(),
                entity.getNotes(),
                entity.getCreatedAt()
        );
    }
}
