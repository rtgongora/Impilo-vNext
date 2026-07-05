package zw.gov.mohcc.impilo.pct.api.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.UUID;

/**
 * Request to escalate a queue item.
 *
 * <p>Escalation raises the item's urgency (default bump to at least the top
 * of the 1–5 triage-derived scale) and records who escalated and why.
 * Optionally the item is transferred to a target queue at the same time.</p>
 *
 * @param reason        mandatory operational reason for the escalation
 * @param targetQueueId optional destination queue (e.g. emergency workspace)
 * @param priority      optional explicit priority override
 */
public record EscalateQueueItemRequest(
        @NotBlank String reason,
        UUID targetQueueId,
        Integer priority
) {}
