package zw.gov.mohcc.impilo.tshepo.keys.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for the TSHEPO Keys service.
 *
 * <p>Bound from {@code tshepo.keys.*} in application.yml.</p>
 */
@ConfigurationProperties(prefix = "tshepo.keys")
public class KeysProperties {

    /**
     * Hex-encoded AES-256 Key Encryption Key (KEK) used to encrypt
     * Ed25519 private keys at rest via AES-256-GCM.
     *
     * <p>In production this MUST be sourced from Vault or a KMS.</p>
     */
    private String kek;

    /** Algorithm identifier for signing keys. Default: Ed25519. */
    private String keyAlgorithm = "Ed25519";

    /** Number of days between automatic key rotations. */
    private int rotationIntervalDays = 90;

    /** Default TTL in seconds for issued JWS tokens. */
    private int tokenDefaultTtlSeconds = 300;

    /** TTL in seconds for the JWKS cache in Redis. */
    private int jwksCacheTtlSeconds = 60;

    private Events events = new Events();

    // --- Getters and Setters ---

    public String getKek() {
        return kek;
    }

    public void setKek(String kek) {
        this.kek = kek;
    }

    public String getKeyAlgorithm() {
        return keyAlgorithm;
    }

    public void setKeyAlgorithm(String keyAlgorithm) {
        this.keyAlgorithm = keyAlgorithm;
    }

    public int getRotationIntervalDays() {
        return rotationIntervalDays;
    }

    public void setRotationIntervalDays(int rotationIntervalDays) {
        this.rotationIntervalDays = rotationIntervalDays;
    }

    public int getTokenDefaultTtlSeconds() {
        return tokenDefaultTtlSeconds;
    }

    public void setTokenDefaultTtlSeconds(int tokenDefaultTtlSeconds) {
        this.tokenDefaultTtlSeconds = tokenDefaultTtlSeconds;
    }

    public int getJwksCacheTtlSeconds() {
        return jwksCacheTtlSeconds;
    }

    public void setJwksCacheTtlSeconds(int jwksCacheTtlSeconds) {
        this.jwksCacheTtlSeconds = jwksCacheTtlSeconds;
    }

    public Events getEvents() {
        return events;
    }

    public void setEvents(Events events) {
        this.events = events;
    }

    public static class Events {
        private String kafkaTopic = "trust.tshepo.keys.events";

        public String getKafkaTopic() {
            return kafkaTopic;
        }

        public void setKafkaTopic(String kafkaTopic) {
            this.kafkaTopic = kafkaTopic;
        }
    }
}
