package zw.gov.mohcc.impilo.obs.core;

import java.time.OffsetDateTime;

/**
 * Disclosure-limited status of a single service for public consumption.
 *
 * <p>Doctrine: honest status only. Exposes ONLY the service name, the derived
 * UP/STALE liveness (never a raw stored status string, never invented
 * DEGRADED/DOWN), and the last heartbeat time. It deliberately omits instance
 * ids, tenant, version tags, metadata, and instance counts.</p>
 */
public record PublicServiceStatusItem(
        String serviceName,
        String status,
        OffsetDateTime lastHeartbeat) {
}
