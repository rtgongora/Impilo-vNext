package zw.gov.mohcc.impilo.msikaflow.api.dto;

import java.util.UUID;

/**
 * Trust-plane snapshot for a pickup claim (headers), forwarded to Tshepo when biometric policy is enabled.
 */
public record PickupClaimTrust(
        UUID tenantId,
        String correlationId,
        String actorId,
        String actorType,
        Integer assuranceLevel,
        String deviceFingerprint) {
}
