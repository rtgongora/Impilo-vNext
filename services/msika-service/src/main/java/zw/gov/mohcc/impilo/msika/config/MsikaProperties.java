package zw.gov.mohcc.impilo.msika.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "msika")
public class MsikaProperties {

    private Outbox outbox = new Outbox();
    private Packs packs = new Packs();

    public Outbox getOutbox() { return outbox; }
    public void setOutbox(Outbox outbox) { this.outbox = outbox; }
    public Packs getPacks() { return packs; }
    public void setPacks(Packs packs) { this.packs = packs; }

    public static class Outbox {
        private long pollIntervalMs = 2000;
        public long getPollIntervalMs() { return pollIntervalMs; }
        public void setPollIntervalMs(long pollIntervalMs) { this.pollIntervalMs = pollIntervalMs; }
    }

    public static class Packs {
        private int cacheTtlMinutes = 30;
        public int getCacheTtlMinutes() { return cacheTtlMinutes; }
        public void setCacheTtlMinutes(int cacheTtlMinutes) { this.cacheTtlMinutes = cacheTtlMinutes; }
    }
}
