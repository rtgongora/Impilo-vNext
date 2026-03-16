package zw.gov.mohcc.impilo.security.secrets;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@link SecretProvider} implementation backed by HashiCorp Vault KV v2 API.
 * <p>
 * Connects to Vault via its HTTP API, authenticates using AppRole (if configured),
 * and retrieves secrets from a KV v2 secrets engine.
 * <p>
 * Falls back to environment variables when Vault is unreachable, ensuring
 * local development and container deployments (where secrets are injected as
 * env vars) continue to work without a running Vault instance.
 * <p>
 * Security invariants:
 * <ul>
 *   <li>{@link #toString()} never reveals cached values or tokens</li>
 *   <li>Exception messages never contain secret values</li>
 *   <li>Cached secrets are cleared on {@link #refresh()}</li>
 *   <li>Vault tokens are never logged</li>
 * </ul>
 */
public final class VaultSecretProvider implements SecretProvider {

    private static final Logger log = LoggerFactory.getLogger(VaultSecretProvider.class);

    private final VaultConfig config;
    private final ConcurrentHashMap<String, CachedSecret> cache = new ConcurrentHashMap<>();
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    private volatile String vaultToken;
    private volatile Instant tokenExpiry = Instant.MIN;

    public VaultSecretProvider(VaultConfig config) {
        this(config, HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build(), new ObjectMapper());
    }

    /** Constructor for testing with injected HTTP client. */
    VaultSecretProvider(VaultConfig config, HttpClient httpClient, ObjectMapper objectMapper) {
        this.config = Objects.requireNonNull(config, "VaultConfig is required");
        this.httpClient = Objects.requireNonNull(httpClient);
        this.objectMapper = Objects.requireNonNull(objectMapper);
    }

    @Override
    public Optional<String> getSecret(String key) {
        Objects.requireNonNull(key, "secret key must not be null");

        // Check cache first, respecting TTL
        CachedSecret cached = cache.get(key);
        if (cached != null && !cached.isExpired(config.cacheTtlSeconds())) {
            return Optional.of(cached.value());
        }

        // Attempt Vault retrieval
        Optional<String> vaultValue = fetchFromVault(key);
        if (vaultValue.isPresent()) {
            cache.put(key, new CachedSecret(vaultValue.get(), Instant.now()));
            return vaultValue;
        }

        // Fall back to environment variable when Vault is unavailable
        String envKey = config.envPrefix() + key.replace('.', '_').replace('-', '_').toUpperCase();
        String envFallback = System.getenv(envKey);
        if (envFallback != null) {
            cache.put(key, new CachedSecret(envFallback, Instant.now()));
            log.debug("Secret '{}' resolved from environment fallback", key);
        }
        return Optional.ofNullable(envFallback);
    }

    @Override
    public void refresh() {
        cache.clear();
        vaultToken = null;
        tokenExpiry = Instant.MIN;
        log.info("Vault secret cache and token cleared");
    }

    @Override
    public String toString() {
        return "VaultSecretProvider{address='" + config.vaultAddress()
                + "', engine='" + config.secretsEngine()
                + "', cachedKeys=" + cache.size() + "}";
    }

    // ------------------------------------------------------------------
    // Vault HTTP API integration
    // ------------------------------------------------------------------

    private Optional<String> fetchFromVault(String key) {
        try {
            String token = ensureAuthenticated();
            if (token == null) {
                log.debug("No Vault token available, will fall back to env for key '{}'", key);
                return Optional.empty();
            }

            // KV v2 read: GET /v1/{engine}/data/{path}
            String path = config.secretPath().endsWith("/")
                    ? config.secretPath() + key
                    : config.secretPath() + "/" + key;
            URI uri = URI.create(config.vaultAddress() + "/v1/" + config.secretsEngine() + "/data/" + path);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(uri)
                    .header("X-Vault-Token", token)
                    .header("Accept", "application/json")
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonNode root = objectMapper.readTree(response.body());
                JsonNode data = root.path("data").path("data");
                // KV v2 stores data under data.data; try the key itself, then "value"
                if (data.has(key)) {
                    return Optional.of(data.get(key).asText());
                }
                if (data.has("value")) {
                    return Optional.of(data.get("value").asText());
                }
                // If the secret path maps to a single value, try first field
                var fields = data.fields();
                if (fields.hasNext()) {
                    return Optional.of(fields.next().getValue().asText());
                }
                log.warn("Vault returned 200 but no recognisable data for key '{}'", key);
                return Optional.empty();
            } else if (response.statusCode() == 404) {
                log.debug("Secret '{}' not found in Vault (404)", key);
                return Optional.empty();
            } else {
                log.warn("Vault returned status {} for key '{}'", response.statusCode(), key);
                return Optional.empty();
            }
        } catch (Exception e) {
            log.warn("Vault fetch failed for key '{}': {}", key, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Ensures we have a valid Vault token. Uses AppRole authentication
     * if roleId is configured, otherwise checks for VAULT_TOKEN env var.
     */
    private synchronized String ensureAuthenticated() {
        if (vaultToken != null && Instant.now().isBefore(tokenExpiry)) {
            return vaultToken;
        }

        // Try AppRole authentication if roleId is configured
        if (config.roleId() != null && !config.roleId().isBlank()) {
            return authenticateAppRole();
        }

        // Fall back to VAULT_TOKEN environment variable
        String envToken = System.getenv("VAULT_TOKEN");
        if (envToken != null && !envToken.isBlank()) {
            vaultToken = envToken;
            tokenExpiry = Instant.now().plusSeconds(config.cacheTtlSeconds());
            return vaultToken;
        }

        return null;
    }

    private String authenticateAppRole() {
        try {
            String secretId = System.getenv("VAULT_SECRET_ID");
            String payload = objectMapper.writeValueAsString(
                    java.util.Map.of("role_id", config.roleId(),
                            "secret_id", secretId != null ? secretId : ""));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(config.vaultAddress() + "/v1/auth/approle/login"))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(10))
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonNode root = objectMapper.readTree(response.body());
                JsonNode auth = root.path("auth");
                vaultToken = auth.path("client_token").asText();
                int leaseDuration = auth.path("lease_duration").asInt(3600);
                tokenExpiry = Instant.now().plusSeconds(Math.max(leaseDuration - 30, 60));
                log.info("Vault AppRole authentication successful, lease={}s", leaseDuration);
                return vaultToken;
            } else {
                log.warn("Vault AppRole auth failed with status {}", response.statusCode());
                return null;
            }
        } catch (Exception e) {
            log.warn("Vault AppRole authentication failed: {}", e.getMessage());
            return null;
        }
    }

    // ------------------------------------------------------------------
    // Cache entry with TTL support
    // ------------------------------------------------------------------

    private record CachedSecret(String value, Instant fetchedAt) {
        boolean isExpired(int ttlSeconds) {
            return Instant.now().isAfter(fetchedAt.plusSeconds(ttlSeconds));
        }
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
