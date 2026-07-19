package zw.gov.mohcc.impilo.varapi.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.NamedParameterSpec;
import java.util.Base64;

/**
 * Seed-derived Ed25519 signing for badge QR payloads (D-P5) — the vito
 * QrSigningService house pattern replicated locally (survives restarts,
 * verifies cross-instance; JDK-native Ed25519, no extra crypto deps).
 * Production supplies {@code VARAPI_BADGE_SIGNING_KEY_SEED} via the tshepo-keys
 * custody flow; the dev fallback warns loudly and must never reach production.
 */
@Service
public class BadgeSigningService {

    private static final Logger log = LoggerFactory.getLogger(BadgeSigningService.class);
    private static final String DEV_FALLBACK_SEED = "varapi-badge-dev-seed-do-not-use-in-production";

    private final PrivateKey privateKey;
    private final PublicKey publicKey;

    public BadgeSigningService(@Value("${varapi.badge.signing-key-seed:}") String configuredSeed) {
        String seed = configuredSeed == null || configuredSeed.isBlank()
                ? DEV_FALLBACK_SEED : configuredSeed;
        if (DEV_FALLBACK_SEED.equals(seed)) {
            log.warn("BADGE SIGNING IS USING THE DEV FALLBACK SEED — set VARAPI_BADGE_SIGNING_KEY_SEED "
                    + "(varapi.badge.signing-key-seed) before issuing real badges.");
        }
        try {
            // One deterministic generator produces BOTH keys — sign and verify
            // must come from the same pair, and every instance sharing the seed
            // derives the identical pair (SHA1PRNG with a fixed seed is
            // deterministic by spec).
            byte[] seed32 = MessageDigest.getInstance("SHA-256")
                    .digest(seed.getBytes(StandardCharsets.UTF_8));
            java.security.KeyPairGenerator generator =
                    java.security.KeyPairGenerator.getInstance("Ed25519");
            java.security.SecureRandom deterministic =
                    java.security.SecureRandom.getInstance("SHA1PRNG");
            deterministic.setSeed(seed32);
            generator.initialize(NamedParameterSpec.ED25519, deterministic);
            java.security.KeyPair pair = generator.generateKeyPair();
            this.privateKey = pair.getPrivate();
            this.publicKey = pair.getPublic();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to initialise badge signing key", e);
        }
    }

    /** Compact signed payload: base64url(payload) + "." + base64url(signature). */
    public String sign(String payload) {
        try {
            Signature signature = Signature.getInstance("Ed25519");
            signature.initSign(privateKey);
            byte[] payloadBytes = payload.getBytes(StandardCharsets.UTF_8);
            signature.update(payloadBytes);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(payloadBytes)
                    + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(signature.sign());
        } catch (Exception e) {
            throw new IllegalStateException("Badge signing failed", e);
        }
    }

    /** Verify a compact signed payload; returns the payload on success, null otherwise. */
    public String verify(String compact) {
        try {
            if (compact == null || !compact.contains(".")) {
                return null;
            }
            String[] parts = compact.split("\\.", 2);
            byte[] payloadBytes = Base64.getUrlDecoder().decode(parts[0]);
            byte[] signatureBytes = Base64.getUrlDecoder().decode(parts[1]);
            Signature signature = Signature.getInstance("Ed25519");
            signature.initVerify(publicKey);
            signature.update(payloadBytes);
            return signature.verify(signatureBytes)
                    ? new String(payloadBytes, StandardCharsets.UTF_8) : null;
        } catch (Exception e) {
            return null;
        }
    }
}
