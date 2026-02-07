package zw.gov.mohcc.impilo.pct.api.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Request to transfer a queue item to a different queue.
 *
 * <p>Moves a patient from one queue to another, preserving the
 * journey context. The item in the source queue is marked as
 * TRANSFERRED and a new item is created in the target queue.</p>
 *
 * @param targetQueueId the queue to transfer the item to
 */
public record TransferQueueItemRequest(
        @NotNull UUID targetQueueId
) {}
