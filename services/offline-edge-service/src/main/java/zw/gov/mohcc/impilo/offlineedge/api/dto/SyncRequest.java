package zw.gov.mohcc.impilo.offlineedge.api.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Request body for {@code POST /internal/v1/offline/sync}.
 *
 * <p>Pushes a batch of offline-captured actions for reconciliation.
 * The entitlement is verified via the {@code X-Offline-Entitlement} header (JWT).</p>
 */
public record SyncRequest(
        @NotNull UUID batchId,
        String deviceId,
        @NotEmpty List<SyncActionEntry> actions
) {
    public record SyncActionEntry(
            @NotNull UUID entryId,
            @NotNull String patientRef,
            @NotNull String actionType,
            @NotNull Map<String, Object> payload,
            @NotNull String capturedAt,
            String deviceId,
            int sequenceNum,
            String idempotencyKey,
            String correlationId
    ) {}
}
