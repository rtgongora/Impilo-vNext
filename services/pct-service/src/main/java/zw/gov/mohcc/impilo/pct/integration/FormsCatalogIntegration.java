package zw.gov.mohcc.impilo.pct.integration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.pct.core.forms.FormCatalogEntry;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Thin client to forms-service {@code GET /internal/v1/forms/catalog} — the clinical form definition
 * slice the resolver buckets. Degrades gracefully: on any failure it returns an empty catalog (the
 * resolver then produces an empty obligation set rather than erroring the encounter).
 */
@Service
public class FormsCatalogIntegration {

    private static final Logger log = LoggerFactory.getLogger(FormsCatalogIntegration.class);

    private final RestTemplate restTemplate;
    private final String baseUrl;
    private final String podId;

    public FormsCatalogIntegration(RestTemplate restTemplate,
                                   @Value("${pct.integration.forms.base-url:http://localhost:8240}") String baseUrl,
                                   @Value("${pct.integration.forms.pod-id:national-spine}") String podId) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl;
        this.podId = podId;
    }

    /** Fetch the tenant's clinical form catalog. Forwards the mandatory trust headers forms-service requires. */
    public List<FormCatalogEntry> fetchCatalog() {
        try {
            TrustContext ctx = TrustContextHolder.require();
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Tenant-ID", ctx.tenantId() == null ? "" : ctx.tenantId().toString());
            headers.set("X-Pod-ID", podId);
            headers.set("X-Request-ID", UUID.randomUUID().toString());
            headers.set("X-Correlation-ID",
                    ctx.correlationId() == null ? UUID.randomUUID().toString() : ctx.correlationId().toString());

            ResponseEntity<List<FormCatalogEntry>> response = restTemplate.exchange(
                    baseUrl + "/internal/v1/forms/catalog",
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    new ParameterizedTypeReference<List<FormCatalogEntry>>() {});

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return response.getBody();
            }
            log.warn("forms-service catalog returned non-success status {}", response.getStatusCode());
            return Collections.emptyList();
        } catch (RestClientException | IllegalStateException e) {
            log.warn("forms-service catalog unavailable: {}", e.getMessage());
            return Collections.emptyList();
        }
    }
}
