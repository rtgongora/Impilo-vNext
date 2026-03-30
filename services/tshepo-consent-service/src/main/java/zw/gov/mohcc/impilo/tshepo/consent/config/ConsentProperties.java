package zw.gov.mohcc.impilo.tshepo.consent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Externalized configuration for the consent service.
 *
 * <p>Bound from {@code consent.*} properties in application.yml.</p>
 */
@ConfigurationProperties(prefix = "consent")
public class ConsentProperties {

    /** Default time-to-live in days for newly created consent directives without an explicit period end. */
    private int defaultTtlDays = 365;

    /** Time-to-live in seconds for cached consent evaluation decisions in Redis. */
    private int cacheTtlSeconds = 60;

    /** Maximum allowed lifetime in hours for share links. */
    private int maxShareLinkHours = 72;

    private Outbox outbox = new Outbox();

    public int getDefaultTtlDays() {
        return defaultTtlDays;
    }

    public void setDefaultTtlDays(int defaultTtlDays) {
        this.defaultTtlDays = defaultTtlDays;
    }

    public int getCacheTtlSeconds() {
        return cacheTtlSeconds;
    }

    public void setCacheTtlSeconds(int cacheTtlSeconds) {
        this.cacheTtlSeconds = cacheTtlSeconds;
    }

    public int getMaxShareLinkHours() {
        return maxShareLinkHours;
    }

    public void setMaxShareLinkHours(int maxShareLinkHours) {
        this.maxShareLinkHours = maxShareLinkHours;
    }

    public Outbox getOutbox() {
        return outbox;
    }

    public void setOutbox(Outbox outbox) {
        this.outbox = outbox;
    }

    public static class Outbox {
        private long pollIntervalMs = 1000;
        private String kafkaTopic = "trust.tshepo.consent.events";

        public long getPollIntervalMs() {
            return pollIntervalMs;
        }

        public void setPollIntervalMs(long pollIntervalMs) {
            this.pollIntervalMs = pollIntervalMs;
        }

        public String getKafkaTopic() {
            return kafkaTopic;
        }

        public void setKafkaTopic(String kafkaTopic) {
            this.kafkaTopic = kafkaTopic;
        }
    }
}
