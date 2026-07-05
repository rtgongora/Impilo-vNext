package zw.gov.mohcc.impilo.experience.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/**
 * HTTP client for the Organization Registry service (organizations, authorized
 * representatives, affiliations, Channel-C delegated onboarding claims).
 */
@Component
public class OrganizationRegistryServiceClient {

    private static final Logger log = LoggerFactory.getLogger(OrganizationRegistryServiceClient.class);

    private final RestTemplate restTemplate;
    private final String baseUrl;
    private final ObjectMapper objectMapper;

    public OrganizationRegistryServiceClient(
            RestTemplate serviceRestTemplate,
            @Value("${impilo.services.organization-registry-base-url:http://localhost:8153}") String baseUrl,
            ObjectMapper objectMapper) {
        this.restTemplate = serviceRestTemplate;
        this.baseUrl = baseUrl;
        this.objectMapper = objectMapper;
    }

    public JsonNode listOrganizations(String status) {
        String url = trimSlash(baseUrl) + "/v1/organizations"
                + (status != null && !status.isBlank() ? "?status=" + status : "");
        return getForJson(url);
    }

    public JsonNode getOrganization(String organizationId) {
        return getForJson(trimSlash(baseUrl) + "/v1/organizations/" + organizationId);
    }

    public JsonNode createOrganization(Object body) {
        return postForJson(trimSlash(baseUrl) + "/v1/organizations", body);
    }

    public JsonNode addRepresentative(String organizationId, Object body) {
        return postForJson(trimSlash(baseUrl) + "/v1/organizations/" + organizationId + "/representatives", body);
    }

    public JsonNode listRepresentatives(String organizationId) {
        return getForJson(trimSlash(baseUrl) + "/v1/organizations/" + organizationId + "/representatives");
    }

    public JsonNode submitVerification(String organizationId) {
        return postForJson(trimSlash(baseUrl) + "/v1/organizations/" + organizationId + "/submit-verification", null);
    }

    public JsonNode verify(String organizationId, Object body) {
        return postForJson(trimSlash(baseUrl) + "/v1/organizations/" + organizationId + "/verify", body);
    }

    public JsonNode listAffiliations(String organizationId) {
        return getForJson(trimSlash(baseUrl) + "/v1/organizations/" + organizationId + "/affiliations");
    }

    public JsonNode createAffiliation(String organizationId, Object body) {
        return postForJson(trimSlash(baseUrl) + "/v1/organizations/" + organizationId + "/affiliations", body);
    }

    public JsonNode submitClaim(String organizationId, Object body) {
        return postForJson(trimSlash(baseUrl) + "/v1/organizations/" + organizationId + "/claims", body);
    }

    public JsonNode getClaim(String claimId) {
        return getForJson(trimSlash(baseUrl) + "/v1/claims/" + claimId);
    }

    private JsonNode getForJson(String url) {
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            log.warn("Organization registry GET {} failed: {}", url, response.getStatusCode());
            throw new IllegalStateException("organization-registry GET failed: " + response.getStatusCode());
        }
        return response.getBody();
    }

    private JsonNode postForJson(String url, Object body) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            String json = body == null ? "{}" : (body instanceof String s ? s : objectMapper.writeValueAsString(body));
            HttpEntity<String> entity = new HttpEntity<>(json, headers);
            ResponseEntity<JsonNode> response = restTemplate.exchange(url, HttpMethod.POST, entity, JsonNode.class);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                log.warn("Organization registry POST {} failed: {}", url, response.getStatusCode());
                throw new IllegalStateException("organization-registry POST failed: " + response.getStatusCode());
            }
            return response.getBody();
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Organization registry POST {} error: {}", url, e.getMessage());
            throw new IllegalStateException("organization-registry POST error: " + e.getMessage(), e);
        }
    }

    private static String trimSlash(String base) {
        if (base == null) {
            return "";
        }
        if (base.endsWith("/")) {
            return base.substring(0, base.length() - 1);
        }
        return base;
    }
}
