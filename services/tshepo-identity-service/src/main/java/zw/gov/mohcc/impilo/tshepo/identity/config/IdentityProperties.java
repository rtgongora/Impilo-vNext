package zw.gov.mohcc.impilo.tshepo.identity.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Externalized configuration for TSHEPO Identity service.
 *
 * <p>All sensitive values (KEK, service URLs) should be injected via environment
 * variables in production. Defaults exist only for local development.</p>
 */
@ConfigurationProperties(prefix = "tshepo.identity")
public record IdentityProperties(
        /** Default scoped-token TTL in seconds. */
        int tokenTtlSeconds,

        /** Base64-encoded AES-256 key-encryption-key for MOSIP link_ref encryption. */
        String mosipKek,

        /** Base URL of the tshepo-keys-service for Ed25519 signing. */
        String keysServiceUrl,

        /** Base URL of the VITO service for Impilo ID resolution. */
        String vitoServiceUrl,

        /** Base URL of the Varapi service for council/EC-number resolution. */
        String varapiServiceUrl
) {
    public IdentityProperties {
        if (tokenTtlSeconds <= 0) {
            tokenTtlSeconds = 300;
        }
        if (keysServiceUrl == null || keysServiceUrl.isBlank()) {
            keysServiceUrl = "http://localhost:8086";
        }
        if (vitoServiceUrl == null || vitoServiceUrl.isBlank()) {
            vitoServiceUrl = "http://localhost:8082";
        }
        if (varapiServiceUrl == null || varapiServiceUrl.isBlank()) {
            varapiServiceUrl = "http://localhost:8083";
        }
    }
}
