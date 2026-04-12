package zw.gov.mohcc.impilo.experience.client;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;

/**
 * HTTP client for {@code inventory-elmis-adapter} (sync state).
 */
@Component
public class InventoryElmisAdapterClient {

    private static final Logger log = LoggerFactory.getLogger(InventoryElmisAdapterClient.class);

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public InventoryElmisAdapterClient(RestTemplate serviceRestTemplate,
                                       ServiceClientConfig.ServiceEndpoints endpoints) {
        this.restTemplate = serviceRestTemplate;
        this.baseUrl = endpoints.inventoryElmisBaseUrl();
    }

    public JsonNode listSyncStates() {
        return getJson(baseUrl + "/internal/v1/sync-states");
    }

    public JsonNode getSyncState(long id) {
        return getJson(baseUrl + "/internal/v1/sync-states/" + id);
    }

    public JsonNode triggerSync(JsonNode body) {
        log.debug("eLMIS adapter POST trigger");
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<JsonNode> entity = new HttpEntity<>(body, headers);
        ResponseEntity<JsonNode> r = restTemplate.exchange(
                baseUrl + "/internal/v1/sync-states/trigger",
                HttpMethod.POST,
                entity,
                JsonNode.class);
        return r.getBody();
    }

    private JsonNode getJson(String url) {
        log.debug("eLMIS adapter GET {}", url);
        ResponseEntity<JsonNode> r = restTemplate.getForEntity(url, JsonNode.class);
        return r.getBody();
    }
}
