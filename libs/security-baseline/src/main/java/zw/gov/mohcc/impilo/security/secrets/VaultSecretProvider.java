package zw.gov.mohcc.impilo.security.secrets;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Config contract for HashiCorp Vault / KMS-backed secret retrieval.
 * <p>
 * This is an integration-point stub: the actual Vault client is NOT included
 * (no real HSM integration per wave 14 scope). This class defines the contract
 * and configuration shape that a production Vault integration must satisfy.
 * <p>
 * For local dev, use {@link EnvSecretProvider} as a functional fallback.
 * <p>
 * Security invariants:
 * <ul>
 *   <li>{@link #toString()} never reveals cached values</li>
 *   <li>Exception messages never contain secret values</li>
 *   <li>Cached secrets are cleared on {@link #refresh()}</li>
 * </ul>
 */
public final class VaultSecretProvider implements SecretProvider {

    private final VaultConfig config;
    private final ConcurrentHashMap<String, String> cache = new ConcurrentHashMap<>();

    public VaultSecretProvider(VaultConfig config) {
        this.config = Objects.requireNonNull(config, "VaultConfig is required");
    }

    @Override
    public Optional<String> getSecret(String key) {
        Objects.requireNonNull(key, "secret key must not be null");

        // Check cache first
        String cached = cache.get(key);
        if (cached != null) {
            return Optional.of(cached);
        }

        // In production, this would call Vault HTTP API:
        //   GET {vaultAddress}/v1/{secretsEngine}/data/{secretPath}/{key}
        //   with X-Vault-Token header
        // For now, fall back to environment variable
        String envFallback = System.getenv(
                config.envPrefix() + key.replace('.', '_').replace('-', '_').toUpperCase());
        if (envFallback != null) {
            cache.put(key, envFallback);
        }
        return Optional.ofNullable(envFallback);
    }

    @Override
    public void refresh() {
        cache.clear();
    }

    @Override
    public String toString() {
        return "VaultSecretProvider{address='" + config.vaultAddress()
                + "', engine='" + config.secretsEngine()
                + "', cachedKeys=" + cache.size() + "}";
    }

    /**
     * Configuration contract for Vault integration.
     *
     * @param vaultAddress   Vault server URL (e.g. "https://vault.impilo.internal:8200")
     * @param secretsEngine  KV secrets engine mount (e.g. "impilo-kv")
     * @param secretPath     path prefix within the engine (e.g. "services/tshepo")
     * @param roleId         AppRole role ID for authentication
     * @param envPrefix      prefix for env-var fallback (e.g. "IMPILO_")
     * @param cacheTtlSeconds how long to cache secrets before re-fetching
     */
    public record VaultConfig(
            String vaultAddress,
            String secretsEngine,
            String secretPath,
            String roleId,
            String envPrefix,
            int cacheTtlSeconds
    ) {
        public VaultConfig {
            Objects.requireNonNull(vaultAddress, "vaultAddress is required");
            Objects.requireNonNull(secretsEngine, "secretsEngine is required");
            Objects.requireNonNull(secretPath, "secretPath is required");
            if (envPrefix == null) envPrefix = "IMPILO_";
            if (cacheTtlSeconds < 0) cacheTtlSeconds = 300;
        }

        public static VaultConfig forLocalDev(String serviceName) {
            return new VaultConfig(
                    "http://localhost:8200",
                    "impilo-kv",
                    "services/" + serviceName,
                    null,
                    "IMPILO_",
                    60
            );
        }
    }
}
