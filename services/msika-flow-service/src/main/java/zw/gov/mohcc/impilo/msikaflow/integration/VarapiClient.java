package zw.gov.mohcc.impilo.msikaflow.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class VarapiClient {

    private static final Logger log = LoggerFactory.getLogger(VarapiClient.class);

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String varapiBaseUrl;

    public VarapiClient(ObjectMapper objectMapper,
                        @Value("${msika-flow.integration.varapi-url:http://localhost:8083}") String varapiBaseUrl) {
        this.restTemplate = new RestTemplate();
        this.objectMapper = objectMapper;
        this.varapiBaseUrl = varapiBaseUrl;
    }

    public JsonNode getStandingSummary(String providerPublicId) {
        try {
            String url = varapiBaseUrl + "/v1/internal/providers/" + providerPublicId + "/standing-summary";
            String response = restTemplate.getForObject(url, String.class);
            if (response != null) {
                return objectMapper.readTree(response).path("data");
            }
        } catch (Exception e) {
            log.warn("VARAPI standing-summary failed for {}: {}", providerPublicId, e.getMessage());
        }
        return null;
    }

    /**
     * Provider recognition by Health ID — {recognised, licenceStatus,
     * profession, cadre}. Upstream is enumeration-resistant (generic
     * recognised=false for unknown ids). Returns null on transport failure so
     * callers FAIL CLOSED for authorization decisions (a down registry never
     * self-authorizes a regulated purchase).
     */
    public JsonNode getRecognitionByHealthId(String healthId) {
        if (healthId == null || healthId.isBlank()) {
            return null;
        }
        try {
            String url = varapiBaseUrl + "/v1/internal/providers/by-health-id/"
                    + healthId.trim() + "/recognition";
            String response = restTemplate.getForObject(url, String.class);
            if (response != null) {
                return objectMapper.readTree(response).path("data");
            }
        } catch (Exception e) {
            log.warn("VARAPI recognition lookup failed: {}", e.getMessage());
        }
        return null;
    }
}
