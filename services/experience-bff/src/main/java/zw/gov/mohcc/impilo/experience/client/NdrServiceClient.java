package zw.gov.mohcc.impilo.experience.client;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;

import java.util.Map;

/**
 * HTTP client for the National Data Repository (canonical datasets & SQL-ish query API).
 */
@Component
public class NdrServiceClient {

    private static final Logger log = LoggerFactory.getLogger(NdrServiceClient.class);

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public NdrServiceClient(RestTemplate serviceRestTemplate,
                            ServiceClientConfig.ServiceEndpoints endpoints) {
        this.restTemplate = serviceRestTemplate;
        this.baseUrl = endpoints.ndrNationalBaseUrl();
    }

    public JsonNode listDatasets() {
        String url = baseUrl + "/internal/v1/datasets";
        log.info("NDR national: list datasets");
        return extractData(restTemplate.getForEntity(url, JsonNode.class));
    }

    public JsonNode createDataset(Map<String, Object> request) {
        String url = baseUrl + "/internal/v1/datasets";
        log.info("NDR national: create dataset [key={}]", request.get("datasetKey"));
        return extractData(restTemplate.postForEntity(url, request, JsonNode.class));
    }

    public JsonNode createDatasetVersion(String datasetKey, Map<String, Object> request) {
        String url = baseUrl + "/internal/v1/datasets/" + datasetKey + "/versions";
        log.info("NDR national: create version [key={}]", datasetKey);
        return extractData(restTemplate.postForEntity(url, request, JsonNode.class));
    }

    public JsonNode executeQuery(Map<String, Object> request) {
        String url = baseUrl + "/internal/v1/query";
        log.info("NDR national: query [datasetKey={}]", request.get("datasetKey"));
        return extractData(restTemplate.postForEntity(url, request, JsonNode.class));
    }

    private JsonNode extractData(ResponseEntity<JsonNode> response) {
        if (response.getBody() != null && response.getBody().has("data")) {
            return response.getBody().get("data");
        }
        return response.getBody();
    }
}
