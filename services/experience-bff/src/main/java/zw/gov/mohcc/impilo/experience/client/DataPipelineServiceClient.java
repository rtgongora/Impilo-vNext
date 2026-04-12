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
 * HTTP client for the Data Pipeline sovereign service.
 */
@Component
public class DataPipelineServiceClient {

    private static final Logger log = LoggerFactory.getLogger(DataPipelineServiceClient.class);

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public DataPipelineServiceClient(RestTemplate serviceRestTemplate,
                                     ServiceClientConfig.ServiceEndpoints endpoints) {
        this.restTemplate = serviceRestTemplate;
        this.baseUrl = endpoints.dataPipelineBaseUrl();
    }

    public JsonNode ingest(Map<String, Object> envelope) {
        String url = baseUrl + "/internal/v1/ingest";
        log.info("Pipeline: ingest [eventId={}, eventType={}]",
                envelope.get("eventId"), envelope.get("eventType"));
        return extractData(restTemplate.postForEntity(url, envelope, JsonNode.class));
    }

    public JsonNode listSources() {
        String url = baseUrl + "/internal/v1/sources";
        log.info("Pipeline: list sources");
        return extractData(restTemplate.getForEntity(url, JsonNode.class));
    }

    public JsonNode createSource(Map<String, Object> request) {
        String url = baseUrl + "/internal/v1/sources";
        log.info("Pipeline: create source [sourceId={}]", request.get("sourceId"));
        return extractData(restTemplate.postForEntity(url, request, JsonNode.class));
    }

    public JsonNode listWatermarks() {
        String url = baseUrl + "/internal/v1/watermarks";
        log.info("Pipeline: list watermarks");
        return extractData(restTemplate.getForEntity(url, JsonNode.class));
    }

    private JsonNode extractData(ResponseEntity<JsonNode> response) {
        if (response.getBody() != null && response.getBody().has("data")) {
            return response.getBody().get("data");
        }
        return response.getBody();
    }
}
