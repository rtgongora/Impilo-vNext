package zw.gov.mohcc.impilo.docstore.core.signature;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.docstore.config.DocumentStoreProperties;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * HTTP-backed signature adapter stub for external e-sign workflows.
 */
@Component
public class ExternalStubSignatureProvider implements SignatureProvider {

    private final DocumentStoreProperties properties;
    private final RestTemplate restTemplate;

    public ExternalStubSignatureProvider(DocumentStoreProperties properties) {
        this.properties = properties;
        this.restTemplate = new RestTemplate();
    }

    @Override
    public String providerType() {
        return "EXTERNAL_STUB";
    }

    @Override
    public SignatureResult sign(SignatureRequest request) {
        String baseUrl = properties.getSignatureStub().getBaseUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalStateException("document-store.signature-stub.base-url is required");
        }
        String path = properties.getSignatureStub().getPath();
        if (path == null || path.isBlank()) {
            path = "/v1/signature/sign";
        }
        String url = baseUrl + path;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String apiKey = properties.getSignatureStub().getApiKey();
        if (apiKey != null && !apiKey.isBlank()) {
            headers.set("X-API-Key", apiKey);
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("objectId", request.objectId().toString());
        body.put("signerId", request.signerId());
        body.put("requestedBy", request.requestedBy());

        ResponseEntity<Map> response = restTemplate.postForEntity(url, new HttpEntity<>(body, headers), Map.class);
        Map payload = response.getBody();
        if (payload == null) {
            throw new IllegalStateException("External signature stub returned empty response");
        }
        String signatureRef = value(payload.get("signatureRef"));
        if (signatureRef == null || signatureRef.isBlank()) {
            throw new IllegalStateException("External signature stub did not return signatureRef");
        }
        String verificationUrl = value(payload.get("verificationUrl"));
        String signedAtRaw = value(payload.get("signedAt"));
        OffsetDateTime signedAt = signedAtRaw == null || signedAtRaw.isBlank()
                ? OffsetDateTime.now()
                : OffsetDateTime.parse(signedAtRaw);
        return new SignatureResult(signatureRef, verificationUrl, signedAt);
    }

    private String value(Object raw) {
        return raw == null ? null : raw.toString();
    }
}
