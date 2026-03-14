package zw.gov.mohcc.impilo.offline.sdk.queue;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * A batch of offline action entries ready for synchronization.
 *
 * <p>This is the wire format for {@code POST /internal/v1/offline/sync}.
 * An edge device accumulates {@link OfflineActionEntry} instances during an
 * offline session and submits them as a batch when connectivity is restored.</p>
 *
 * @param batchId      client-generated batch identifier
 * @param tokenId      capability token that authorized these actions
 * @param signedToken  the JWS-signed entitlement token for batch verification
 * @param deviceId     originating device
 * @param facilityId   facility scope
 * @param tenantId     tenant scope
 * @param submittedAt  when the batch was submitted for sync
 * @param entries      ordered list of offline action entries
 */
public record OfflineSyncBatch(
        UUID batchId,
        UUID tokenId,
        String signedToken,
        String deviceId,
        UUID facilityId,
        UUID tenantId,
        Instant submittedAt,
        List<OfflineActionEntry> entries
) {
    public OfflineSyncBatch {
        Objects.requireNonNull(batchId, "batchId is required");
        Objects.requireNonNull(tokenId, "tokenId is required");
        Objects.requireNonNull(signedToken, "signedToken is required for batch verification");
        Objects.requireNonNull(tenantId, "tenantId is required");
        Objects.requireNonNull(entries, "entries is required");
        if (entries.isEmpty()) {
            throw new IllegalArgumentException("entries must not be empty");
        }
        entries = List.copyOf(entries);
    }
}
