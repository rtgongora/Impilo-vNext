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
    private GovernanceProperties governance = new GovernanceProperties();
    private MusheXProperties mushex = new MusheXProperties();
    private FundoProperties fundo = new FundoProperties();
    private LearningPlatformSyncProperties learningPlatformSync = new LearningPlatformSyncProperties();
    private CouncilRegulatoryProperties councilRegulatory = new CouncilRegulatoryProperties();
    private IdentityProperties identity = new IdentityProperties();

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

    public GovernanceProperties getGovernance() { return governance; }
    public void setGovernance(GovernanceProperties governance) { this.governance = governance; }

    public MusheXProperties getMushex() { return mushex; }
    public void setMushex(MusheXProperties mushex) { this.mushex = mushex; }

    public FundoProperties getFundo() { return fundo; }
    public void setFundo(FundoProperties fundo) { this.fundo = fundo; }

    public LearningPlatformSyncProperties getLearningPlatformSync() {
        return learningPlatformSync;
    }

    public void setLearningPlatformSync(LearningPlatformSyncProperties learningPlatformSync) {
        this.learningPlatformSync = learningPlatformSync;
    }

    public CouncilRegulatoryProperties getCouncilRegulatory() { return councilRegulatory; }
    public void setCouncilRegulatory(CouncilRegulatoryProperties councilRegulatory) {
        this.councilRegulatory = councilRegulatory;
    }

    public IdentityProperties getIdentity() { return identity; }
    public void setIdentity(IdentityProperties identity) { this.identity = identity; }

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

    /** Optional Workforce Governance service (assignments / scope summaries). */
    public static class GovernanceProperties {
        private boolean enabled = false;
        private String baseUrl = "http://localhost:8165";

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    }

    /** MusheX payment intent API (financial truth for council fees). */
    public static class MusheXProperties {
        private boolean enabled = false;
        private String baseUrl = "http://localhost:8102";

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    }

    /** Impilo Fundo (Moodle) webhook integration. */
    public static class FundoProperties {
        private boolean webhookEnabled = true;
        private String webhookSharedSecret = "CHANGE_ME";
        private boolean autoAcceptCpdFromFundo = false;

        public boolean isWebhookEnabled() { return webhookEnabled; }
        public void setWebhookEnabled(boolean webhookEnabled) { this.webhookEnabled = webhookEnabled; }
        public String getWebhookSharedSecret() { return webhookSharedSecret; }
        public void setWebhookSharedSecret(String webhookSharedSecret) { this.webhookSharedSecret = webhookSharedSecret; }
        public boolean isAutoAcceptCpdFromFundo() { return autoAcceptCpdFromFundo; }
        public void setAutoAcceptCpdFromFundo(boolean autoAcceptCpdFromFundo) {
            this.autoAcceptCpdFromFundo = autoAcceptCpdFromFundo;
        }
    }

    /** Optional sync to learning-service after Fundo webhook ingestion. */
    public static class LearningPlatformSyncProperties {
        private String baseUrl = "";
        private String internalApiKey = "";

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getInternalApiKey() {
            return internalApiKey;
        }

        public void setInternalApiKey(String internalApiKey) {
            this.internalApiKey = internalApiKey;
        }
    }

    /** Experience Doctrine: Impilo ID (Health ID) anchors provider profiles. */
    public static class IdentityProperties {
        private boolean requireImpiloHealthIdOnProviderCreate = true;
        private boolean requireImpiloHealthIdForFacilityAffiliation = true;

        public boolean isRequireImpiloHealthIdOnProviderCreate() { return requireImpiloHealthIdOnProviderCreate; }
        public void setRequireImpiloHealthIdOnProviderCreate(boolean requireImpiloHealthIdOnProviderCreate) {
            this.requireImpiloHealthIdOnProviderCreate = requireImpiloHealthIdOnProviderCreate;
        }
        public boolean isRequireImpiloHealthIdForFacilityAffiliation() {
            return requireImpiloHealthIdForFacilityAffiliation;
        }
        public void setRequireImpiloHealthIdForFacilityAffiliation(boolean requireImpiloHealthIdForFacilityAffiliation) {
            this.requireImpiloHealthIdForFacilityAffiliation = requireImpiloHealthIdForFacilityAffiliation;
        }
    }

    /** Optional Tshepo policy gate for council staff / provider self-service actions. */
    public static class CouncilRegulatoryProperties {
        private boolean policyEnabled = false;
        private String tshepoPolicyBaseUrl = "http://localhost:8081";
        /** When policy is enabled, deny workflow actions if Tshepo is unreachable or returns an empty body. */
        private boolean policyDenyWhenUnreachable = true;
        /** Subscribe to {@code mushex.payment.status.changed} to settle Varapi obligations without polling. */
        private boolean mushexPaymentStatusKafkaEnabled = false;
        /**
         * Channel A live council/HPA verification adapter seam. OFF by default — Wave 1
         * ingests council data only via manual bulk upload. When true, the
         * {@code HttpCouncilRegistryAdapter} bean is created and an enabled per-council
         * registration triggers a live pre-verification call ahead of the local registry.
         */
        private boolean liveAdapterEnabled = false;

        public boolean isPolicyEnabled() { return policyEnabled; }
        public void setPolicyEnabled(boolean policyEnabled) { this.policyEnabled = policyEnabled; }
        public String getTshepoPolicyBaseUrl() { return tshepoPolicyBaseUrl; }
        public void setTshepoPolicyBaseUrl(String tshepoPolicyBaseUrl) { this.tshepoPolicyBaseUrl = tshepoPolicyBaseUrl; }
        public boolean isPolicyDenyWhenUnreachable() { return policyDenyWhenUnreachable; }
        public void setPolicyDenyWhenUnreachable(boolean policyDenyWhenUnreachable) {
            this.policyDenyWhenUnreachable = policyDenyWhenUnreachable;
        }
        public boolean isLiveAdapterEnabled() { return liveAdapterEnabled; }
        public void setLiveAdapterEnabled(boolean liveAdapterEnabled) {
            this.liveAdapterEnabled = liveAdapterEnabled;
        }
        public boolean isMushexPaymentStatusKafkaEnabled() { return mushexPaymentStatusKafkaEnabled; }
        public void setMushexPaymentStatusKafkaEnabled(boolean mushexPaymentStatusKafkaEnabled) {
            this.mushexPaymentStatusKafkaEnabled = mushexPaymentStatusKafkaEnabled;
        }
    }
}
