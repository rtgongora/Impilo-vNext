package zw.gov.mohcc.impilo.msikaflow.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class MsikaCoreClient {

    private static final Logger log = LoggerFactory.getLogger(MsikaCoreClient.class);

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String msikaCoreBaseUrl;

    public MsikaCoreClient(ObjectMapper objectMapper,
                           @Value("${msika-flow.integration.msika-core-url:http://localhost:8086}") String msikaCoreBaseUrl) {
        this.restTemplate = new RestTemplate();
        this.objectMapper = objectMapper;
        this.msikaCoreBaseUrl = msikaCoreBaseUrl;
    }

    public JsonNode lookupItem(String msikaCoreCode) {
        try {
            String url = msikaCoreBaseUrl + "/v1/items/" + msikaCoreCode;
            String response = restTemplate.getForObject(url, String.class);
            if (response != null) {
                JsonNode envelope = objectMapper.readTree(response);
                return envelope.path("data");
            }
        } catch (Exception e) {
            log.warn("MSIKA Core lookup failed for {}: {}. Using graceful degradation.", msikaCoreCode, e.getMessage());
        }
        // Graceful degradation: return a permissive default
        return objectMapper.createObjectNode()
                .put("code", msikaCoreCode)
                .put("status", "UNKNOWN");
    }

    public JsonNode lookupRestrictions(String msikaCoreCode) {
        JsonNode item = lookupItem(msikaCoreCode);
        return item.path("restrictions");
    }
}
