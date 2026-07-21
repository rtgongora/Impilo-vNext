package zw.gov.mohcc.impilo.tuso.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "tuso")
public class TusoProperties {

    private String registryMode = "STANDALONE";
    private GofrProperties gofr = new GofrProperties();
    private ZiboProperties zibo = new ZiboProperties();
    private VarapiProperties varapi = new VarapiProperties();
    private RitoProperties rito = new RitoProperties();
    private AlertThresholds alerts = new AlertThresholds();
    private int configCacheTtlSeconds = 300;
    private OutboxProperties outbox = new OutboxProperties();

    // --- Getters/Setters ---

    public String getRegistryMode() { return registryMode; }
    public void setRegistryMode(String registryMode) { this.registryMode = registryMode; }

    public boolean isGofrMode() { return "GOFR".equalsIgnoreCase(registryMode); }
    public boolean isStandaloneMode() { return "STANDALONE".equalsIgnoreCase(registryMode); }

    public GofrProperties getGofr() { return gofr; }
    public void setGofr(GofrProperties gofr) { this.gofr = gofr; }

    public ZiboProperties getZibo() { return zibo; }
    public void setZibo(ZiboProperties zibo) { this.zibo = zibo; }

    public VarapiProperties getVarapi() { return varapi; }
    public void setVarapi(VarapiProperties varapi) { this.varapi = varapi; }

    public RitoProperties getRito() { return rito; }
    public void setRito(RitoProperties rito) { this.rito = rito; }

    public AlertThresholds getAlerts() { return alerts; }
    public void setAlerts(AlertThresholds alerts) { this.alerts = alerts; }

    public int getConfigCacheTtlSeconds() { return configCacheTtlSeconds; }
    public void setConfigCacheTtlSeconds(int configCacheTtlSeconds) { this.configCacheTtlSeconds = configCacheTtlSeconds; }

    public OutboxProperties getOutbox() { return outbox; }
    public void setOutbox(OutboxProperties outbox) { this.outbox = outbox; }

    // --- Nested classes ---

    /** Rito Experience & Reputation — read-only facility experience summary (RW8). */
    public static class RitoProperties {
        private boolean enabled = false;
        private String baseUrl = "http://localhost:8391";

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    }

    public static class GofrProperties {
        private String baseUrl = "http://localhost:3000";
        private String syncCron = "0 */6 * * * *";
        private boolean enabled = false;

        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
        public String getSyncCron() { return syncCron; }
        public void setSyncCron(String syncCron) { this.syncCron = syncCron; }
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
    }

    public static class ZiboProperties {
        private String baseUrl = "http://localhost:8085";
        private String validationMode = "LENIENT";
        private boolean enabled = false;

        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
        public String getValidationMode() { return validationMode; }
        public void setValidationMode(String validationMode) { this.validationMode = validationMode; }
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public boolean isStrict() { return "STRICT".equalsIgnoreCase(validationMode); }
    }

    public static class VarapiProperties {
        private String baseUrl = "http://localhost:8083";

        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    }

    private MushexProperties mushex = new MushexProperties();

    public MushexProperties getMushex() { return mushex; }
    public void setMushex(MushexProperties mushex) { this.mushex = mushex; }

    /** MusheX payment-intent integration for HPA registration fees. */
    public static class MushexProperties {
        private boolean enabled = true;
        private String baseUrl = "http://localhost:8102";
        /** Rig/preview only: attach Impilo-simulation metadata so mushex sandbox auto-settles the intent. */
        private boolean sandboxSimulation = false;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
        public boolean isSandboxSimulation() { return sandboxSimulation; }
        public void setSandboxSimulation(boolean sandboxSimulation) { this.sandboxSimulation = sandboxSimulation; }
    }

    public static class AlertThresholds {
        private int queueWaitThresholdMinutes = 60;
        private int taskStagnationThresholdMinutes = 120;
        private int lowBedOccupancyPct = 20;
        private int highBedOccupancyPct = 90;

        public int getQueueWaitThresholdMinutes() { return queueWaitThresholdMinutes; }
        public void setQueueWaitThresholdMinutes(int v) { this.queueWaitThresholdMinutes = v; }
        public int getTaskStagnationThresholdMinutes() { return taskStagnationThresholdMinutes; }
        public void setTaskStagnationThresholdMinutes(int v) { this.taskStagnationThresholdMinutes = v; }
        public int getLowBedOccupancyPct() { return lowBedOccupancyPct; }
        public void setLowBedOccupancyPct(int v) { this.lowBedOccupancyPct = v; }
        public int getHighBedOccupancyPct() { return highBedOccupancyPct; }
        public void setHighBedOccupancyPct(int v) { this.highBedOccupancyPct = v; }
    }

    public static class OutboxProperties {
        private int pollIntervalMs = 1000;
        private String kafkaTopicPrefix = "tuso";

        public int getPollIntervalMs() { return pollIntervalMs; }
        public void setPollIntervalMs(int pollIntervalMs) { this.pollIntervalMs = pollIntervalMs; }
        public String getKafkaTopicPrefix() { return kafkaTopicPrefix; }
        public void setKafkaTopicPrefix(String kafkaTopicPrefix) { this.kafkaTopicPrefix = kafkaTopicPrefix; }
    }
}
