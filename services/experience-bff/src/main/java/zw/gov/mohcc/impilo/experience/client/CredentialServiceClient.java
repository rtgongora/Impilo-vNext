package zw.gov.mohcc.impilo.experience.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Component
public class CredentialServiceClient {

    private static final Logger log = LoggerFactory.getLogger(CredentialServiceClient.class);

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public CredentialServiceClient(
            RestTemplate serviceRestTemplate,
            ServiceClientConfig.ServiceEndpoints endpoints
    ) {
        this.restTemplate = serviceRestTemplate;
        this.baseUrl = endpoints.credentialBaseUrl();
    }

    public ResponseEntity<String> searchCredentials(MultiValueMap<String, String> queryParams) {
        String url = UriComponentsBuilder.fromHttpUrl(baseUrl + "/v1/internal/credentials")
                .queryParams(copy(queryParams))
                .toUriString();
        log.info("Credential service: listing credentials");
        return restTemplate.getForEntity(url, String.class);
    }

    public ResponseEntity<byte[]> getCredentialPdf(String credentialId) {
        log.info("Credential service: downloading credential PDF={}", credentialId);
        return restTemplate.getForEntity(baseUrl + "/v1/internal/credentials/" + credentialId + "/pdf", byte[].class);
    }

    /**
     * Public verification by opaque token — maps to credential-verification-service
     * {@code GET /v1/public/verify/{token}} (returns {@code ApiResponse<VerificationResult>}).
     */
    public JsonNode publicVerifyByToken(String token) {
        String encoded = URLEncoder.encode(token, StandardCharsets.UTF_8);
        String url = baseUrl + "/v1/public/verify/" + encoded;
        log.info("Credential service: public verify (token length={})", token != null ? token.length() : 0);
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        JsonNode root = response.getBody();
        if (root != null && root.has("data")) {
            return root.get("data");
        }
        return root;
    }

    private MultiValueMap<String, String> copy(MultiValueMap<String, String> source) {
        return source == null ? new LinkedMultiValueMap<>() : new LinkedMultiValueMap<>(source);
    }
}
