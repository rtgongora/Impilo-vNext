package zw.gov.mohcc.impilo.varapi.integration;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.varapi.api.dto.PublicPractitionerVerificationResponse.ExperienceDomain;
import zw.gov.mohcc.impilo.varapi.api.dto.PublicPractitionerVerificationResponse.ExperienceSummary;
import zw.gov.mohcc.impilo.varapi.config.VarapiProperties;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Reads a provider's public verified-experience summary from Rito (the reputation SoR).
 * VARAPI composes and displays this read-only, source-tagged block on a provider profile;
 * it never owns or stores ratings. Gated + fail-open: if Rito is disabled or unavailable
 * the caller simply gets {@code null} and the profile renders without the block.
 */
@Component
public class RitoClient {

    private static final Logger log = LoggerFactory.getLogger(RitoClient.class);

    private final RestTemplate restTemplate;
    private final VarapiProperties varapiProperties;

    public RitoClient(RestTemplate restTemplate, VarapiProperties varapiProperties) {
        this.restTemplate = restTemplate;
        this.varapiProperties = varapiProperties;
    }

    public boolean isEnabled() {
        VarapiProperties.RitoProperties p = varapiProperties.getRito();
        return p.isEnabled() && p.getBaseUrl() != null && !p.getBaseUrl().isBlank();
    }

    /** GET /v1/public/rito/reputation/{providerPublicId} — null on disabled/failure. */
    public ExperienceSummary getExperienceSummary(UUID tenantId, String providerPublicId) {
        if (!isEnabled() || tenantId == null || providerPublicId == null || providerPublicId.isBlank()) {
            return null;
        }
        String base = trimSlash(varapiProperties.getRito().getBaseUrl());
        String url = base + "/v1/public/rito/reputation/" + providerPublicId;
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Tenant-ID", tenantId.toString());
        try {
            ResponseEntity<JsonNode> response = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(headers), JsonNode.class);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                return null;
            }
            return map(response.getBody());
        } catch (RestClientException e) {
            log.warn("Failed to read Rito experience summary for provider {}: {}", providerPublicId, e.getMessage());
            return null;
        }
    }

    private static ExperienceSummary map(JsonNode body) {
        List<ExperienceDomain> domains = new ArrayList<>();
        JsonNode arr = body.path("domains");
        if (arr.isArray()) {
            for (JsonNode d : arr) {
                BigDecimal mean = d.hasNonNull("verifiedMeanScore") ? d.get("verifiedMeanScore").decimalValue() : null;
                domains.add(new ExperienceDomain(d.path("domain").asText(null), mean,
                        d.path("verifiedCount").asInt(0)));
            }
        }
        return new ExperienceSummary(
                body.path("source").asText("Rito"),
                body.path("reportingPeriod").asText(null),
                body.path("verifiedInteractionTotal").asInt(0),
                domains);
    }

    private static String trimSlash(String s) {
        return (s != null && s.endsWith("/")) ? s.substring(0, s.length() - 1) : s;
    }
}
