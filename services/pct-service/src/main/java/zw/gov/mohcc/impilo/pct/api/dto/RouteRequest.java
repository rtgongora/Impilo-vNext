package zw.gov.mohcc.impilo.pct.api.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Request to route a patient journey to a specific queue.
 *
 * <p>After triage, the routing engine determines which queue
 * the patient should join based on clinical need and facility
 * configuration. This request allows manual override routing.</p>
 *
 * @param targetQueueId the queue to route the patient to
 * @param priority      optional priority override; higher values indicate higher priority
 */
public record RouteRequest(
        @NotNull UUID targetQueueId,
        Integer priority
) {}
