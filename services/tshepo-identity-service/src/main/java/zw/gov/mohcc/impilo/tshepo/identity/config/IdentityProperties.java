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
        /** UUID v5 namespace for deterministic CPID generation. */
        String cpidNamespace,

        /** Default scoped-token TTL in seconds. */
        int tokenTtlSeconds,

        /** Base64-encoded AES-256 key-encryption-key for MOSIP link_ref encryption. */
        String mosipKek,

        /** Base URL of the tshepo-keys-service for Ed25519 signing. */
        String keysServiceUrl,

        /** Base URL of the VITO service for Impilo ID resolution. */
        String vitoServiceUrl
) {
    public IdentityProperties {
        if (cpidNamespace == null || cpidNamespace.isBlank()) {
            cpidNamespace = "6ba7b810-9dad-11d1-80b4-00c04fd430c8";
        }
        if (tokenTtlSeconds <= 0) {
            tokenTtlSeconds = 300;
        }
        if (keysServiceUrl == null || keysServiceUrl.isBlank()) {
            keysServiceUrl = "http://localhost:8086";
        }
        if (vitoServiceUrl == null || vitoServiceUrl.isBlank()) {
            vitoServiceUrl = "http://localhost:8082";
        }
    }
}
