package zw.gov.mohcc.impilo.vito.core.qr;

import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.*;
import com.nimbusds.jose.jwk.*;
import com.nimbusds.jose.util.Base64URL;
import com.nimbusds.jwt.*;
import jakarta.annotation.PostConstruct;
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters;
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.ParseException;
import java.time.Instant;
import java.util.*;

/**
 * Ed25519 QR token signing and verification (Identity Contract §10).
 *
 * QR encodes a base64url JWT with:
 *   jti       - unique token ID
 *   tenant_id - tenant scope
 *   purpose   - what the QR is for (PICKUP, WALLET, EMERGENCY, HEALTH_ID)
 *   pointer   - opaque reference (HMAC of the actual ID, NOT plaintext)
 *   exp       - expiry unix timestamp
 *   iat       - issued at
 *
 * <p>C3 fix: the signing key is <b>derived deterministically</b> from a
 * configured 32-byte seed ({@code vito.qr.signing-key-seed}) rather than
 * regenerated on every startup. Previously each restart minted a fresh random
 * key, so QRs signed before a restart could not be verified afterward and two
 * instances disagreed. A stable seed makes signatures survive restarts and
 * verify cross-instance. The seed is a deployment secret (tshepo-keys custody
 * target); unset falls back to a dev seed with a loud warning so preview boots.</p>
 */
@Service
public class QrSigningService {

    private static final Logger log = LoggerFactory.getLogger(QrSigningService.class);
    private static final String KEY_ID = "vito-qr-1";
    private static final String DEV_SEED = "vito-qr-signing-dev-seed-change-me-32b";

    private final String seedMaterial;

    private OctetKeyPair jwk;
    private JWSSigner signer;
    private JWSVerifier verifier;

    public QrSigningService(@Value("${vito.qr.signing-key-seed:}") String configuredSeed) {
        if (configuredSeed == null || configuredSeed.strip().length() < 32) {
            log.warn("vito.qr.signing-key-seed is unset/weak — using the DEV seed. Production MUST "
                    + "supply a >=32-char secret (tshepo-keys custody); QR signatures depend on it.");
            this.seedMaterial = DEV_SEED;
        } else {
            this.seedMaterial = configuredSeed;
        }
    }

    @PostConstruct
    public void init() throws Exception {
        // Deterministic Ed25519 key from the seed: 32-byte private scalar =
        // SHA-256(seed); public key derived via BouncyCastle. Same seed -> same
        // key on every instance and every restart.
        byte[] priv = MessageDigest.getInstance("SHA-256")
                .digest(seedMaterial.getBytes(StandardCharsets.UTF_8));
        Ed25519PrivateKeyParameters privParams = new Ed25519PrivateKeyParameters(priv, 0);
        Ed25519PublicKeyParameters pubParams = privParams.generatePublicKey();

        jwk = new OctetKeyPair.Builder(Curve.Ed25519, Base64URL.encode(pubParams.getEncoded()))
                .d(Base64URL.encode(priv))
                .keyID(KEY_ID)
                .build();
        signer = new Ed25519Signer(jwk);
        verifier = new Ed25519Verifier(jwk.toPublicJWK());
        log.info("Ed25519 QR signing key initialized from seed (kid: {}, stable across restarts)",
                jwk.getKeyID());
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
