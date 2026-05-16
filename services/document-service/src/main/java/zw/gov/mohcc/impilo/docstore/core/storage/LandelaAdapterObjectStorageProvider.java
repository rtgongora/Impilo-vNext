package zw.gov.mohcc.impilo.docstore.core.storage;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.docstore.config.DocumentStoreProperties;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Map;

/**
 * Storage provider backed by landela-adapter-service.
 */
@Component
public class LandelaAdapterObjectStorageProvider implements ObjectStorageProvider {

    private final DocumentStoreProperties properties;
    private final RestTemplate restTemplate;

    public LandelaAdapterObjectStorageProvider(DocumentStoreProperties properties) {
        this.properties = properties;
        this.restTemplate = new RestTemplate();
    }

    @Override
    public String providerType() {
        return "LANDELA_ADAPTER";
    }

    @Override
    public void ensureBucketExists(String bucket) {
        // no-op for adapter-backed external storage
    }

    @Override
    public String putObject(String bucket, String objectKey, byte[] content, String mimeType) {
        String baseUrl = requireBaseUrl();
        HttpHeaders headers = authHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        ByteArrayResource fileResource = new ByteArrayResource(content) {
            @Override
            public String getFilename() {
                return "document.bin";
            }
        };

        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("file", fileResource);
        form.add("subjectType", "OTHER");
        form.add("subjectId", objectKey);
        form.add("documentTypeCode", "GENERAL_DOCUMENT");
        form.add("source", "uploaded");
        form.add("confidentiality", "NORMAL");

        ResponseEntity<JsonNode> response = restTemplate.postForEntity(
                baseUrl + "/v1/internal/documents",
                new HttpEntity<>(form, headers),
                JsonNode.class);
        JsonNode body = response.getBody();
        String documentId = first(body, "data", "documentId");
        if (documentId == null || documentId.isBlank()) {
            documentId = first(body, "documentId");
        }
        if (documentId == null || documentId.isBlank()) {
            throw new IllegalStateException("Landela adapter upload did not return documentId");
        }
        return documentId;
    }

    @Override
    public InputStream getObject(String bucket, String objectKey) {
        String baseUrl = requireBaseUrl();
        ResponseEntity<byte[]> response = restTemplate.exchange(
                baseUrl + "/v1/internal/documents/" + objectKey + "/download",
                HttpMethod.GET,
                new HttpEntity<>(authHeaders()),
                byte[].class);
        byte[] body = response.getBody();
        if (body == null) {
            throw new IllegalStateException("Landela adapter returned empty binary payload");
        }
        return new ByteArrayInputStream(body);
    }

    @Override
    public String generateSignedUrl(String bucket, String objectKey, int ttlSeconds) {
        String baseUrl = requireBaseUrl();
        ResponseEntity<JsonNode> response = restTemplate.exchange(
                baseUrl + "/v1/internal/documents/" + objectKey + "/signed-url",
                HttpMethod.GET,
                new HttpEntity<>(authHeaders()),
                JsonNode.class);
        JsonNode body = response.getBody();
        String url = first(body, "data", "url");
        if (url == null || url.isBlank()) {
            url = first(body, "url");
        }
        if (url == null || url.isBlank()) {
            throw new IllegalStateException("Landela adapter did not return signed URL");
        }
        return url;
    }

    @Override
    public void removeObject(String bucket, String objectKey) {
        String baseUrl = requireBaseUrl();
        HttpHeaders headers = authHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        Map<String, Object> request = Map.of("action", "DELETE");
        restTemplate.exchange(
                baseUrl + "/v1/internal/documents/" + objectKey + "/lifecycle",
                HttpMethod.POST,
                new HttpEntity<>(request, headers),
                JsonNode.class);
    }

    private HttpHeaders authHeaders() {
        HttpHeaders headers = new HttpHeaders();
        String apiKey = properties.getLandelaAdapter().getApiKey();
        if (apiKey != null && !apiKey.isBlank()) {
            headers.set("X-API-Key", apiKey);
        }
        return headers;
    }

    private String requireBaseUrl() {
        String baseUrl = properties.getLandelaAdapter().getBaseUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalStateException("document-store.landela-adapter.base-url is required");
        }
        return baseUrl;
    }

    private String first(JsonNode node, String... keys) {
        JsonNode current = node;
        if (current == null) {
            return null;
        }
        for (String key : keys) {
            if (current == null) {
                return null;
            }
            current = current.path(key);
        }
        if (current == null || current.isMissingNode() || current.isNull()) {
            return null;
        }
        String value = current.asText();
        return value == null || value.isBlank() ? null : value;
    }
}
