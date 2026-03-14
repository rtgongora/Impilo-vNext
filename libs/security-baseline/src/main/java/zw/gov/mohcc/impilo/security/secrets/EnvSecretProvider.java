package zw.gov.mohcc.impilo.security.secrets;

import java.util.Objects;
import java.util.Optional;

/**
 * {@link SecretProvider} implementation that reads secrets from environment variables.
 * <p>
 * Suitable for local development and container deployments where secrets are injected
 * as environment variables (e.g. Kubernetes Secrets mounted as env vars).
 * <p>
 * Key mapping: dots and hyphens are converted to underscores, then uppercased.
 * An optional prefix is prepended (e.g. "IMPILO_" → "IMPILO_DB_PASSWORD").
 * <p>
 * Security: this class never logs or includes secret values in toString/exceptions.
 */
public final class EnvSecretProvider implements SecretProvider {

    private final String prefix;

    /**
     * @param prefix prefix prepended to all keys (e.g. "IMPILO_"). Use "" for no prefix.
     */
    public EnvSecretProvider(String prefix) {
        this.prefix = Objects.requireNonNull(prefix, "prefix must not be null (use empty string for no prefix)");
    }

    public EnvSecretProvider() {
        this("IMPILO_");
    }

    @Override
    public Optional<String> getSecret(String key) {
        Objects.requireNonNull(key, "secret key must not be null");
        String envKey = toEnvVar(key);
        String value = System.getenv(envKey);
        return Optional.ofNullable(value);
    }

    /**
     * Convert a dot/hyphen-separated key to an uppercase underscore-separated env var name.
     * Examples:
     *   "db.password"      → "IMPILO_DB_PASSWORD"
     *   "jwt.signing-key"  → "IMPILO_JWT_SIGNING_KEY"
     */
    String toEnvVar(String key) {
        return prefix + key.replace('.', '_').replace('-', '_').toUpperCase();
    }

    @Override
    public String toString() {
        return "EnvSecretProvider{prefix='" + prefix + "'}";
    }
}
