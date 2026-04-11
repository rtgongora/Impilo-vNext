package zw.gov.mohcc.impilo.experience.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class CredentialServiceClient {

    private static final Logger log = LoggerFactory.getLogger(CredentialServiceClient.class);

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public CredentialServiceClient(
            RestTemplate serviceRestTemplate,
            @Value("${impilo.services.credential-base-url:http://localhost:8094}") String credentialBaseUrl
    ) {
        this.restTemplate = serviceRestTemplate;
        this.baseUrl = credentialBaseUrl;
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

    private MultiValueMap<String, String> copy(MultiValueMap<String, String> source) {
        return source == null ? new LinkedMultiValueMap<>() : new LinkedMultiValueMap<>(source);
    }
}
