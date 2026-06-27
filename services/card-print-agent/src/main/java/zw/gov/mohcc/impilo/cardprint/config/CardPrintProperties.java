package zw.gov.mohcc.impilo.cardprint.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Typed configuration properties for the Card Print Agent.
 * Bound from the {@code card-print} prefix in application.yml.
 */
@ConfigurationProperties(prefix = "card-print")
public class CardPrintProperties {

    private Templates templates = new Templates();
    private Spooler spooler = new Spooler();
    private Landela landela = new Landela();
    private Vito vito = new Vito();
    private Qr qr = new Qr();
    private Processor processor = new Processor();
    private Outbox outbox = new Outbox();

    public Templates getTemplates() {
        return templates;
    }

    public void setTemplates(Templates templates) {
        this.templates = templates;
    }

    public Spooler getSpooler() {
        return spooler;
    }

    public void setSpooler(Spooler spooler) {
        this.spooler = spooler;
    }

    public Landela getLandela() {
        return landela;
    }

    public void setLandela(Landela landela) {
        this.landela = landela;
    }

    public Vito getVito() {
        return vito;
    }

    public void setVito(Vito vito) {
        this.vito = vito;
    }

    public Qr getQr() {
        return qr;
    }

    public void setQr(Qr qr) {
        this.qr = qr;
    }

    public Processor getProcessor() {
        return processor;
    }

    public void setProcessor(Processor processor) {
        this.processor = processor;
    }

    public Outbox getOutbox() {
        return outbox;
    }

    public void setOutbox(Outbox outbox) {
        this.outbox = outbox;
    }

    public static class Templates {
        private String providerCard = "classpath:templates/provider-card.json";
        private String clientCard = "classpath:templates/client-card.json";
        private String shareSlip = "classpath:templates/share-slip.json";
        private String emergencyCapsule = "classpath:templates/emergency-capsule.json";

        public String getProviderCard() {
            return providerCard;
        }

        public void setProviderCard(String providerCard) {
            this.providerCard = providerCard;
        }

        public String getClientCard() {
            return clientCard;
        }

        public void setClientCard(String clientCard) {
            this.clientCard = clientCard;
        }

        public String getShareSlip() {
            return shareSlip;
        }

        public void setShareSlip(String shareSlip) {
            this.shareSlip = shareSlip;
        }

        public String getEmergencyCapsule() {
            return emergencyCapsule;
        }

        public void setEmergencyCapsule(String emergencyCapsule) {
            this.emergencyCapsule = emergencyCapsule;
        }
    }

    public static class Spooler {
        private SpoolerType type = SpoolerType.FILE;
        private String outputDir = "/tmp/card-print-output";

        public SpoolerType getType() {
            return type;
        }

        public void setType(SpoolerType type) {
            this.type = type;
        }

        public String getOutputDir() {
            return outputDir;
        }

        public void setOutputDir(String outputDir) {
            this.outputDir = outputDir;
        }

        public enum SpoolerType {
            FILE, NETWORK
        }
    }

    public static class Landela {
        private String baseUrl = "http://localhost:8092";

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }
    }

    /**
     * VITO (smart-card lifecycle SoR) connection. The agent calls VITO's internal
     * print endpoint to provision the personalised card's public key (G032).
     */
    public static class Vito {
        private String baseUrl = "http://localhost:8082";
        /** Actor id the agent presents on its internal print callback for audit. */
        private String actorId = "card-print-agent";

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getActorId() {
            return actorId;
        }

        public void setActorId(String actorId) {
            this.actorId = actorId;
        }
    }

    public static class Qr {
        private String hmacSecret = "changeme-not-for-production";

        public String getHmacSecret() {
            return hmacSecret;
        }

        public void setHmacSecret(String hmacSecret) {
            this.hmacSecret = hmacSecret;
        }
    }

    public static class Processor {
        private long pollIntervalMs = 5000;
        private int batchSize = 10;

        public long getPollIntervalMs() {
            return pollIntervalMs;
        }

        public void setPollIntervalMs(long pollIntervalMs) {
            this.pollIntervalMs = pollIntervalMs;
        }

        public int getBatchSize() {
            return batchSize;
        }

        public void setBatchSize(int batchSize) {
            this.batchSize = batchSize;
        }
    }

    public static class Outbox {
        private long pollIntervalMs = 3000;
        private int batchSize = 50;

        public long getPollIntervalMs() {
            return pollIntervalMs;
        }

        public void setPollIntervalMs(long pollIntervalMs) {
            this.pollIntervalMs = pollIntervalMs;
        }

        public int getBatchSize() {
            return batchSize;
        }

        public void setBatchSize(int batchSize) {
            this.batchSize = batchSize;
        }
    }
}
