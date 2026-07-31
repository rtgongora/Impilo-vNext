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

    /** Regulatory appointments held by a person (ROM-W2) — the org-session context source. */
    public JsonNode listAppointmentsByPerson(String personHealthId) {
        return getForJson(trimSlash(baseUrl) + "/v1/regulatory/appointments/by-person/"
                + java.net.URLEncoder.encode(personHealthId, java.nio.charset.StandardCharsets.UTF_8));
    }

    // ── Regulator user administration (RB-6): the appointment roster + lifecycle for one regulator.

    /** The appointment roster for one regulator organisation — the users-admin read model. */
    public JsonNode listAppointmentsForOrganization(String organizationId) {
        return getForJson(trimSlash(baseUrl) + "/v1/regulatory/organizations/" + organizationId + "/appointments");
    }

    /** The closed appointment-role vocabulary, so the invite form offers governed roles only. */
    // ── Regulatory Configuration Registry reads (NCZ-W1A) ────────────────────────────────────

    public JsonNode listConfigPacks(String organizationId) {
        return exchangeJson(HttpMethod.GET,
                baseUrl + "/v1/regulatory/config/organizations/" + organizationId + "/packs", null);
    }

    /** Org-scoped configuration audit events (pack/release/activation). */
    public JsonNode listConfigAuditEvents(String organizationId) {
        return exchangeJson(HttpMethod.GET,
                baseUrl + "/v1/regulatory/config/organizations/" + organizationId + "/audit-events", null);
    }

    public JsonNode listConfigReleases(String packId) {
        return exchangeJson(HttpMethod.GET,
                baseUrl + "/v1/regulatory/config/packs/" + packId + "/releases", null);
    }

    public JsonNode listConfigDefinitions(String packId) {
        return exchangeJson(HttpMethod.GET,
                baseUrl + "/v1/regulatory/config/packs/" + packId + "/definitions", null);
    }

    public JsonNode activeConfiguration(String packId) {
        return exchangeJson(HttpMethod.GET,
                baseUrl + "/v1/regulatory/config/packs/" + packId + "/active", null);
    }

    public JsonNode configDefinitionVersions(String definitionId) {
        return exchangeJson(HttpMethod.GET,
                baseUrl + "/v1/regulatory/config/definitions/" + definitionId + "/versions", null);
    }

    public JsonNode listAppointmentRoles() {
        return getForJson(trimSlash(baseUrl) + "/v1/regulatory/appointment-roles");
    }

    public JsonNode createAppointment(String organizationId, Object body) {
        return postForJson(trimSlash(baseUrl) + "/v1/regulatory/organizations/" + organizationId + "/appointments", body);
    }

    public JsonNode verifyAppointment(String appointmentId, Object body) {
        return postForJson(trimSlash(baseUrl) + "/v1/regulatory/appointments/" + appointmentId + "/verify", body);
    }

    public JsonNode endAppointment(String appointmentId, Object body) {
        return postForJson(trimSlash(baseUrl) + "/v1/regulatory/appointments/" + appointmentId + "/end", body);
    }

    /** Invite a colleague into a governed role (RB-4 hashed, single-use invitation rail). */
    public JsonNode createInvitation(String organizationId, Object body) {
        return postForJson(trimSlash(baseUrl) + "/v1/organizations/" + organizationId + "/invitations", body);
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

    public JsonNode updateLogo(String organizationId, Object body) {
        return exchangeJson(HttpMethod.PUT,
                trimSlash(baseUrl) + "/v1/organizations/" + organizationId + "/logo", body);
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
        return exchangeJson(HttpMethod.POST, url, body);
    }

    private JsonNode exchangeJson(HttpMethod method, String url, Object body) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            String json = body == null ? "{}" : (body instanceof String s ? s : objectMapper.writeValueAsString(body));
            HttpEntity<String> entity = new HttpEntity<>(json, headers);
            ResponseEntity<JsonNode> response = restTemplate.exchange(url, method, entity, JsonNode.class);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                log.warn("Organization registry {} {} failed: {}", method, url, response.getStatusCode());
                throw new IllegalStateException("organization-registry " + method + " failed: " + response.getStatusCode());
            }
            return response.getBody();
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Organization registry {} {} error: {}", method, url, e.getMessage());
            throw new IllegalStateException("organization-registry " + method + " error: " + e.getMessage(), e);
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
