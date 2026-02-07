package zw.gov.mohcc.impilo.varapi.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "varapi")
public class VarapiProperties {

    private String registryMode = "STANDALONE";
    private Argon2Properties argon2 = new Argon2Properties();
    private HmacProperties hmac = new HmacProperties();
    private ZiboProperties zibo = new ZiboProperties();
    private TusoProperties tuso = new TusoProperties();
    private DocumentProperties document = new DocumentProperties();
    private TokenProperties token = new TokenProperties();
    private OutboxProperties outbox = new OutboxProperties();

    public String getRegistryMode() { return registryMode; }
    public void setRegistryMode(String registryMode) { this.registryMode = registryMode; }

    public boolean isStandaloneMode() { return "STANDALONE".equalsIgnoreCase(registryMode); }
    public boolean isExternalAdapterMode() { return "EXTERNAL".equalsIgnoreCase(registryMode); }

    public Argon2Properties getArgon2() { return argon2; }
    public void setArgon2(Argon2Properties argon2) { this.argon2 = argon2; }

    public HmacProperties getHmac() { return hmac; }
    public void setHmac(HmacProperties hmac) { this.hmac = hmac; }

    public ZiboProperties getZibo() { return zibo; }
    public void setZibo(ZiboProperties zibo) { this.zibo = zibo; }

    public TusoProperties getTuso() { return tuso; }
    public void setTuso(TusoProperties tuso) { this.tuso = tuso; }

    public DocumentProperties getDocument() { return document; }
    public void setDocument(DocumentProperties document) { this.document = document; }

    public TokenProperties getToken() { return token; }
    public void setToken(TokenProperties token) { this.token = token; }

    public OutboxProperties getOutbox() { return outbox; }
    public void setOutbox(OutboxProperties outbox) { this.outbox = outbox; }

    // --- Nested ---

    public static class Argon2Properties {
        private int memoryCostKb = 19456;
        private int iterations = 2;
        private int parallelism = 1;

        public int getMemoryCostKb() { return memoryCostKb; }
        public void setMemoryCostKb(int v) { this.memoryCostKb = v; }
        public int getIterations() { return iterations; }
        public void setIterations(int v) { this.iterations = v; }
        public int getParallelism() { return parallelism; }
        public void setParallelism(int v) { this.parallelism = v; }
    }

    public static class HmacProperties {
        private String pepper = "CHANGE_ME_IN_PRODUCTION_varapi_pepper_2024";

        public String getPepper() { return pepper; }
        public void setPepper(String pepper) { this.pepper = pepper; }
    }

    public static class ZiboProperties {
        private String baseUrl = "http://localhost:8085";
        private String validationMode = "LENIENT";
        private boolean enabled = false;

        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String v) { this.baseUrl = v; }
        public String getValidationMode() { return validationMode; }
        public void setValidationMode(String v) { this.validationMode = v; }
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean v) { this.enabled = v; }
        public boolean isStrict() { return "STRICT".equalsIgnoreCase(validationMode); }
    }

    public static class TusoProperties {
        private String baseUrl = "http://localhost:8084";

        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String v) { this.baseUrl = v; }
    }

    public static class DocumentProperties {
        private String storageType = "MINIO";
        private String minioEndpoint = "http://localhost:9000";
        private String minioBucket = "varapi-documents";
        private String minioAccessKey = "minioadmin";
        private String minioSecretKey = "minioadmin";

        public String getStorageType() { return storageType; }
        public void setStorageType(String v) { this.storageType = v; }
        public String getMinioEndpoint() { return minioEndpoint; }
        public void setMinioEndpoint(String v) { this.minioEndpoint = v; }
        public String getMinioBucket() { return minioBucket; }
        public void setMinioBucket(String v) { this.minioBucket = v; }
        public String getMinioAccessKey() { return minioAccessKey; }
        public void setMinioAccessKey(String v) { this.minioAccessKey = v; }
        public String getMinioSecretKey() { return minioSecretKey; }
        public void setMinioSecretKey(String v) { this.minioSecretKey = v; }
    }

    public static class TokenProperties {
        private String prefix = "VA-";
        private int digitCount = 10;

        public String getPrefix() { return prefix; }
        public void setPrefix(String v) { this.prefix = v; }
        public int getDigitCount() { return digitCount; }
        public void setDigitCount(int v) { this.digitCount = v; }
    }

    public static class OutboxProperties {
        private int pollIntervalMs = 1000;
        private String kafkaTopicPrefix = "varapi";

        public int getPollIntervalMs() { return pollIntervalMs; }
        public void setPollIntervalMs(int v) { this.pollIntervalMs = v; }
        public String getKafkaTopicPrefix() { return kafkaTopicPrefix; }
        public void setKafkaTopicPrefix(String v) { this.kafkaTopicPrefix = v; }
    }
}
