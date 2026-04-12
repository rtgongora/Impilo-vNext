package zw.gov.mohcc.impilo.coverage.integration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.UUID;

/**
 * Calls MUSHEX mesh-internal API when an appeal overturns a claim denial.
 */
@Component
public class MushexClaimResubmitClient {

    private static final Logger log = LoggerFactory.getLogger(MushexClaimResubmitClient.class);

    private final RestClient restClient;

    public MushexClaimResubmitClient(
            RestClient.Builder restClientBuilder,
            @Value("${impilo.services.mushex-base-url:http://localhost:8102}") String baseUrl) {
        this.restClient = restClientBuilder.baseUrl(baseUrl).build();
    }

    public void notifyClaimResubmitAfterAppeal(UUID tenantId, String podId, UUID correlationId, String mushexClaimId) {
        if (mushexClaimId == null || mushexClaimId.isBlank()) {
            return;
        }
        try {
            restClient.post()
                    .uri("/internal/v1/claims/{id}/appeal-resubmit", mushexClaimId)
                    .header("X-Tenant-ID", tenantId.toString())
                    .header("X-Pod-ID", podId != null && !podId.isBlank() ? podId : "national-spine")
                    .header("X-Correlation-ID", correlationId.toString())
                    .header("X-Actor-ID", "coverage-service")
                    .header("X-Access-Mode", "INTERNAL")
                    .header("x-envoy-internal", "true")
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            log.warn("Mushex appeal-resubmit failed claimId={}: {}", mushexClaimId, e.getMessage());
        }
    }
}
