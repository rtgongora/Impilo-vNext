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

    public boolean facilitySupportsService(UUID facilityId, String serviceCode) {
        try {
            String url = tusoBaseUrl + "/v1/facilities/" + facilityId + "/capabilities";
            String response = restTemplate.getForObject(url, String.class);
            if (response != null) {
                JsonNode data = objectMapper.readTree(response).path("data");
                // Check if facility has the capability
                return data.has("capabilities");
            }
        } catch (Exception e) {
            log.warn("TUSO capability check failed for facility {}: {}. Defaulting to allowed.", facilityId, e.getMessage());
        }
        return true; // Graceful degradation
    }
}
