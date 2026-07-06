package zw.gov.mohcc.impilo.experience.client;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * HTTP client for the organization-registry facility-admin affiliation seam (IATG Wave 2, WS-E).
 *
 * <p>When a facility administrator appointment goes ACTIVE, the BFF records the administering
 * relationship as an ordinary {@code FACILITY} affiliation. Org-registry stays a relationship
 * registry only — TUSO remains the facility and appointment system of record.</p>
 */
@Component
public class OrgRegistryFacilityAdminClient {

    private static final Logger log = LoggerFactory.getLogger(OrgRegistryFacilityAdminClient.class);

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public OrgRegistryFacilityAdminClient(
            RestTemplate serviceRestTemplate,
            @Value("${impilo.services.organization-registry-base-url:http://localhost:8153}") String orgRegistryBaseUrl) {
        this.restTemplate = serviceRestTemplate;
        this.baseUrl = orgRegistryBaseUrl;
    }

    /** Record the administering affiliation for a facility whose admin appointment went ACTIVE. */
    public JsonNode recordFacilityAdminAffiliation(String organizationId, Map<String, Object> body) {
        String url = baseUrl + "/v1/internal/org-registry/organizations/"
                + URLEncoder.encode(organizationId, StandardCharsets.UTF_8) + "/facility-admin-affiliations";
        log.info("ORG-REGISTRY: recording facility-admin affiliation for org={}", organizationId);
        ResponseEntity<JsonNode> response =
                restTemplate.postForEntity(url, new HttpEntity<>(body), JsonNode.class);
        return response.getBody();
    }
}
