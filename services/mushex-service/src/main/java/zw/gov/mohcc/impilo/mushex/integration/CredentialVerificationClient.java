package zw.gov.mohcc.impilo.mushex.integration;

import java.util.UUID;

/**
 * Mesh client for credential-verification-service payee checks before payouts.
 */
@FunctionalInterface
public interface CredentialVerificationClient {

    record CredentialVerificationResult(boolean allowed, String status, String verificationRef, String credentialId) {}

    /**
     * Verifies the payee (provider) has an acceptable credential posture for payment.
     */
    CredentialVerificationResult verifyPayee(UUID tenantId, String providerId);
}
