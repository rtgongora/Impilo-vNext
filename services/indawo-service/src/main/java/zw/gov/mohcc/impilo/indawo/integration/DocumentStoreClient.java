package zw.gov.mohcc.impilo.indawo.integration;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.companion.context.RequestContext;
import zw.gov.mohcc.impilo.companion.context.RequestContextHolder;

import java.util.Map;
import java.util.UUID;

/**
 * Minimal client to document-service for uploads and signed URL retrieval.
 *
 * Stores objects in document-service (MinIO-backed) and returns the objectId.
 */
@Component
public class DocumentStoreClient {

    private static final Logger log = LoggerFactory.getLogger(DocumentStoreClient.class);

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public DocumentStoreClient(RestTemplate restTemplate,
                               @Value("${impilo.services.document-store.base-url:http://localhost:8070}") String baseUrl) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl;
    }

    public UUID upload(MultipartFile file, String mimeType, Map<String, String> metadata) {
        RequestContext ctx = RequestContextHolder.require();
        String url = baseUrl + "/v1/internal/objects";

        try {
            ByteArrayResource resource = new ByteArrayResource(file.getBytes()) {
                @Override
                public String getFilename() {
                    return file.getOriginalFilename() != null ? file.getOriginalFilename() : "upload.bin";
                }
            };

            MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
            parts.add("file", resource);
            parts.add("mimeType", mimeType);
            parts.add("originalFilename", file.getOriginalFilename());
            if (metadata != null && !metadata.isEmpty()) {
                parts.add("metadata", metadata);
            }

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);
            // forward trust headers + auth token if present (service-to-service via gateway)
            headers.set(CompanionHeaders.TENANT_ID, ctx.tenantId());
            headers.set(CompanionHeaders.POD_ID, ctx.podId());
            headers.set(CompanionHeaders.REQUEST_ID, ctx.requestId());
            headers.set(CompanionHeaders.CORRELATION_ID, ctx.correlationId());
            if (ctx.authToken() != null && !ctx.authToken().isBlank()) {
                headers.set(CompanionHeaders.AUTHORIZATION, ctx.authToken());
            }

            ResponseEntity<JsonNode> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    new HttpEntity<>(parts, headers),
                    JsonNode.class
            );

            JsonNode body = response.getBody();
            if (body != null && body.has("data") && body.get("data").has("objectId")) {
                return UUID.fromString(body.get("data").get("objectId").asText());
            }
            throw new IllegalStateException("Document store upload did not return objectId");
        } catch (Exception e) {
            log.warn("Document store upload failed: {}", e.getMessage());
            throw new IllegalStateException("Document upload failed: " + e.getMessage(), e);
        }
    }

    public JsonNode signedUrl(UUID objectId) {
        RequestContext ctx = RequestContextHolder.require();
        String url = baseUrl + "/v1/internal/objects/" + objectId + "/signed-url";

        HttpHeaders headers = new HttpHeaders();
        headers.set(CompanionHeaders.TENANT_ID, ctx.tenantId());
        headers.set(CompanionHeaders.POD_ID, ctx.podId());
        headers.set(CompanionHeaders.REQUEST_ID, ctx.requestId());
        headers.set(CompanionHeaders.CORRELATION_ID, ctx.correlationId());
        if (ctx.authToken() != null && !ctx.authToken().isBlank()) {
            headers.set(CompanionHeaders.AUTHORIZATION, ctx.authToken());
        }

        ResponseEntity<JsonNode> response = restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), JsonNode.class);
        JsonNode body = response.getBody();
        return body != null && body.has("data") ? body.get("data") : body;
    }
}

