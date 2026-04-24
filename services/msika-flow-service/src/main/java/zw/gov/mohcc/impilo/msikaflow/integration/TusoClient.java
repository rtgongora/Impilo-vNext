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
public class TusoClient {

    private static final Logger log = LoggerFactory.getLogger(TusoClient.class);

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String tusoBaseUrl;

    public TusoClient(ObjectMapper objectMapper,
                      @Value("${msika-flow.integration.tuso-url:http://localhost:8084}") String tusoBaseUrl) {
        this.restTemplate = new RestTemplate();
        this.objectMapper = objectMapper;
        this.tusoBaseUrl = tusoBaseUrl;
    }

    public JsonNode getFacilityStatusSummary(long facilityId) {
        try {
            String url = tusoBaseUrl + "/v1/internal/facilities/" + facilityId + "/status-summary";
            String response = restTemplate.getForObject(url, String.class);
            if (response != null) {
                return objectMapper.readTree(response).path("data");
            }
        } catch (Exception e) {
            log.warn("TUSO status-summary failed for facility {}: {}", facilityId, e.getMessage());
        }
        return null;
    }
}
