package zw.gov.mohcc.impilo.experience.controller.mobile.citizen;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;

import java.util.Map;

/**
 * Citizen-safe public-health feed surface for mobile.
 */
@RestController
@RequestMapping("/internal/v1/mobile/citizen/public-health")
public class CitizenPublicHealthController {

    private final RestTemplate restTemplate;
    private final String surveillanceUrl;

    public CitizenPublicHealthController(
            RestTemplate serviceRestTemplate,
            ServiceClientConfig.ServiceEndpoints endpoints) {
        this.restTemplate = serviceRestTemplate;
        this.surveillanceUrl = endpoints.surveillanceBaseUrl();
    }

    @GetMapping("/summary")
    public ResponseEntity<?> summary(@RequestHeader(CompanionHeaders.REQUEST_ID) String requestId) {
        return restTemplate.getForEntity(
                surveillanceUrl + "/internal/v1/public-health/operations-home",
                Map.class);
    }

    @GetMapping("/alerts")
    public ResponseEntity<?> alerts(@RequestHeader(CompanionHeaders.REQUEST_ID) String requestId) {
        return restTemplate.getForEntity(
                surveillanceUrl + "/internal/v1/surveillance/alerts",
                Map.class);
    }
}
