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
 * HTTP client for the Tshepo Offline sovereign service (capability tokens,
 * offline actions, packs, reconciliation batches).
 */
@Component
public class TshepoOfflineServiceClient {

    private static final Logger log = LoggerFactory.getLogger(TshepoOfflineServiceClient.class);

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public TshepoOfflineServiceClient(RestTemplate serviceRestTemplate,
                                      ServiceClientConfig.ServiceEndpoints endpoints) {
        this.restTemplate = serviceRestTemplate;
        this.baseUrl = endpoints.tshepoOfflineBaseUrl();
    }

    public JsonNode issueCapabilityToken(Map<String, Object> request) {
        String url = baseUrl + "/v1/offline/capabilities";
        log.info("TSHEPO-OFFLINE: issueCapabilityToken operation");
        ResponseEntity<JsonNode> response =
                restTemplate.postForEntity(url, new HttpEntity<>(request), JsonNode.class);
        return extractData(response);
    }

    public JsonNode getCapabilityToken(UUID id) {
        String url = baseUrl + "/v1/offline/capabilities/" + id;
        log.info("TSHEPO-OFFLINE: getCapabilityToken operation [id={}]", id);
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    public JsonNode revokeCapabilityToken(UUID id) {
        String url = baseUrl + "/v1/offline/capabilities/" + id;
        log.info("TSHEPO-OFFLINE: revokeCapabilityToken operation [id={}]", id);
        ResponseEntity<JsonNode> response =
                restTemplate.exchange(url, HttpMethod.DELETE, null, JsonNode.class);
        return extractData(response);
    }

    public JsonNode verifyCapabilityToken(Map<String, Object> request) {
        String url = baseUrl + "/v1/offline/capabilities/verify";
        log.info("TSHEPO-OFFLINE: verifyCapabilityToken operation");
        ResponseEntity<JsonNode> response =
                restTemplate.postForEntity(url, new HttpEntity<>(request), JsonNode.class);
        return extractData(response);
    }

    public JsonNode logOfflineAction(Map<String, Object> request) {
        String url = baseUrl + "/v1/offline/actions";
        log.info("TSHEPO-OFFLINE: logOfflineAction operation");
        ResponseEntity<JsonNode> response =
                restTemplate.postForEntity(url, new HttpEntity<>(request), JsonNode.class);
        return extractData(response);
    }

    public JsonNode queryOfflineActions(int page, int size) {
        String url = UriComponentsBuilder.fromHttpUrl(baseUrl + "/v1/offline/actions")
                .queryParam("page", page)
                .queryParam("size", size)
                .build()
                .encode()
                .toUriString();
        log.info("TSHEPO-OFFLINE: queryOfflineActions operation [page={}, size={}]", page, size);
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    public JsonNode generateOfflinePack(Map<String, Object> request) {
        String url = baseUrl + "/v1/offline/packs";
        log.info("TSHEPO-OFFLINE: generateOfflinePack operation");
        ResponseEntity<JsonNode> response =
                restTemplate.postForEntity(url, new HttpEntity<>(request), JsonNode.class);
        return extractData(response);
    }

    public JsonNode getOfflinePack(UUID id) {
        String url = baseUrl + "/v1/offline/packs/" + id;
        log.info("TSHEPO-OFFLINE: getOfflinePack operation [id={}]", id);
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    public JsonNode getLatestOfflinePackForFacility(UUID facilityId) {
        String url = baseUrl + "/v1/offline/packs/facility/" + facilityId;
        log.info("TSHEPO-OFFLINE: getLatestOfflinePackForFacility operation [facilityId={}]", facilityId);
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    public JsonNode submitOfflineReconcileBatch(Map<String, Object> request) {
        String url = baseUrl + "/v1/offline/reconcile";
        log.info("TSHEPO-OFFLINE: submitOfflineReconcileBatch operation");
        ResponseEntity<JsonNode> response =
                restTemplate.postForEntity(url, new HttpEntity<>(request), JsonNode.class);
        return extractData(response);
    }

    public JsonNode getOfflineReconcileBatchStatus(UUID batchId) {
        String url = baseUrl + "/v1/offline/reconcile/" + batchId;
        log.info("TSHEPO-OFFLINE: getOfflineReconcileBatchStatus operation [batchId={}]", batchId);
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    public JsonNode listPendingOfflineReconcileBatches() {
        String url = baseUrl + "/v1/offline/reconcile/pending";
        log.info("TSHEPO-OFFLINE: listPendingOfflineReconcileBatches operation");
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
