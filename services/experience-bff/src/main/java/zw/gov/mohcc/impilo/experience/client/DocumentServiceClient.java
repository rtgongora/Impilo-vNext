package zw.gov.mohcc.impilo.experience.client;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;

import java.util.UUID;

/**
 * HTTP client for the Document Store sovereign service.
 *
 * <p>Provides access to document metadata and pre-signed download URLs.
 * The document-service stores binary objects in MinIO with SHA-256
 * deduplication and optional antivirus scanning.</p>
 *
 * <p>Note: Binary upload requires multipart form data which is handled
 * directly by the document-service. This client provides metadata and
 * URL generation operations that can be used by the BFF to enrich
 * clinical document records with download capabilities.</p>
 */
@Component
public class DocumentServiceClient {

    private static final Logger log = LoggerFactory.getLogger(DocumentServiceClient.class);

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public DocumentServiceClient(RestTemplate serviceRestTemplate,
                                 ServiceClientConfig.ServiceEndpoints endpoints) {
        this.restTemplate = serviceRestTemplate;
        this.baseUrl = endpoints.documentStoreBaseUrl();
    }

    /**
     * Get metadata for a stored object.
     *
     * @param objectId the document-service object UUID
     * @return object metadata (filename, mime type, size, hash, etc.)
     */
    public JsonNode getObjectMetadata(UUID objectId) {
        String url = baseUrl + "/v1/internal/objects/" + objectId;
        log.debug("DocStore: Getting metadata for object={}", objectId);
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    /**
     * Generate a pre-signed download URL for an object.
     *
     * @param objectId the document-service object UUID
     * @return signed URL response with time-limited download URL
     */
    public JsonNode getSignedUrl(UUID objectId) {
        String url = baseUrl + "/v1/internal/objects/" + objectId + "/signed-url";
        log.debug("DocStore: Generating signed URL for object={}", objectId);
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    /**
     * Generate preview payload for inline-safe document rendering.
     */
    public JsonNode getPreview(UUID objectId) {
        String url = baseUrl + "/v1/internal/objects/" + objectId + "/preview";
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    /**
     * Find objects by SHA-256 content hash (deduplication check).
     *
     * @param sha256 the content hash
     * @return list of matching objects
     */
    public JsonNode findByHash(String sha256) {
        String url = baseUrl + "/v1/internal/objects/by-hash/" + sha256;
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    /**
     * Upload binary content to document-store (MinIO-backed object registry).
     */
    public JsonNode uploadObject(byte[] bytes, String originalFilename, String mimeType) {
        String url = baseUrl + "/v1/internal/objects";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        ByteArrayResource fileResource = new ByteArrayResource(bytes) {
            @Override
            public String getFilename() {
                return originalFilename;
            }
        };

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", fileResource);
        body.add("mimeType", mimeType);
        if (originalFilename != null && !originalFilename.isBlank()) {
            body.add("originalFilename", originalFilename);
        }

        HttpEntity<MultiValueMap<String, Object>> request = new HttpEntity<>(body, headers);
        log.debug("DocStore: Uploading object filename={} mime={}", originalFilename, mimeType);
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, request, JsonNode.class);
        return extractData(response);
    }

    /** Request OCR / scanning pipeline for a stored object. */
    public JsonNode requestOcr(UUID objectId) {
        String url = baseUrl + "/v1/internal/objects/" + objectId + "/ocr";
        log.debug("DocStore: Requesting OCR for object={}", objectId);
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, null, JsonNode.class);
        return extractData(response);
    }

    /** Latest OCR job status and extracted text for an object. */
    public JsonNode getLatestOcr(UUID objectId) {
        String url = baseUrl + "/v1/internal/objects/" + objectId + "/ocr";
        log.debug("DocStore: Getting latest OCR for object={}", objectId);
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    private JsonNode extractData(ResponseEntity<JsonNode> response) {
        if (response.getBody() != null && response.getBody().has("data")) {
            return response.getBody().get("data");
        }
        return response.getBody();
    }
}
