package zw.gov.mohcc.impilo.tuso.integration;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import zw.gov.mohcc.impilo.tuso.api.dto.FacilityResponse.ExperienceDomain;
import zw.gov.mohcc.impilo.tuso.api.dto.FacilityResponse.FacilityExperienceSummary;
import zw.gov.mohcc.impilo.tuso.config.TusoProperties;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads a facility's public verified-experience summary from Rito (the reputation SoR).
 * TUSO composes and displays this read-only, source-tagged block on the public facility
 * profile; it never owns or stores ratings. Gated + fail-open: if Rito is disabled or
 * unavailable the caller gets {@code null} and the profile renders without the block.
 */
@Service
public class RitoClient {

    private static final Logger log = LoggerFactory.getLogger(RitoClient.class);

    private final TusoProperties tusoProperties;
    private final RestClient restClient;

    public RitoClient(TusoProperties tusoProperties, RestClient.Builder restClientBuilder) {
        this.tusoProperties = tusoProperties;
        this.restClient = restClientBuilder.baseUrl(tusoProperties.getRito().getBaseUrl()).build();
    }

    public boolean isEnabled() {
        TusoProperties.RitoProperties p = tusoProperties.getRito();
        return p.isEnabled() && p.getBaseUrl() != null && !p.getBaseUrl().isBlank();
    }

    /** GET /v1/public/rito/reputation/facility/{facilityId} — null on disabled/failure. */
    public FacilityExperienceSummary getFacilityExperience(String facilityId, String tenantId) {
        if (!isEnabled() || facilityId == null || facilityId.isBlank()) {
            return null;
        }
        try {
            JsonNode body = restClient.get()
                    .uri("/v1/public/rito/reputation/facility/{facilityId}", facilityId)
                    .headers(h -> {
                        if (tenantId != null) {
                            h.set("X-Tenant-ID", tenantId);
                        }
                    })
                    .retrieve()
                    .body(JsonNode.class);
            return body == null ? null : map(body);
        } catch (RestClientException e) {
            log.warn("Failed to read Rito facility experience for {}: {}", facilityId, e.getMessage());
            return null;
        }
    }

    private static FacilityExperienceSummary map(JsonNode body) {
        List<ExperienceDomain> domains = new ArrayList<>();
        JsonNode arr = body.path("domains");
        if (arr.isArray()) {
            for (JsonNode d : arr) {
                BigDecimal mean = d.hasNonNull("verifiedMeanScore") ? d.get("verifiedMeanScore").decimalValue() : null;
                domains.add(new ExperienceDomain(d.path("domain").asText(null), mean, d.path("verifiedCount").asInt(0)));
            }
        }
        return new FacilityExperienceSummary(
                body.path("source").asText("Rito"),
                body.path("reportingPeriod").asText(null),
                body.path("verifiedInteractionTotal").asInt(0),
                domains);
    }
}
