package zw.gov.mohcc.impilo.pct.api.dto;

import jakarta.validation.constraints.NotBlank;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Request to create a clinical or administrative task.
 *
 * <p>Tasks are scoped to a journey and optionally to an encounter
 * and workspace. They can be assigned to a specific provider or
 * left unassigned for role-based pickup.</p>
 *
 * @param journeyId    the journey this task relates to
 * @param encounterId  the encounter this task relates to; may be {@code null}
 * @param taskType     the type of task (e.g. LAB_REVIEW, PRESCRIPTION, REFERRAL)
 * @param assigneeId   the assigned provider identifier; may be {@code null}
 * @param assigneeRole the role of the assignee (e.g. DOCTOR, NURSE); may be {@code null}
 * @param workspaceId  the workspace where the task should be performed; may be {@code null}
 * @param dueAt        when the task is due; may be {@code null}
 * @param notes        free-text notes about the task; may be {@code null}
 */
public record CreateTaskRequest(
        String journeyId,
        Long encounterId,
        @NotBlank String taskType,
        String assigneeId,
        String assigneeRole,
        UUID workspaceId,
        OffsetDateTime dueAt,
        String notes
) {}
