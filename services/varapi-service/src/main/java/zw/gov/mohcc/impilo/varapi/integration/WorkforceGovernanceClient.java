package zw.gov.mohcc.impilo.varapi.integration;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.varapi.config.VarapiProperties;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Reads assignment rows from Workforce Governance for provider directory enrichment.
 */
@Service
public class WorkforceGovernanceClient {

    private static final Logger log = LoggerFactory.getLogger(WorkforceGovernanceClient.class);

    private final RestTemplate restTemplate;
    private final VarapiProperties varapiProperties;

    public WorkforceGovernanceClient(RestTemplate restTemplate, VarapiProperties varapiProperties) {
        this.restTemplate = restTemplate;
        this.varapiProperties = varapiProperties;
    }

    public boolean isEnabled() {
        VarapiProperties.GovernanceProperties g = varapiProperties.getGovernance();
        return g.isEnabled() && g.getBaseUrl() != null && !g.getBaseUrl().isBlank();
    }

    /**
     * @return {@code data} node from governance API (typically an array of assignments), or {@code null} if disabled or on failure.
     */
    public JsonNode fetchProviderAssignments(String providerPublicId) {
        if (!isEnabled()) {
            return null;
        }
        TrustContext ctx = TrustContextHolder.require();
        if (ctx.tenantId() == null) {
            return null;
        }
        try {
            String base = trimSlash(varapiProperties.getGovernance().getBaseUrl());
            String q = URLEncoder.encode(providerPublicId, StandardCharsets.UTF_8);
            String url = base + "/v1/internal/governance/assignments/search?subjectType=PROVIDER&subjectId=" + q;

            HttpHeaders headers = new HttpHeaders();
            headers.set("x-tenant-id", ctx.tenantId().toString());
            if (ctx.actorId() != null) {
                headers.set("x-actor-id", ctx.actorId());
            }
            if (ctx.actorType() != null) {
                headers.set("x-actor-type", ctx.actorType());
            }
            headers.set("x-purpose-of-use", ctx.purposeOfUse() != null ? ctx.purposeOfUse() : "REGISTRY_ADMIN");
            headers.set("x-device-fingerprint", ctx.deviceFingerprint() != null ? ctx.deviceFingerprint() : "varapi-registry");
            if (ctx.correlationId() != null) {
                headers.set("x-correlation-id", ctx.correlationId().toString());
            }

            ResponseEntity<JsonNode> response = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(headers), JsonNode.class);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                return null;
            }
            return response.getBody().path("data");
        } catch (RestClientException e) {
            log.warn("Workforce governance assignments lookup failed for {}: {}", providerPublicId, e.getMessage());
            return null;
        }
    }

    private static String trimSlash(String base) {
        if (base.endsWith("/")) {
            return base.substring(0, base.length() - 1);
        }
        return base;
    }
}
