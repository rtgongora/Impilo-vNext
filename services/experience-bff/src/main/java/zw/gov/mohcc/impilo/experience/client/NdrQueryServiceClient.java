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
 * HTTP client for the NDR ring service (bronze ingest, bronze/gold query, gold build).
 */
@Component
public class NdrQueryServiceClient {

    private static final Logger log = LoggerFactory.getLogger(NdrQueryServiceClient.class);

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public NdrQueryServiceClient(RestTemplate serviceRestTemplate,
                                 ServiceClientConfig.ServiceEndpoints endpoints) {
        this.restTemplate = serviceRestTemplate;
        this.baseUrl = endpoints.ndrQueryBaseUrl();
    }

    public JsonNode ingestEvent(String rawJson) {
        String url = baseUrl + "/internal/v1/ndr/ingest/events";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        log.info("NDR query: ingest event (raw JSON)");
        return extractData(restTemplate.postForEntity(url, new HttpEntity<>(rawJson, headers), JsonNode.class));
    }

    public JsonNode queryBronze(String eventType, String subjectId, int cursor, int limit) {
        StringBuilder url = new StringBuilder(baseUrl).append("/internal/v1/ndr/query/bronze?cursor=")
                .append(cursor).append("&limit=").append(limit);
        if (eventType != null && !eventType.isBlank()) {
            url.append("&event_type=").append(eventType);
        }
        if (subjectId != null && !subjectId.isBlank()) {
            url.append("&subject_id=").append(subjectId);
        }
        log.info("NDR query: bronze [eventType={}, cursor={}, limit={}]", eventType, cursor, limit);
        return extractData(restTemplate.getForEntity(url.toString(), JsonNode.class));
    }

    public JsonNode buildGoldEncounters() {
        String url = baseUrl + "/internal/v1/ndr/build/gold/encounters";
        log.info("NDR query: build gold encounters");
        return extractData(restTemplate.postForEntity(url, null, JsonNode.class));
    }

    public JsonNode queryGoldEncounters(String patientId, int cursor, int limit) {
        StringBuilder url = new StringBuilder(baseUrl).append("/internal/v1/ndr/query/gold/encounters?cursor=")
                .append(cursor).append("&limit=").append(limit);
        if (patientId != null && !patientId.isBlank()) {
            url.append("&patient_id=").append(patientId);
        }
        log.info("NDR query: gold encounters [patientId={}, cursor={}, limit={}]", patientId, cursor, limit);
        return extractData(restTemplate.getForEntity(url.toString(), JsonNode.class));
    }

    private JsonNode extractData(ResponseEntity<JsonNode> response) {
        if (response.getBody() != null && response.getBody().has("data")) {
            return response.getBody().get("data");
        }
        return response.getBody();
    }
}
