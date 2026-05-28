package zw.gov.mohcc.impilo.experience.client;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;

@Component
public class NhumeServiceClient {

    private static final Logger log = LoggerFactory.getLogger(NhumeServiceClient.class);

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public NhumeServiceClient(RestTemplate serviceRestTemplate, ServiceClientConfig.ServiceEndpoints endpoints) {
        this.restTemplate = serviceRestTemplate;
        this.baseUrl = endpoints.nhumeBaseUrl();
    }

    public JsonNode listDeliveries(String status, String facilityId, int page, int size) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(baseUrl + "/internal/v1/nhume/deliveries")
                .queryParam("page", page)
                .queryParam("size", size);
        if (status != null && !status.isBlank()) {
            builder.queryParam("status", status);
        }
        if (facilityId != null && !facilityId.isBlank()) {
            builder.queryParam("facility_id", facilityId);
        }
        String url = builder.toUriString();
        log.debug("Nhume: listDeliveries {}", url);
        return restTemplate.getForEntity(url, JsonNode.class).getBody();
    }
}
