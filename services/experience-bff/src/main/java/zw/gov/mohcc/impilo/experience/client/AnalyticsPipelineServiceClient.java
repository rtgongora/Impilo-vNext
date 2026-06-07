package zw.gov.mohcc.impilo.experience.client;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;

import java.util.Map;

/**
 * HTTP client for analytics-pipeline-service telemedicine lifecycle analytics.
 */
@Component
public class AnalyticsPipelineServiceClient {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsPipelineServiceClient.class);

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public AnalyticsPipelineServiceClient(RestTemplate serviceRestTemplate,
                                          ServiceClientConfig.ServiceEndpoints endpoints) {
        this.restTemplate = serviceRestTemplate;
        this.baseUrl = endpoints.analyticsPipelineBaseUrl();
    }

    public JsonNode getTelemedicineSlaAggregates(String facilityId, String from, String to) {
        UriComponentsBuilder builder = UriComponentsBuilder
                .fromHttpUrl(baseUrl + "/internal/v1/telemedicine/sla");
        if (facilityId != null && !facilityId.isBlank()) {
            builder.queryParam("facility_id", facilityId);
        }
        if (from != null && !from.isBlank()) {
            builder.queryParam("from", from);
        }
        if (to != null && !to.isBlank()) {
            builder.queryParam("to", to);
        }
        String url = builder.encode().toUriString();
        log.info("ANALYTICS-PIPELINE: telemedicine SLA aggregates");
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return response.getBody();
    }

    public JsonNode ingestTelemedicineEvent(Map<String, Object> event) {
        String url = baseUrl + "/internal/v1/telemedicine/events";
        log.info("ANALYTICS-PIPELINE: ingest telemedicine event type={}", event.get("eventType"));
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, event, JsonNode.class);
        return response.getBody();
    }
}
