package zw.gov.mohcc.impilo.offline.sdk.queue;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Canonical format for a single offline-captured action entry.
 *
 * <p>This is the wire format for actions captured on edge devices while offline.
 * Each entry is self-contained with enough context for the edge service to:
 * <ul>
 *   <li>Verify the entitlement (via tokenId + signedToken)</li>
 *   <li>Audit the action (via actor, device, timestamp, correlation)</li>
 *   <li>Replay the action when online (via actionType + payload)</li>
 *   <li>Ensure idempotency (via idempotencyKey)</li>
 * </ul>
 *
 * @param entryId         client-generated unique ID for this entry
 * @param tokenId         ID of the capability token authorizing this action
 * @param signedToken     the full JWS-signed entitlement token (for first-use verification)
 * @param actorId         identity of the actor performing the action
 * @param tenantId        tenant scope
 * @param facilityId      facility where the action was performed
 * @param patientRef      CPID (Clinical Patient ID) — no PII
 * @param actionType      type of action (e.g. "CAPTURE_VITALS", "VITAL_SIGN")
 * @param payload         action-specific data (e.g. vital sign readings)
 * @param capturedAt      when the action was captured on the device
 * @param deviceId        device identifier
 * @param deviceFingerprint device attestation fingerprint
 * @param sequenceNum     monotonic sequence within this offline session
 * @param idempotencyKey  client-supplied deduplication key
 * @param correlationId   correlation ID for end-to-end tracing
 */
public record OfflineActionEntry(
        UUID entryId,
        UUID tokenId,
        String signedToken,
        String actorId,
        UUID tenantId,
        UUID facilityId,
        String patientRef,
        String actionType,
        Map<String, Object> payload,
        Instant capturedAt,
        String deviceId,
        String deviceFingerprint,
        int sequenceNum,
        String idempotencyKey,
        String correlationId
) {
    public OfflineActionEntry {
        Objects.requireNonNull(entryId, "entryId is required");
        Objects.requireNonNull(tokenId, "tokenId is required");
        Objects.requireNonNull(actorId, "actorId is required");
        Objects.requireNonNull(tenantId, "tenantId is required");
        Objects.requireNonNull(patientRef, "patientRef is required");
        Objects.requireNonNull(actionType, "actionType is required");
        Objects.requireNonNull(payload, "payload is required");
        Objects.requireNonNull(capturedAt, "capturedAt is required");
        if (sequenceNum < 1) {
            throw new IllegalArgumentException("sequenceNum must be >= 1");
        }
    }
}
