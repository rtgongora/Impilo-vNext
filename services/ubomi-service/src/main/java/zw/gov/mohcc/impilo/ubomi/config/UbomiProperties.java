package zw.gov.mohcc.impilo.ubomi.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ubomi")
public class UbomiProperties {

    private VitoProperties vito = new VitoProperties();
    private OutboxProperties outbox = new OutboxProperties();

    public VitoProperties getVito() { return vito; }
    public void setVito(VitoProperties vito) { this.vito = vito; }

    public OutboxProperties getOutbox() { return outbox; }
    public void setOutbox(OutboxProperties outbox) { this.outbox = outbox; }

    public static class VitoProperties {
        private String baseUrl = "http://localhost:8082";

        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    }

    public static class OutboxProperties {
        private long pollIntervalMs = 1000;

        public long getPollIntervalMs() { return pollIntervalMs; }
        public void setPollIntervalMs(long pollIntervalMs) { this.pollIntervalMs = pollIntervalMs; }
    }
}
