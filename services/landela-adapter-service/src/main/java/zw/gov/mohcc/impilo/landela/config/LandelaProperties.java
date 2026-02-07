package zw.gov.mohcc.impilo.landela.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Landela Adapter configuration properties.
 * Bound from application.yml under the 'landela' prefix.
 *
 * Supports dual-mode operation:
 *   LANDELA  — routes storage to external Landela DMS
 *   INTERNAL — routes storage to internal MinIO cluster
 */
@ConfigurationProperties(prefix = "landela")
public class LandelaProperties {

    private String mode = "INTERNAL";
    private Landela landela = new Landela();
    private Internal internal = new Internal();
    private SignedUrl signedUrl = new SignedUrl();
    private Kafka kafka = new Kafka();
    private Outbox outbox = new Outbox();

    // --- Nested classes ---

    public static class Landela {
        private String baseUrl;
        private String apiKey;
        private int timeout = 30000;

        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }
        public int getTimeout() { return timeout; }
        public void setTimeout(int timeout) { this.timeout = timeout; }
    }

    public static class Internal {
        private String minioEndpoint = "http://localhost:9000";
        private String minioBucket = "impilo-documents";
        private String minioAccessKey = "minioadmin";
        private String minioSecretKey = "minioadmin";

        public String getMinioEndpoint() { return minioEndpoint; }
        public void setMinioEndpoint(String minioEndpoint) { this.minioEndpoint = minioEndpoint; }
        public String getMinioBucket() { return minioBucket; }
        public void setMinioBucket(String minioBucket) { this.minioBucket = minioBucket; }
        public String getMinioAccessKey() { return minioAccessKey; }
        public void setMinioAccessKey(String minioAccessKey) { this.minioAccessKey = minioAccessKey; }
        public String getMinioSecretKey() { return minioSecretKey; }
        public void setMinioSecretKey(String minioSecretKey) { this.minioSecretKey = minioSecretKey; }
    }

    public static class SignedUrl {
        private int ttlSeconds = 300;

        public int getTtlSeconds() { return ttlSeconds; }
        public void setTtlSeconds(int ttlSeconds) { this.ttlSeconds = ttlSeconds; }
    }

    public static class Kafka {
        private String topicPrefix = "docs";

        public String getTopicPrefix() { return topicPrefix; }
        public void setTopicPrefix(String topicPrefix) { this.topicPrefix = topicPrefix; }
    }

    public static class Outbox {
        private long pollIntervalMs = 1000;

        public long getPollIntervalMs() { return pollIntervalMs; }
        public void setPollIntervalMs(long pollIntervalMs) { this.pollIntervalMs = pollIntervalMs; }
    }

    // --- Getters/Setters ---

    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }
    public Landela getLandela() { return landela; }
    public void setLandela(Landela landela) { this.landela = landela; }
    public Internal getInternal() { return internal; }
    public void setInternal(Internal internal) { this.internal = internal; }
    public SignedUrl getSignedUrl() { return signedUrl; }
    public void setSignedUrl(SignedUrl signedUrl) { this.signedUrl = signedUrl; }
    public Kafka getKafka() { return kafka; }
    public void setKafka(Kafka kafka) { this.kafka = kafka; }
    public Outbox getOutbox() { return outbox; }
    public void setOutbox(Outbox outbox) { this.outbox = outbox; }
}
