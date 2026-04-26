package zw.gov.mohcc.impilo.experience.client;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;

/**
 * HTTP client for {@code scheduling-service} MVP slot surface ({@code GET /v1/slots}, holds).
 */
@Component
public class SchedulingServiceClient {

    private static final Logger log = LoggerFactory.getLogger(SchedulingServiceClient.class);

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public SchedulingServiceClient(RestTemplate serviceRestTemplate,
                                   ServiceClientConfig.ServiceEndpoints endpoints) {
        this.restTemplate = serviceRestTemplate;
        this.baseUrl = endpoints.schedulingServiceBaseUrl();
    }

    /**
     * Returns the scheduling-service JSON body (resource_id, date, slots[]), not wrapped in {@code data}.
     */
    public JsonNode getSlots(String resourceId, String date) {
        String url = UriComponentsBuilder.fromHttpUrl(baseUrl + "/v1/slots")
                .queryParam("resource_id", resourceId)
                .queryParam("date", date)
                .toUriString();
        log.debug("Scheduling-service: GET slots resource_id={} date={}", resourceId, date);
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return response.getBody();
    }
}
