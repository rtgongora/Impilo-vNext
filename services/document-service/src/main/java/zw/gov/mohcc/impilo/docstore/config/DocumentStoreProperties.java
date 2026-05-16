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
    private LandelaAdapter landelaAdapter = new LandelaAdapter();
    private Storage storage = new Storage();
    private Ocr ocr = new Ocr();
    private Signature signature = new Signature();
    private OcrStub ocrStub = new OcrStub();
    private SignatureStub signatureStub = new SignatureStub();
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

    public static class Storage {
        private StorageProviderType providerType = StorageProviderType.MINIO;

        public StorageProviderType getProviderType() { return providerType; }
        public void setProviderType(StorageProviderType providerType) { this.providerType = providerType; }
    }

    public static class LandelaAdapter {
        private String baseUrl = "";
        private String apiKey = "";

        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }
    }

    public static class Ocr {
        private OcrProviderType providerType = OcrProviderType.NOOP;

        public OcrProviderType getProviderType() { return providerType; }
        public void setProviderType(OcrProviderType providerType) { this.providerType = providerType; }
    }

    public static class Signature {
        private SignatureProviderType providerType = SignatureProviderType.NOOP;

        public SignatureProviderType getProviderType() { return providerType; }
        public void setProviderType(SignatureProviderType providerType) { this.providerType = providerType; }
    }

    public static class OcrStub {
        private String baseUrl = "";
        private String path = "/v1/ocr/extract";
        private String apiKey = "";

        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
        public String getPath() { return path; }
        public void setPath(String path) { this.path = path; }
        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }
    }

    public static class SignatureStub {
        private String baseUrl = "";
        private String path = "/v1/signature/sign";
        private String apiKey = "";

        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
        public String getPath() { return path; }
        public void setPath(String path) { this.path = path; }
        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }
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

    public enum StorageProviderType {
        MINIO, LANDELA_ADAPTER
    }

    public enum OcrProviderType {
        NOOP, EXTERNAL_STUB
    }

    public enum SignatureProviderType {
        NOOP, EXTERNAL_STUB
    }

    // --- Getters/Setters ---

    public Minio getMinio() { return minio; }
    public void setMinio(Minio minio) { this.minio = minio; }
    public LandelaAdapter getLandelaAdapter() { return landelaAdapter; }
    public void setLandelaAdapter(LandelaAdapter landelaAdapter) { this.landelaAdapter = landelaAdapter; }
    public Storage getStorage() { return storage; }
    public void setStorage(Storage storage) { this.storage = storage; }
    public Ocr getOcr() { return ocr; }
    public void setOcr(Ocr ocr) { this.ocr = ocr; }
    public Signature getSignature() { return signature; }
    public void setSignature(Signature signature) { this.signature = signature; }
    public OcrStub getOcrStub() { return ocrStub; }
    public void setOcrStub(OcrStub ocrStub) { this.ocrStub = ocrStub; }
    public SignatureStub getSignatureStub() { return signatureStub; }
    public void setSignatureStub(SignatureStub signatureStub) { this.signatureStub = signatureStub; }
    public SignedUrl getSignedUrl() { return signedUrl; }
    public void setSignedUrl(SignedUrl signedUrl) { this.signedUrl = signedUrl; }
    public Scan getScan() { return scan; }
    public void setScan(Scan scan) { this.scan = scan; }
    public int getMaxFileSizeMb() { return maxFileSizeMb; }
    public void setMaxFileSizeMb(int maxFileSizeMb) { this.maxFileSizeMb = maxFileSizeMb; }
}
