package zw.gov.mohcc.impilo.pharmacy.elmis.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import zw.gov.mohcc.impilo.pharmacy.elmis.domain.ConnectorMode;

/**
 * Type-safe configuration properties for the eLMIS adapter.
 *
 * <p>Bound from the {@code elmis.*} namespace in application.yml.
 * Controls the connector mode, external endpoint configuration,
 * retry policy, and code-system mapping.</p>
 */
@ConfigurationProperties(prefix = "elmis")
public class ElmisAdapterProperties {

    private Connector connector = new Connector();
    private Mapping mapping = new Mapping();
    private KafkaTopics kafka = new KafkaTopics();

    // ── Getters / setters ──────────────────────────────────────────────

    public Connector getConnector() { return connector; }
    public void setConnector(Connector connector) { this.connector = connector; }

    public Mapping getMapping() { return mapping; }
    public void setMapping(Mapping mapping) { this.mapping = mapping; }

    public KafkaTopics getKafka() { return kafka; }
    public void setKafka(KafkaTopics kafka) { this.kafka = kafka; }

    // ── Nested configuration classes ───────────────────────────────────

    /**
     * Connector settings for eLMIS communication.
     *
     * <p>{@code mode}: REST, CSV, or KAFKA. Default: REST.</p>
     * <p>{@code externalUrl}: base URL of the external eLMIS API.</p>
     * <p>{@code timeoutSeconds}: HTTP request timeout. Default: 30.</p>
     * <p>{@code retryMaxAttempts}: max retry attempts on failure. Default: 3.</p>
     * <p>{@code retryBackoffMs}: backoff between retries (ms). Default: 1000.</p>
     */
    public static class Connector {
        private ConnectorMode mode = ConnectorMode.REST;
        private String externalUrl = "http://localhost:9080/api";
        private int timeoutSeconds = 30;
        private int retryMaxAttempts = 3;
        private long retryBackoffMs = 1000;

        public ConnectorMode getMode() { return mode; }
        public void setMode(ConnectorMode mode) { this.mode = mode; }

        public String getExternalUrl() { return externalUrl; }
        public void setExternalUrl(String externalUrl) { this.externalUrl = externalUrl; }

        public int getTimeoutSeconds() { return timeoutSeconds; }
        public void setTimeoutSeconds(int timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }

        public int getRetryMaxAttempts() { return retryMaxAttempts; }
        public void setRetryMaxAttempts(int retryMaxAttempts) { this.retryMaxAttempts = retryMaxAttempts; }

        public long getRetryBackoffMs() { return retryBackoffMs; }
        public void setRetryBackoffMs(long retryBackoffMs) { this.retryBackoffMs = retryBackoffMs; }
    }

    /**
     * Code-system mapping configuration.
     *
     * <p>Defines the OID/URN namespaces used to translate between
     * Impilo internal codes and eLMIS external codes.</p>
     */
    public static class Mapping {
        private String facilityCodeSystem = "urn:zw:mohcc:facility";
        private String drugCodeSystem = "urn:zw:mohcc:drug";

        public String getFacilityCodeSystem() { return facilityCodeSystem; }
        public void setFacilityCodeSystem(String facilityCodeSystem) { this.facilityCodeSystem = facilityCodeSystem; }

        public String getDrugCodeSystem() { return drugCodeSystem; }
        public void setDrugCodeSystem(String drugCodeSystem) { this.drugCodeSystem = drugCodeSystem; }
    }

    /**
     * Kafka topic names for eLMIS adapter events.
     */
    public static class KafkaTopics {
        private String stockReceiptTopic = "pharmacy.stock.receipt";
        private String stockReconcileTopic = "pharmacy.reconcile.result";
        private String orderForwardTopic = "elmis.order.forwarded";

        public String getStockReceiptTopic() { return stockReceiptTopic; }
        public void setStockReceiptTopic(String stockReceiptTopic) { this.stockReceiptTopic = stockReceiptTopic; }

        public String getStockReconcileTopic() { return stockReconcileTopic; }
        public void setStockReconcileTopic(String stockReconcileTopic) { this.stockReconcileTopic = stockReconcileTopic; }

        public String getOrderForwardTopic() { return orderForwardTopic; }
        public void setOrderForwardTopic(String orderForwardTopic) { this.orderForwardTopic = orderForwardTopic; }
    }
}
