package zw.gov.mohcc.impilo.zibo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Type-safe configuration properties for the ZIBO service.
 *
 * <p>Bound from the {@code zibo.*} namespace in application.yml.
 * Controls outbox polling, cache TTL, and terminology validation defaults.</p>
 */
@ConfigurationProperties(prefix = "zibo")
public class ZiboProperties {

    private Outbox outbox = new Outbox();
    private Cache cache = new Cache();
    private Validation validation = new Validation();

    // -- Getters / setters -----------------------------------------------

    public Outbox getOutbox() { return outbox; }
    public void setOutbox(Outbox outbox) { this.outbox = outbox; }

    public Cache getCache() { return cache; }
    public void setCache(Cache cache) { this.cache = cache; }

    public Validation getValidation() { return validation; }
    public void setValidation(Validation validation) { this.validation = validation; }

    // -- Nested configuration classes ------------------------------------

    /**
     * Event outbox polling settings.
     *
     * <p>{@code pollIntervalMs}: how often the outbox publisher polls for
     * unpublished events (in milliseconds). Default: 2000.</p>
     */
    public static class Outbox {
        private long pollIntervalMs = 2000;

        public long getPollIntervalMs() { return pollIntervalMs; }
        public void setPollIntervalMs(long pollIntervalMs) { this.pollIntervalMs = pollIntervalMs; }
    }

    /**
     * Cache settings for terminology lookups.
     *
     * <p>{@code ttlMinutes}: time-to-live for cached terminology data
     * in Redis (in minutes). Default: 60.</p>
     */
    public static class Cache {
        private int ttlMinutes = 60;

        public int getTtlMinutes() { return ttlMinutes; }
        public void setTtlMinutes(int ttlMinutes) { this.ttlMinutes = ttlMinutes; }
    }

    /**
     * Terminology validation settings.
     *
     * <p>{@code defaultMode}: the default validation policy mode applied
     * when no scope-specific assignment overrides it. Accepts STRICT or
     * LENIENT. Default: LENIENT.</p>
     */
    public static class Validation {
        private String defaultMode = "LENIENT";

        public String getDefaultMode() { return defaultMode; }
        public void setDefaultMode(String defaultMode) { this.defaultMode = defaultMode; }
    }
}
