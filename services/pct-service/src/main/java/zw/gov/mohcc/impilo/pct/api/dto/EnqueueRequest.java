package zw.gov.mohcc.impilo.pct.api.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request to enqueue a patient journey into a specific queue.
 *
 * <p>Creates a new queue item with the specified priority. A token
 * number is assigned automatically by the queue engine.</p>
 *
 * @param journeyId the patient journey identifier to enqueue
 * @param priority  the priority level for ordering (higher = sooner)
 */
public record EnqueueRequest(
        @NotBlank String journeyId,
        int priority
) {}
