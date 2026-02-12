package zw.gov.mohcc.impilo.federation.identity;

/**
 * Port interface for pod identity verification.
 *
 * Implementations verify that a calling pod's identity is authentic
 * using mTLS certificate validation and/or JWT audience checks.
 *
 * The federation protocol requires:
 * - mTLS: calling pod presents a valid client certificate signed by the national CA
 * - JWT: token audience must include "federation" (aud=federation)
 *
 * Services implement this interface to plug into their TLS/security stack.
 */
public interface PodIdentityVerifier {

    /**
     * Verify the calling pod's identity.
     *
     * @param certificate the PEM-encoded client certificate (from mTLS)
     * @param jwtToken    the Bearer token (expected aud=federation)
     * @return verification result
     */
    PodVerificationResult verify(String certificate, String jwtToken);
}
