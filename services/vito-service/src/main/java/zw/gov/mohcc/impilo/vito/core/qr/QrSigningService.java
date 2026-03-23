package zw.gov.mohcc.impilo.vito.core.qr;

import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.*;
import com.nimbusds.jose.jwk.*;
import com.nimbusds.jose.jwk.gen.OctetKeyPairGenerator;
import com.nimbusds.jwt.*;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.text.ParseException;
import java.time.Instant;
import java.util.*;

/**
 * Ed25519 QR token signing and verification.
 *
 * QR encodes a base64url JWT with:
 *   jti       - unique token ID
 *   tenant_id - tenant scope
 *   purpose   - what the QR is for (PICKUP, WALLET, EMERGENCY, HEALTH_ID)
 *   pointer   - opaque reference (HMAC of the actual ID, NOT plaintext)
 *   exp       - expiry unix timestamp
 *   iat       - issued at
 *
 * Signed with Ed25519 (OKP/EdDSA). Key pair generated on startup
 * (in production, loaded from K8s Secret or Vault).
 */
@Service
public class QrSigningService {

    private static final Logger log = LoggerFactory.getLogger(QrSigningService.class);

    private OctetKeyPair jwk;
    private JWSSigner signer;
    private JWSVerifier verifier;

    @PostConstruct
    public void init() throws Exception {
        // Generate Ed25519 key pair (in production: load from secret store)
        jwk = new OctetKeyPairGenerator(Curve.Ed25519)
                .keyID("vito-qr-1")
                .generate();
        signer = new Ed25519Signer(jwk);
        verifier = new Ed25519Verifier(jwk.toPublicJWK());
        log.info("Ed25519 QR signing key initialized (kid: {})", jwk.getKeyID());
    }

    /**
     * Sign a QR token.
     *
     * @param tenantId tenant scope
     * @param purpose  PICKUP, WALLET, EMERGENCY, HEALTH_ID
     * @param pointer  opaque reference (e.g. HMAC of health_id)
     * @param ttlSeconds time-to-live in seconds
     * @return compact JWS string suitable for QR encoding
     */
    public String sign(String tenantId, String purpose, String pointer, long ttlSeconds) {
        try {
            Instant now = Instant.now();
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .jwtID(UUID.randomUUID().toString())
                    .claim("tenant_id", tenantId)
                    .claim("purpose", purpose)
                    .claim("pointer", pointer)
                    .issueTime(Date.from(now))
                    .expirationTime(Date.from(now.plusSeconds(ttlSeconds)))
                    .build();

            SignedJWT jwt = new SignedJWT(
                    new JWSHeader.Builder(JWSAlgorithm.EdDSA)
                            .keyID(jwk.getKeyID())
                            .type(JOSEObjectType.JWT)
                            .build(),
                    claims);

            jwt.sign(signer);
            return jwt.serialize();
        } catch (JOSEException e) {
            throw new RuntimeException("Failed to sign QR token", e);
        }
    }

    /**
     * Verify and extract claims from a QR token.
     *
     * @param compactJws the compact JWS string from the QR code
     * @return the verified claims, or empty if invalid/expired
     */
    public Optional<QrClaims> verify(String compactJws) {
        try {
            SignedJWT jwt = SignedJWT.parse(compactJws);

            if (!jwt.verify(verifier)) {
                return Optional.empty();
            }

            JWTClaimsSet claims = jwt.getJWTClaimsSet();

            // Check expiry
            Date exp = claims.getExpirationTime();
            if (exp != null && exp.toInstant().isBefore(Instant.now())) {
                return Optional.empty();
            }

            return Optional.of(new QrClaims(
                    claims.getJWTID(),
                    claims.getStringClaim("tenant_id"),
                    claims.getStringClaim("purpose"),
                    claims.getStringClaim("pointer"),
                    exp != null ? exp.toInstant().getEpochSecond() : 0,
                    claims.getIssueTime() != null ? claims.getIssueTime().toInstant().getEpochSecond() : 0
            ));
        } catch (ParseException | JOSEException e) {
            log.warn("QR token verification failed: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Get the public key in JWK format (for external verifiers).
     */
    public String getPublicKeyJwk() {
        return jwk.toPublicJWK().toJSONString();
    }

    public record QrClaims(String jti, String tenantId, String purpose, String pointer, long exp, long iat) {}
}
