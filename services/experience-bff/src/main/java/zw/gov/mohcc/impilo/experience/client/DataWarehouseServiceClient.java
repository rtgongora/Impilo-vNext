package zw.gov.mohcc.impilo.experience.client;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * HTTP client for the Data Warehouse (gold materialize & query).
 */
@Component
public class DataWarehouseServiceClient {

    private static final Logger log = LoggerFactory.getLogger(DataWarehouseServiceClient.class);

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public DataWarehouseServiceClient(RestTemplate serviceRestTemplate,
                                      ServiceClientConfig.ServiceEndpoints endpoints) {
        this.restTemplate = serviceRestTemplate;
        this.baseUrl = endpoints.dataWarehouseBaseUrl();
    }

    public JsonNode materialize(Map<String, Object> request) {
        String url = baseUrl + "/internal/v1/gold/materialize";
        log.info("Warehouse: materialize [eventId={}]", request.get("eventId"));
        return extractData(restTemplate.postForEntity(url, request, JsonNode.class));
    }

    public JsonNode queryGold(String dataset, int page, int limit) {
        String enc = URLEncoder.encode(dataset, StandardCharsets.UTF_8);
        String url = baseUrl + "/internal/v1/gold/query?dataset=" + enc
                + "&page=" + page + "&limit=" + limit;
        log.info("Warehouse: gold query [dataset={}, page={}, limit={}]", dataset, page, limit);
        return extractData(restTemplate.getForEntity(url, JsonNode.class));
    }

    public JsonNode goldStats() {
        String url = baseUrl + "/internal/v1/gold/stats";
        log.info("Warehouse: gold stats");
        return extractData(restTemplate.getForEntity(url, JsonNode.class));
    }

    public JsonNode listGoldDatasets() {
        String url = baseUrl + "/external/v1/gold/datasets";
        log.info("Warehouse: list gold datasets (external)");
        return extractData(restTemplate.getForEntity(url, JsonNode.class));
    }

    private JsonNode extractData(ResponseEntity<JsonNode> response) {
        if (response.getBody() != null && response.getBody().has("data")) {
            return response.getBody().get("data");
        }
        return response.getBody();
    }
}
