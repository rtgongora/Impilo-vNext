package zw.gov.mohcc.impilo.experience.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;

/**
 * HTTP client for the Butano clinical summary service.
 *
 * <p>Returns raw upstream bodies so the Experience UI can render FHIR bundles
 * without lossy JSON envelope rewrites in the BFF.</p>
 */
@Component
public class ButanoServiceClient {

    private static final Logger log = LoggerFactory.getLogger(ButanoServiceClient.class);

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public ButanoServiceClient(RestTemplate serviceRestTemplate,
                               ServiceClientConfig.ServiceEndpoints endpoints) {
        this.restTemplate = serviceRestTemplate;
        this.baseUrl = endpoints.butanoBaseUrl();
    }

    public ResponseEntity<String> getIpsSummary(String cpid) {
        String url = baseUrl + "/v1/summary/ips/" + cpid;
        log.info("BUTANO: Fetching IPS summary for cpid={}", cpid);
        return restTemplate.getForEntity(url, String.class);
    }

    public ResponseEntity<String> getVisitSummary(String encounterId) {
        String url = baseUrl + "/v1/summary/visit/" + encounterId;
        log.info("BUTANO: Fetching visit summary for encounterId={}", encounterId);
        return restTemplate.getForEntity(url, String.class);
    }
}
