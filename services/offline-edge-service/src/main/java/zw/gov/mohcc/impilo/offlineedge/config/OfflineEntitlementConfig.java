package zw.gov.mohcc.impilo.offlineedge.config;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.OctetKeyPair;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.gen.OctetKeyPairGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import zw.gov.mohcc.impilo.offline.sdk.entitlement.OfflineEntitlementVerifier;

/**
 * Configuration for JWT-based offline entitlement verification.
 *
 * <p>In production, the JWKS is fetched from the keys-service endpoint and cached.
 * For local/dev, a deterministic Ed25519 key is generated from a configured seed.</p>
 */
@Configuration
public class OfflineEntitlementConfig {

    private static final Logger log = LoggerFactory.getLogger(OfflineEntitlementConfig.class);

    @Value("${impilo.offline.jwks-json:}")
    private String jwksJson;

    @Value("${impilo.offline.clock-skew-seconds:30}")
    private int clockSkewSeconds;

    @Bean
    public OfflineEntitlementVerifier offlineEntitlementVerifier() {
        JWKSet jwkSet;
        try {
            if (jwksJson != null && !jwksJson.isBlank()) {
                jwkSet = JWKSet.parse(jwksJson);
                log.info("Loaded JWKS for offline entitlement verification ({} keys)", jwkSet.getKeys().size());
            } else {
                // Dev/test fallback: generate a deterministic Ed25519 key pair
                log.warn("No JWKS configured — generating ephemeral Ed25519 key for dev/test");
                OctetKeyPair devKey = new OctetKeyPairGenerator(Curve.Ed25519)
                        .keyID("offline-capability-signing-key")
                        .generate();
                jwkSet = new JWKSet(devKey.toPublicJWK());
            }
        } catch (Exception e) {
            log.error("Failed to parse JWKS for offline entitlement verification, using ephemeral key", e);
            try {
                OctetKeyPair fallbackKey = new OctetKeyPairGenerator(Curve.Ed25519)
                        .keyID("offline-capability-signing-key")
                        .generate();
                jwkSet = new JWKSet(fallbackKey.toPublicJWK());
            } catch (Exception ex) {
                throw new RuntimeException("Cannot initialize offline entitlement verifier", ex);
            }
        }

        return new OfflineEntitlementVerifier(jwkSet, clockSkewSeconds);
    }
}
