package zw.gov.mohcc.impilo.experience.client;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;

import java.util.Map;
import java.util.UUID;

/**
 * HTTP client for the Tshepo Keys sovereign service (JWKS, signing keys,
 * certificates, payload signing).
 */
@Component
public class TshepoKeysServiceClient {

    private static final Logger log = LoggerFactory.getLogger(TshepoKeysServiceClient.class);

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public TshepoKeysServiceClient(RestTemplate serviceRestTemplate,
                                   ServiceClientConfig.ServiceEndpoints endpoints) {
        this.restTemplate = serviceRestTemplate;
        this.baseUrl = endpoints.tshepoKeysBaseUrl();
    }

    public JsonNode importCertificate(Map<String, Object> request) {
        String url = baseUrl + "/v1/certificates";
        log.info("TSHEPO-KEYS: importCertificate operation");
        ResponseEntity<JsonNode> response =
                restTemplate.postForEntity(url, new HttpEntity<>(request), JsonNode.class);
        return extractData(response);
    }

    public JsonNode listCertificates(UUID tenantId) {
        String url = UriComponentsBuilder.fromHttpUrl(baseUrl + "/v1/certificates")
                .queryParam("tenantId", tenantId)
                .build()
                .encode()
                .toUriString();
        log.info("TSHEPO-KEYS: listCertificates operation [tenantId={}]", tenantId);
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    public JsonNode revokeCertificate(long id, UUID tenantId) {
        String url = UriComponentsBuilder.fromHttpUrl(baseUrl + "/v1/certificates/" + id)
                .queryParam("tenantId", tenantId)
                .build()
                .encode()
                .toUriString();
        log.info("TSHEPO-KEYS: revokeCertificate operation [id={}, tenantId={}]", id, tenantId);
        ResponseEntity<JsonNode> response =
                restTemplate.exchange(url, HttpMethod.DELETE, null, JsonNode.class);
        return extractData(response);
    }

    public JsonNode getJwks() {
        String url = baseUrl + "/v1/keys/jwks";
        log.info("TSHEPO-KEYS: getJwks operation");
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    public JsonNode rotateSigningKey(Map<String, Object> request) {
        String url = baseUrl + "/v1/keys/rotate";
        log.info("TSHEPO-KEYS: rotateSigningKey operation");
        ResponseEntity<JsonNode> response =
                restTemplate.postForEntity(url, new HttpEntity<>(request), JsonNode.class);
        return extractData(response);
    }

    public JsonNode listSigningKeys(UUID tenantId) {
        String url = UriComponentsBuilder.fromHttpUrl(baseUrl + "/v1/keys")
                .queryParam("tenantId", tenantId)
                .build()
                .encode()
                .toUriString();
        log.info("TSHEPO-KEYS: listSigningKeys operation [tenantId={}]", tenantId);
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    public JsonNode getSigningKey(String keyId) {
        String url = baseUrl + "/v1/keys/" + keyId;
        log.info("TSHEPO-KEYS: getSigningKey operation [keyId={}]", keyId);
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    public JsonNode revokeSigningKey(String keyId) {
        String url = baseUrl + "/v1/keys/" + keyId;
        log.info("TSHEPO-KEYS: revokeSigningKey operation [keyId={}]", keyId);
        ResponseEntity<JsonNode> response =
                restTemplate.exchange(url, HttpMethod.DELETE, null, JsonNode.class);
        return extractData(response);
    }

    public JsonNode signPayload(Map<String, Object> request) {
        String url = baseUrl + "/v1/sign";
        log.info("TSHEPO-KEYS: signPayload operation");
        ResponseEntity<JsonNode> response =
                restTemplate.postForEntity(url, new HttpEntity<>(request), JsonNode.class);
        return extractData(response);
    }

    public JsonNode issueSigningServiceToken(Map<String, Object> request) {
        String url = baseUrl + "/v1/sign/token";
        log.info("TSHEPO-KEYS: issueSigningServiceToken operation");
        ResponseEntity<JsonNode> response =
                restTemplate.postForEntity(url, new HttpEntity<>(request), JsonNode.class);
        return extractData(response);
    }

    private JsonNode extractData(ResponseEntity<JsonNode> response) {
        if (response.getBody() != null && response.getBody().has("data")) {
            return response.getBody().get("data");
        }
        return response.getBody();
    }
}
