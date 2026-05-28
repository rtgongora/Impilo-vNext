package zw.gov.mohcc.impilo.experience.client;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;

import java.util.Map;

@Component
public class NdilaServiceClient {

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public NdilaServiceClient(RestTemplate serviceRestTemplate, ServiceClientConfig.ServiceEndpoints endpoints) {
        this.restTemplate = serviceRestTemplate;
        this.baseUrl = endpoints.ndilaBaseUrl();
    }

    public JsonNode tileConfig() {
        String url = baseUrl + "/api/v1/ndila/tiles/config";
        return restTemplate.getForEntity(url, JsonNode.class).getBody();
    }

    public JsonNode nearbyAssets(Map<String, Object> body) {
        String url = baseUrl + "/api/v1/ndila/tracking/assets/nearby";
        return restTemplate.postForEntity(url, body, JsonNode.class).getBody();
    }
}
