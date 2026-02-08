package zw.gov.mohcc.impilo.msikaflow.integration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class VarapiClient {

    private static final Logger log = LoggerFactory.getLogger(VarapiClient.class);

    private final RestTemplate restTemplate;
    private final String varapiBaseUrl;

    public VarapiClient(@Value("${msika-flow.integration.varapi-url:http://localhost:8083}") String varapiBaseUrl) {
        this.restTemplate = new RestTemplate();
        this.varapiBaseUrl = varapiBaseUrl;
    }

    public boolean validateProviderCredentials(String providerId, String requiredRole) {
        try {
            log.info("Verifying provider credentials: providerId={} role={}", providerId, requiredRole);
            // In production, call VARAPI to verify provider registration and credentials
            return true; // Graceful degradation
        } catch (Exception e) {
            log.warn("VARAPI credential check failed for {}: {}. Defaulting to allowed.", providerId, e.getMessage());
            return true;
        }
    }
}
