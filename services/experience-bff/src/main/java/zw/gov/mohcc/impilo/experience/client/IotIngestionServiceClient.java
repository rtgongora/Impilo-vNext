package zw.gov.mohcc.impilo.experience.client;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;

import java.util.Map;

@Component
public class IotIngestionServiceClient {

    private static final Logger log = LoggerFactory.getLogger(IotIngestionServiceClient.class);

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public IotIngestionServiceClient(RestTemplate serviceRestTemplate, ServiceClientConfig.ServiceEndpoints endpoints) {
        this.restTemplate = serviceRestTemplate;
        this.baseUrl = endpoints.iotIngestionBaseUrl();
    }

    public JsonNode listDevices(String ownerHealthId) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(baseUrl + "/internal/v1/devices");
        if (ownerHealthId != null && !ownerHealthId.isBlank()) {
            builder.queryParam("owner_health_id", ownerHealthId);
        }
        log.debug("IoT: listDevices");
        return extractData(restTemplate.getForEntity(builder.toUriString(), JsonNode.class));
    }

    public JsonNode registerDevice(Map<String, Object> body) {
        log.info("IoT: registerDevice");
        ResponseEntity<JsonNode> response =
                restTemplate.postForEntity(baseUrl + "/internal/v1/devices/register", new HttpEntity<>(body), JsonNode.class);
        return extractData(response);
    }

    public JsonNode revokeDevice(String deviceId, Map<String, Object> body) {
        log.info("IoT: revokeDevice id={}", deviceId);
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(
                baseUrl + "/internal/v1/devices/" + deviceId + "/revoke", new HttpEntity<>(body), JsonNode.class);
        return extractData(response);
    }

    public JsonNode getDeviceTrust(String deviceId) {
        log.debug("IoT: getDeviceTrust id={}", deviceId);
        return extractData(restTemplate.getForEntity(baseUrl + "/internal/v1/devices/" + deviceId + "/trust", JsonNode.class));
    }

    private JsonNode extractData(ResponseEntity<JsonNode> response) {
        if (response.getBody() != null && response.getBody().has("data")) {
            return response.getBody().get("data");
        }
        return response.getBody();
    }
}
