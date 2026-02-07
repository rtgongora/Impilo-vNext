package zw.gov.mohcc.impilo.docstore.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Document Store service configuration properties.
 * Bound from application.yml under the 'document-store' prefix.
 */
@Component
@ConfigurationProperties(prefix = "document-store")
public class DocumentStoreProperties {

    private Minio minio = new Minio();
    private SignedUrl signedUrl = new SignedUrl();
    private Scan scan = new Scan();
    private int maxFileSizeMb = 50;

    // --- Nested classes ---

    public static class Minio {
        private String endpoint = "http://localhost:9000";
        private String bucket = "impilo-documents";
        private String accessKey = "minioadmin";
        private String secretKey = "minioadmin";

        public String getEndpoint() { return endpoint; }
        public void setEndpoint(String endpoint) { this.endpoint = endpoint; }
        public String getBucket() { return bucket; }
        public void setBucket(String bucket) { this.bucket = bucket; }
        public String getAccessKey() { return accessKey; }
        public void setAccessKey(String accessKey) { this.accessKey = accessKey; }
        public String getSecretKey() { return secretKey; }
        public void setSecretKey(String secretKey) { this.secretKey = secretKey; }
    }

    public static class SignedUrl {
        private int ttlSeconds = 300;

        public int getTtlSeconds() { return ttlSeconds; }
        public void setTtlSeconds(int ttlSeconds) { this.ttlSeconds = ttlSeconds; }
    }

    public static class Scan {
        private boolean enabled = false;
        private ScannerType scannerType = ScannerType.NOOP;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public ScannerType getScannerType() { return scannerType; }
        public void setScannerType(ScannerType scannerType) { this.scannerType = scannerType; }
    }

    public enum ScannerType {
        NOOP, CLAMAV
    }

    // --- Getters/Setters ---

    public Minio getMinio() { return minio; }
    public void setMinio(Minio minio) { this.minio = minio; }
    public SignedUrl getSignedUrl() { return signedUrl; }
    public void setSignedUrl(SignedUrl signedUrl) { this.signedUrl = signedUrl; }
    public Scan getScan() { return scan; }
    public void setScan(Scan scan) { this.scan = scan; }
    public int getMaxFileSizeMb() { return maxFileSizeMb; }
    public void setMaxFileSizeMb(int maxFileSizeMb) { this.maxFileSizeMb = maxFileSizeMb; }
}
