package zw.gov.mohcc.impilo.oros.api.dto;

import jakarta.validation.constraints.NotNull;

import java.time.OffsetDateTime;

/**
 * Request DTO for scheduling a diagnostic/imaging order's acquisition/appointment.
 *
 * <p>Sets {@code scheduledAt} on the order and drives the imaging workflow state to
 * {@code SCHEDULED} (guarded by {@link zw.gov.mohcc.impilo.oros.core.ImagingWorkflow}).</p>
 */
public record ScheduleRequest(
        @NotNull OffsetDateTime scheduledAt,
        String note
) {}
