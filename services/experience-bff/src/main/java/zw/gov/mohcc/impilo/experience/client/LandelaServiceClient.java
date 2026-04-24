package zw.gov.mohcc.impilo.experience.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Minimal Landela (document gateway) client for large import payloads stored as documents.
 */
@Component
public class LandelaServiceClient {

    private static final Logger log = LoggerFactory.getLogger(LandelaServiceClient.class);
    private static final int MAX_DOWNLOAD_BYTES = 5 * 1024 * 1024;

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public LandelaServiceClient(RestTemplate serviceRestTemplate, ServiceClientConfig.ServiceEndpoints endpoints) {
        this.restTemplate = serviceRestTemplate;
        this.baseUrl = endpoints.landelaBaseUrl();
    }

    /**
     * Downloads document bytes as UTF-8 text (CSV). Intended for text/csv uploads.
     */
    public String downloadDocumentAsUtf8Text(UUID documentId) {
        String url = baseUrl + "/v1/internal/documents/" + documentId + "/download";
        log.info("LANDELA: downloading document id={}", documentId);
        ResponseEntity<byte[]> response = restTemplate.getForEntity(url, byte[].class);
        byte[] body = response.getBody();
        if (body == null) {
            return "";
        }
        if (body.length > MAX_DOWNLOAD_BYTES) {
            throw new IllegalStateException("Document exceeds maximum download size for import (" + MAX_DOWNLOAD_BYTES + " bytes)");
        }
        return new String(body, StandardCharsets.UTF_8);
    }
}
