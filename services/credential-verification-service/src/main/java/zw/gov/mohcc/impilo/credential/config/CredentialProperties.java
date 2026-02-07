package zw.gov.mohcc.impilo.credential.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "credential")
public class CredentialProperties {

    private SigningProperties signing = new SigningProperties();
    private VerifyProperties verify = new VerifyProperties();
    private PdfProperties pdf = new PdfProperties();
    private OutboxProperties outbox = new OutboxProperties();

    public SigningProperties getSigning() { return signing; }
    public void setSigning(SigningProperties signing) { this.signing = signing; }

    public VerifyProperties getVerify() { return verify; }
    public void setVerify(VerifyProperties verify) { this.verify = verify; }

    public PdfProperties getPdf() { return pdf; }
    public void setPdf(PdfProperties pdf) { this.pdf = pdf; }

    public OutboxProperties getOutbox() { return outbox; }
    public void setOutbox(OutboxProperties outbox) { this.outbox = outbox; }

    // --- Nested ---

    public static class SigningProperties {
        private String keyStorePath = "keys/ed25519.pem";
        private String algorithm = "Ed25519";

        public String getKeyStorePath() { return keyStorePath; }
        public void setKeyStorePath(String keyStorePath) { this.keyStorePath = keyStorePath; }
        public String getAlgorithm() { return algorithm; }
        public void setAlgorithm(String algorithm) { this.algorithm = algorithm; }
    }

    public static class VerifyProperties {
        private String baseUrl = "http://localhost:8094/v1/public/verify";

        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    }

    public static class PdfProperties {
        private String issuerName = "Ministry of Health and Child Care, Zimbabwe";
        private String logoPath;

        public String getIssuerName() { return issuerName; }
        public void setIssuerName(String issuerName) { this.issuerName = issuerName; }
        public String getLogoPath() { return logoPath; }
        public void setLogoPath(String logoPath) { this.logoPath = logoPath; }
    }

    public static class OutboxProperties {
        private int pollIntervalMs = 1000;

        public int getPollIntervalMs() { return pollIntervalMs; }
        public void setPollIntervalMs(int pollIntervalMs) { this.pollIntervalMs = pollIntervalMs; }
    }
}
