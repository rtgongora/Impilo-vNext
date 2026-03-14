package zw.gov.mohcc.impilo.offline.sdk.entitlement;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.Ed25519Verifier;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.OctetKeyPair;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import java.text.ParseException;
import java.time.Instant;
import java.util.*;

/**
 * Offline-capable JWT entitlement verifier.
 *
 * <p>Verifies JWS-signed capability tokens issued by TSHEPO's offline service.
 * Supports Ed25519 (EdDSA) and RS256 signing algorithms. The JWKS (public keys)
 * are loaded once at construction time, enabling fully offline verification.</p>
 *
 * <h3>Usage</h3>
 * <pre>{@code
 * JWKSet jwks = JWKSet.parse(jwksJson);
 * var verifier = new OfflineEntitlementVerifier(jwks);
 * EntitlementVerificationResult result = verifier.verify(signedTokenString, "CAPTURE_VITALS");
 * if (result.valid()) {
 *     OfflineEntitlement ent = result.entitlement();
 *     // proceed with offline operation
 * }
 * }</pre>
 *
 * <h3>TSHEPO compatibility</h3>
 * <p>This verifier is compatible with the JWT claim set issued by
 * {@code tshepo-offline-service/CapabilityTokenService}, which signs tokens
 * using Ed25519 via the keys-service JWKS endpoint.</p>
 */
public class OfflineEntitlementVerifier {

    private static final String EXPECTED_ISSUER = "tshepo-offline-service";
    private static final String EXPECTED_AUDIENCE = "impilo-offline";

    private final JWKSet jwkSet;
    private final ObjectMapper objectMapper;
    private final int clockSkewSeconds;

    /**
     * Create a verifier with the given JWKS and default 30-second clock skew tolerance.
     */
    public OfflineEntitlementVerifier(JWKSet jwkSet) {
        this(jwkSet, 30);
    }

    /**
     * Create a verifier with explicit clock skew tolerance.
     *
     * @param jwkSet            the JWKS containing public keys for signature verification
     * @param clockSkewSeconds  allowed clock skew in seconds (for edge device time drift)
     */
    public OfflineEntitlementVerifier(JWKSet jwkSet, int clockSkewSeconds) {
        this.jwkSet = Objects.requireNonNull(jwkSet, "jwkSet is required");
        this.objectMapper = new ObjectMapper();
        this.clockSkewSeconds = clockSkewSeconds;
    }

    /**
     * Verify an offline entitlement token and check for a required capability.
     *
     * @param signedToken      the JWS compact serialization string
     * @param requiredCapability capability that must be present (null to skip check)
     * @return verification result with parsed entitlement on success
     */
    public EntitlementVerificationResult verify(String signedToken, String requiredCapability) {
        if (signedToken == null || signedToken.isBlank()) {
            return EntitlementVerificationResult.denied("Token is null or empty");
        }

        // Step 1: Parse the JWS
        SignedJWT jwt;
        try {
            jwt = SignedJWT.parse(signedToken);
        } catch (ParseException e) {
            return EntitlementVerificationResult.denied("Invalid JWS format: " + e.getMessage());
        }

        // Step 2: Find the signing key in JWKS
        String keyId = jwt.getHeader().getKeyID();
        JWSAlgorithm algorithm = jwt.getHeader().getAlgorithm();
        JWSVerifier jwsVerifier;
        try {
            jwsVerifier = resolveVerifier(keyId, algorithm);
        } catch (Exception e) {
            return EntitlementVerificationResult.denied("Cannot resolve signing key: " + e.getMessage());
        }

        // Step 3: Verify signature
        try {
            if (!jwt.verify(jwsVerifier)) {
                return EntitlementVerificationResult.denied("Signature verification failed");
            }
        } catch (JOSEException e) {
            return EntitlementVerificationResult.denied("Signature verification error: " + e.getMessage());
        }

        // Step 4: Extract and validate claims
        JWTClaimsSet claims;
        try {
            claims = jwt.getJWTClaimsSet();
        } catch (ParseException e) {
            return EntitlementVerificationResult.denied("Failed to parse JWT claims: " + e.getMessage());
        }

        // Validate issuer
        String issuer = claims.getIssuer();
        if (!EXPECTED_ISSUER.equals(issuer)) {
            return EntitlementVerificationResult.denied("Invalid issuer: " + issuer);
        }

        // Validate audience
        List<String> audience = claims.getAudience();
        if (audience == null || !audience.contains(EXPECTED_AUDIENCE)) {
            return EntitlementVerificationResult.denied("Invalid audience: " + audience);
        }

        // Validate expiration with clock skew tolerance
        Date expirationTime = claims.getExpirationTime();
        if (expirationTime == null) {
            return EntitlementVerificationResult.denied("Token has no expiration");
        }
        Instant expiry = expirationTime.toInstant();
        if (Instant.now().isAfter(expiry.plusSeconds(clockSkewSeconds))) {
            return EntitlementVerificationResult.denied("Token has expired");
        }

        // Validate not-before / issued-at
        Date issueTime = claims.getIssueTime();
        if (issueTime != null) {
            Instant iat = issueTime.toInstant();
            if (Instant.now().isBefore(iat.minusSeconds(clockSkewSeconds))) {
                return EntitlementVerificationResult.denied("Token not yet valid (issued in the future)");
            }
        }

        // Step 5: Parse custom claims into OfflineEntitlement
        try {
            OfflineEntitlement entitlement = parseEntitlement(claims);

            // Step 6: Check required capability
            if (requiredCapability != null && !entitlement.hasCapability(requiredCapability)) {
                return EntitlementVerificationResult.denied(
                        "Missing required capability: " + requiredCapability);
            }

            return EntitlementVerificationResult.success(entitlement);
        } catch (Exception e) {
            return EntitlementVerificationResult.denied("Failed to parse entitlement claims: " + e.getMessage());
        }
    }

