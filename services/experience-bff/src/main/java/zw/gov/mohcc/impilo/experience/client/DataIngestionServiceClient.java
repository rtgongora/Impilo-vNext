package zw.gov.mohcc.impilo.experience.client;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;

/**
 * HTTP client for the Data Ingestion sovereign service.
 */
@Component
public class DataIngestionServiceClient {

    private static final Logger log = LoggerFactory.getLogger(DataIngestionServiceClient.class);

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public DataIngestionServiceClient(RestTemplate serviceRestTemplate,
                                    ServiceClientConfig.ServiceEndpoints endpoints) {
        this.restTemplate = serviceRestTemplate;
        this.baseUrl = endpoints.dataIngestionBaseUrl();
    }

    public JsonNode ingestEvent(String rawJson) {
        String url = baseUrl + "/internal/v1/ingest/events";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        log.info("Data ingestion: single event");
        return extractData(restTemplate.postForEntity(url, new HttpEntity<>(rawJson, headers), JsonNode.class));
    }

    public JsonNode ingestBatch(String rawJson) {
        String url = baseUrl + "/internal/v1/ingest/batch";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        log.info("Data ingestion: batch");
        return extractData(restTemplate.postForEntity(url, new HttpEntity<>(rawJson, headers), JsonNode.class));
    }

    public JsonNode ingestionStatus() {
        String url = baseUrl + "/internal/v1/ingestion/status";
        log.info("Data ingestion: status");
        return extractData(restTemplate.getForEntity(url, JsonNode.class));
    }

    public JsonNode health() {
        String url = baseUrl + "/internal/v1/ingest/health";
        log.info("Data ingestion: health");
        return extractData(restTemplate.getForEntity(url, JsonNode.class));
    }

    public JsonNode listBronzeEvents(int cursor, int limit) {
        String url = baseUrl + "/internal/v1/bronze/events?cursor=" + cursor + "&limit=" + limit;
        log.info("Data ingestion: bronze snapshot [cursor={}, limit={}]", cursor, limit);
        return extractData(restTemplate.getForEntity(url, JsonNode.class));
    }

    private JsonNode extractData(ResponseEntity<JsonNode> response) {
        if (response.getBody() != null && response.getBody().has("data")) {
            return response.getBody().get("data");
        }
        return response.getBody();
    }
}
