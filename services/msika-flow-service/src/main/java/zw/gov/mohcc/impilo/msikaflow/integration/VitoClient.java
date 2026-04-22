package zw.gov.mohcc.impilo.msikaflow.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.UUID;

@Service
public class VitoClient {

    private static final Logger log = LoggerFactory.getLogger(VitoClient.class);

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String vitoBaseUrl;

    public VitoClient(ObjectMapper objectMapper,
                      @Value("${msika-flow.integration.vito-url:http://localhost:8082}") String vitoBaseUrl) {
        this.restTemplate = new RestTemplate();
        this.objectMapper = objectMapper;
        this.vitoBaseUrl = vitoBaseUrl;
    }

    public JsonNode getIdentitySummary(UUID healthId) {
        try {
            String url = vitoBaseUrl + "/v1/client-registry/clients/" + healthId + "/identity-summary";
            String response = restTemplate.getForObject(url, String.class);
            if (response != null) {
                return objectMapper.readTree(response).path("data");
            }
        } catch (Exception e) {
            log.warn("VITO identity-summary failed for {}: {}", healthId, e.getMessage());
        }
        return null;
    }
}