    /**
     * Verify a token without checking a specific capability.
     */
    public EntitlementVerificationResult verify(String signedToken) {
        return verify(signedToken, null);
    }

    private JWSVerifier resolveVerifier(String keyId, JWSAlgorithm algorithm) throws JOSEException {
        JWK key = null;

        // Try by key ID first
        if (keyId != null) {
            key = jwkSet.getKeyByKeyId(keyId);
        }

        // Fall back to first key matching the algorithm
        if (key == null) {
            for (JWK candidate : jwkSet.getKeys()) {
                if (candidate.getAlgorithm() != null && candidate.getAlgorithm().equals(algorithm)) {
                    key = candidate;
                    break;
                }
            }
        }

        // Final fallback: first key in the set
        if (key == null && !jwkSet.getKeys().isEmpty()) {
            key = jwkSet.getKeys().get(0);
        }

        if (key == null) {
            throw new JOSEException("No suitable key found in JWKS for kid=" + keyId + ", alg=" + algorithm);
        }

        if (JWSAlgorithm.EdDSA.equals(algorithm) && key instanceof OctetKeyPair okp) {
            return new Ed25519Verifier(okp);
        } else if (JWSAlgorithm.RS256.equals(algorithm) && key instanceof RSAKey rsaKey) {
            return new RSASSAVerifier(rsaKey);
        } else if (key instanceof OctetKeyPair okp) {
            return new Ed25519Verifier(okp);
        } else if (key instanceof RSAKey rsaKey) {
            return new RSASSAVerifier(rsaKey);
        }

        throw new JOSEException("Unsupported key type or algorithm: " + key.getKeyType() + "/" + algorithm);
    }

    @SuppressWarnings("unchecked")
    private OfflineEntitlement parseEntitlement(JWTClaimsSet claims) throws ParseException {
        UUID tokenId = UUID.fromString(claims.getJWTID());
        String actorId = claims.getSubject();
        String issuer = claims.getIssuer();
        String audience = claims.getAudience() != null && !claims.getAudience().isEmpty()
                ? claims.getAudience().get(0) : null;

        UUID tenantId = UUID.fromString((String) claims.getClaim("tenant_id"));
        UUID facilityId = UUID.fromString((String) claims.getClaim("facility_id"));
        String deviceFingerprint = (String) claims.getClaim("device_fingerprint");

        List<String> capabilities;
        Object capsClaim = claims.getClaim("capabilities");
        if (capsClaim instanceof List<?> list) {
            capabilities = list.stream().map(Object::toString).toList();
        } else {
            capabilities = List.of();
        }

        int maxEncounters = 50; // default
        Object maxEnc = claims.getClaim("max_offline_encounters");
        if (maxEnc instanceof Number num) {
            maxEncounters = num.intValue();
        }

        Instant issuedAt = claims.getIssueTime() != null ? claims.getIssueTime().toInstant() : Instant.now();
        Instant expiresAt = claims.getExpirationTime().toInstant();

        return new OfflineEntitlement(
                tokenId, issuer, actorId, audience,
                tenantId, facilityId, deviceFingerprint,
                capabilities, maxEncounters,
                issuedAt, expiresAt
        );
    }
}
