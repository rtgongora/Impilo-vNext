package zw.gov.mohcc.impilo.experience.client;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;

import java.util.Map;
import java.util.UUID;

/**
 * HTTP client for the Tshepo Identity sovereign service (CPID, MOSIP links,
 * reconciliation, resolution, scoped tokens).
 */
@Component
public class TshepoIdentityServiceClient {

    private static final Logger log = LoggerFactory.getLogger(TshepoIdentityServiceClient.class);

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public TshepoIdentityServiceClient(RestTemplate serviceRestTemplate,
                                       ServiceClientConfig.ServiceEndpoints endpoints) {
        this.restTemplate = serviceRestTemplate;
        this.baseUrl = endpoints.tshepoIdentityBaseUrl();
    }

    public JsonNode generateCpid(Map<String, Object> request) {
        String url = baseUrl + "/v1/identity/cpid/generate";
        log.info("TSHEPO-IDENTITY: generateCpid operation");
        ResponseEntity<JsonNode> response =
                restTemplate.postForEntity(url, new HttpEntity<>(request), JsonNode.class);
        return extractData(response);
    }

    public JsonNode generateProvisionalCpid(Map<String, Object> request) {
        String url = baseUrl + "/v1/identity/cpid/provisional";
        log.info("TSHEPO-IDENTITY: generateProvisionalCpid operation");
        ResponseEntity<JsonNode> response =
                restTemplate.postForEntity(url, new HttpEntity<>(request), JsonNode.class);
        return extractData(response);
    }

    public JsonNode storeMosipLink(Map<String, Object> request) {
        String url = baseUrl + "/v1/identity/mosip/link";
        log.info("TSHEPO-IDENTITY: storeMosipLink operation");
        ResponseEntity<JsonNode> response =
                restTemplate.postForEntity(url, new HttpEntity<>(request), JsonNode.class);
        return extractData(response);
    }

    public JsonNode verifyMosipLink(Map<String, Object> request) {
        String url = baseUrl + "/v1/identity/mosip/verify";
        log.info("TSHEPO-IDENTITY: verifyMosipLink operation");
        ResponseEntity<JsonNode> response =
                restTemplate.postForEntity(url, new HttpEntity<>(request), JsonNode.class);
        return extractData(response);
    }

    public JsonNode reconcileProvisionalCpid(Map<String, Object> request) {
        String url = baseUrl + "/v1/identity/reconcile";
        log.info("TSHEPO-IDENTITY: reconcileProvisionalCpid operation");
        ResponseEntity<JsonNode> response =
                restTemplate.postForEntity(url, new HttpEntity<>(request), JsonNode.class);
        return extractData(response);
    }

    public JsonNode listUnreconciledProvisionalCpids() {
        String url = baseUrl + "/v1/identity/provisional";
        log.info("TSHEPO-IDENTITY: listUnreconciledProvisionalCpids operation");
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    public JsonNode resolveIdentity(Map<String, Object> request) {
        String url = baseUrl + "/v1/identity/resolve";
        log.info("TSHEPO-IDENTITY: resolveIdentity operation");
        ResponseEntity<JsonNode> response =
                restTemplate.postForEntity(url, new HttpEntity<>(request), JsonNode.class);
        return extractData(response);
    }

    public JsonNode getIdentityMappingByHealthId(UUID healthId) {
        String url = baseUrl + "/v1/identity/mapping/" + healthId;
        log.info("TSHEPO-IDENTITY: getIdentityMappingByHealthId operation [healthId={}]", healthId);
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    public JsonNode createIdentityMapping(Map<String, Object> request) {
        String url = baseUrl + "/v1/identity/mapping";
        log.info("TSHEPO-IDENTITY: createIdentityMapping operation");
        ResponseEntity<JsonNode> response =
                restTemplate.postForEntity(url, new HttpEntity<>(request), JsonNode.class);
        return extractData(response);
    }

    public JsonNode issueScopedToken(Map<String, Object> request) {
        String url = baseUrl + "/v1/identity/tokens";
        log.info("TSHEPO-IDENTITY: issueScopedToken operation");
        ResponseEntity<JsonNode> response =
                restTemplate.postForEntity(url, new HttpEntity<>(request), JsonNode.class);
        return extractData(response);
    }

    public JsonNode introspectScopedToken(Map<String, Object> request) {
        String url = baseUrl + "/v1/identity/tokens/introspect";
        log.info("TSHEPO-IDENTITY: introspectScopedToken operation");
        ResponseEntity<JsonNode> response =
                restTemplate.postForEntity(url, new HttpEntity<>(request), JsonNode.class);
        return extractData(response);
    }

    public JsonNode revokeScopedToken(String jti) {
        String url = baseUrl + "/v1/identity/tokens/" + jti;
        log.info("TSHEPO-IDENTITY: revokeScopedToken operation [jti={}]", jti);
        ResponseEntity<JsonNode> response =
                restTemplate.exchange(url, HttpMethod.DELETE, null, JsonNode.class);
        return extractData(response);
    }

    private JsonNode extractData(ResponseEntity<JsonNode> response) {
        if (response.getBody() != null && response.getBody().has("data")) {
            return response.getBody().get("data");
        }
        return response.getBody();
    }
}
