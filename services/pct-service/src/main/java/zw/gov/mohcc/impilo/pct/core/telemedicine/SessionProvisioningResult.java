package zw.gov.mohcc.impilo.pct.core.telemedicine;

/**
 * Result of provisioning a virtual-care session with a specific provider mode.
 */
public record SessionProvisioningResult(
        String providerType,
        String channel,
        String roomUrl,
        String accessToken
) {
}
