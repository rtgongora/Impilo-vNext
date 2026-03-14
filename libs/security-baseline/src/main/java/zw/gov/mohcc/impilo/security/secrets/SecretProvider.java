package zw.gov.mohcc.impilo.security.secrets;

import java.util.Optional;

/**
 * Contract for retrieving secrets at runtime. Implementations may be backed by
 * HashiCorp Vault, AWS KMS, Azure Key Vault, or environment variables.
 * <p>
 * Security invariants:
 * <ul>
 *   <li>Implementations MUST NOT log secret values</li>
 *   <li>Implementations MUST NOT include secret values in exception messages</li>
 *   <li>{@link #toString()} MUST NOT reveal cached secret values</li>
 *   <li>Secrets should be fetched lazily and cached with a configurable TTL</li>
 * </ul>
 */
public interface SecretProvider {

    /**
     * Retrieve a secret by key.
     *
     * @param key the secret key (e.g. "db.password", "jwt.signing-key")
     * @return the secret value, or empty if not found
     */
    Optional<String> getSecret(String key);

    /**
     * Retrieve a required secret. Throws if the secret is not available.
     *
     * @param key the secret key
     * @return the secret value
     * @throws SecretNotFoundException if the secret is not found
     */
    default String requireSecret(String key) {
        return getSecret(key).orElseThrow(() ->
                new SecretNotFoundException(key));
    }

    /**
     * Rotate/refresh cached secrets. Called periodically or on-demand
     * to pick up rotated credentials.
     */
    default void refresh() {
        // no-op by default; override for caching implementations
    }
}
