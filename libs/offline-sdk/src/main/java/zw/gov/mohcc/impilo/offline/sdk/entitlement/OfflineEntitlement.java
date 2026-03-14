package zw.gov.mohcc.impilo.offline.sdk.entitlement;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Parsed offline entitlement extracted from a verified TSHEPO capability token JWT.
 *
 * <p>Compatible with the claim set issued by {@code tshepo-offline-service}'s
 * {@code CapabilityTokenService}:
 * <pre>
 * {
 *   "jti":  "&lt;token-id&gt;",
 *   "iss":  "tshepo-offline-service",
 *   "sub":  "&lt;actor-id&gt;",
 *   "aud":  "impilo-offline",
 *   "iat":  &lt;epoch-seconds&gt;,
 *   "exp":  &lt;epoch-seconds&gt;,
 *   "tenant_id":          "&lt;tenant-uuid&gt;",
 *   "facility_id":        "&lt;facility-uuid&gt;",
 *   "device_fingerprint": "&lt;device-fp&gt;",
 *   "capabilities":       ["READ_PATIENT", "CREATE_PROVISIONAL_ENCOUNTER", ...],
 *   "max_offline_encounters": 50
 * }
 * </pre>
 *
 * @param tokenId            unique token identifier (jti claim)
 * @param issuer             token issuer (iss claim — expected "tshepo-offline-service")
 * @param actorId            subject of the token (sub claim)
 * @param audience           intended audience (aud claim — expected "impilo-offline")
 * @param tenantId           tenant scope
 * @param facilityId         facility the entitlement is scoped to
 * @param deviceFingerprint  device attestation fingerprint
 * @param capabilities       list of allowed offline capabilities
 * @param maxOfflineEncounters maximum offline encounters allowed in one session
 * @param issuedAt           when the token was issued
 * @param expiresAt          when the token expires
 */
public record OfflineEntitlement(
        UUID tokenId,
        String issuer,
        String actorId,
        String audience,
        UUID tenantId,
        UUID facilityId,
        String deviceFingerprint,
        List<String> capabilities,
        int maxOfflineEncounters,
        Instant issuedAt,
        Instant expiresAt
) {
    /**
     * Check if this entitlement grants the specified capability.
     */
    public boolean hasCapability(String capability) {
        return capabilities != null && capabilities.contains(capability);
    }

    /**
     * Check if this entitlement has expired.
     */
    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }

    /**
     * Check if this entitlement is valid (not expired and has capabilities).
     */
    public boolean isValid() {
        return !isExpired() && capabilities != null && !capabilities.isEmpty();
    }
}
