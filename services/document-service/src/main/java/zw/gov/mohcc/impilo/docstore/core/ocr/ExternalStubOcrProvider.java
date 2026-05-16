package zw.gov.mohcc.impilo.docstore.core.ocr;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.docstore.config.DocumentStoreProperties;

import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * HTTP-backed OCR adapter stub for integration with external OCR engines.
 */
@Component
public class ExternalStubOcrProvider implements OcrProvider {

    private final DocumentStoreProperties properties;
    private final RestTemplate restTemplate;

    public ExternalStubOcrProvider(DocumentStoreProperties properties) {
        this.properties = properties;
        this.restTemplate = new RestTemplate();
    }

    @Override
    public String providerType() {
        return "EXTERNAL_STUB";
    }

    @Override
    public OcrResult extract(UUID objectId, byte[] content, String mimeType) {
        String baseUrl = properties.getOcrStub().getBaseUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalStateException("document-store.ocr-stub.base-url is required");
        }
        String path = properties.getOcrStub().getPath();
        if (path == null || path.isBlank()) {
            path = "/v1/ocr/extract";
        }
        String url = baseUrl + path;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String apiKey = properties.getOcrStub().getApiKey();
        if (apiKey != null && !apiKey.isBlank()) {
            headers.set("X-API-Key", apiKey);
        }

        Map<String, Object> request = new LinkedHashMap<>();
        request.put("objectId", objectId.toString());
        request.put("mimeType", mimeType);
        request.put("contentBase64", Base64.getEncoder().encodeToString(content));

        ResponseEntity<Map> response = restTemplate.postForEntity(url, new HttpEntity<>(request, headers), Map.class);
        Map body = response.getBody();
        if (body == null) {
            throw new IllegalStateException("External OCR stub returned empty response");
        }
        Object text = body.get("text");
        Object confidence = body.get("confidence");
        String extracted = text == null ? "" : text.toString();
        double score = 0.0d;
        if (confidence instanceof Number n) {
            score = n.doubleValue();
        } else if (confidence != null) {
            score = Double.parseDouble(confidence.toString());
        }
        return new OcrResult(extracted, score);
    }
}
