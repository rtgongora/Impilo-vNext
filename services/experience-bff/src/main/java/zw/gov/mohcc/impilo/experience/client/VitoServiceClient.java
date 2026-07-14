package zw.gov.mohcc.impilo.experience.client;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * HTTP client for the VITO (Client Registry / Patient Identity) service.
 *
 * Provides access to patient identity operations: registration,
 * resolution, validation, recovery, and issuance.
 */
@Component
public class VitoServiceClient {

    private static final Logger log = LoggerFactory.getLogger(VitoServiceClient.class);

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public VitoServiceClient(RestTemplate serviceRestTemplate,
                             ServiceClientConfig.ServiceEndpoints endpoints) {
        this.restTemplate = serviceRestTemplate;
        this.baseUrl = endpoints.vitoBaseUrl();
    }

    /** Register a new patient identity (INTERNAL) — issues Health ID / provisional client. */
    public JsonNode registerIdentity(Map<String, Object> patientData) {
        String url = baseUrl + "/v1/identity/register";
        log.info("VITO: Registering new patient identity");
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, patientData, JsonNode.class);
        return extractData(response);
    }

    /**
     * Masked client search (INTERNAL) — search-before-create for operators.
     */
    public JsonNode searchInternalClients(Map<String, Object> body) {
        String url = baseUrl + "/v1/internal/clients/search";
        log.info("VITO: Internal masked client search");
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body, JsonNode.class);
        return extractData(response);
    }

    /** Resolve a health ID to a CPID. */
    public JsonNode resolveIdentity(String healthId) {
        String url = baseUrl + "/v1/identity/resolve";
        Map<String, Object> body = Map.of("healthId", healthId);
        log.info("VITO: Resolving identity for healthId={}", healthId);
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body, JsonNode.class);
        return extractData(response);
    }

    /** Start ID recovery process. */
    public JsonNode startRecovery(Map<String, Object> recoveryData) {
        String url = baseUrl + "/v1/portal/id/recovery/start";
        log.info("VITO: Starting ID recovery");
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, recoveryData, JsonNode.class);
        return extractData(response);
    }

    /** Verify ID recovery with proof. */
    public JsonNode verifyRecovery(Map<String, Object> verificationData) {
        String url = baseUrl + "/v1/portal/id/recovery/verify";
        log.info("VITO: Verifying ID recovery");
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, verificationData, JsonNode.class);
        return extractData(response);
    }

    /** Search clients in the registry. */
    public JsonNode searchClients(String query) {
        String url = UriComponentsBuilder.fromHttpUrl(baseUrl + "/v1/internal/clients")
                .queryParam("search", query)
                .toUriString();
        log.info("VITO: Searching clients queryLength={}", query != null ? query.length() : 0);
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(java.net.URI.create(url), JsonNode.class);
        return extractData(response);
    }

    /** Get citizen profile by CPID. */
    public JsonNode getCitizenProfile(String cpid) {
        String url = baseUrl + "/v1/identity/profile/" + cpid;
        log.info("VITO: Getting citizen profile for cpid={}", cpid);
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    /** Update citizen profile. */
    public JsonNode updateCitizenProfile(String cpid, Map<String, Object> updates) {
        String url = baseUrl + "/v1/identity/profile/" + cpid;
        log.info("VITO: Updating citizen profile for cpid={}", cpid);
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, updates, JsonNode.class);
        return extractData(response);
    }

    /** Delete/deactivate citizen account. */
    public void deleteCitizenAccount(String cpid) {
        String url = baseUrl + "/v1/identity/profile/" + cpid + "/deactivate";
        log.info("VITO: Deactivating citizen account for cpid={}", cpid);
        restTemplate.postForEntity(url, null, JsonNode.class);
    }

    /** List patients with search, status, pagination (internal). */
    public JsonNode listPatients(String search, String status, int page, int size) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(baseUrl + "/v1/internal/clients")
                .queryParam("page", page)
                .queryParam("size", size);
        if (search != null && !search.isBlank()) builder.queryParam("search", search);
        if (status != null && !status.isBlank()) builder.queryParam("status", status);
        log.info("VITO: Listing patients page={}, size={}", page, size);
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(builder.encode().build().toUri(), JsonNode.class);
        return extractData(response);
    }

    /** Get a single client by Health ID (canonical read path on {@code /v1/clients}). */
    public JsonNode getPatient(String healthId) {
        String url = baseUrl + "/v1/clients/" + healthId;
        log.info("VITO: Getting client healthId={}", healthId);
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    /**
     * Walk-in / Experience patient registration — delegates to {@link #registerIdentity(Map)}
     * ({@code POST /v1/identity/register}) so Health ID anchoring stays on the golden issuance path.
     */
    public JsonNode registerPatient(Map<String, Object> patientData) {
        Map<String, Object> issuance = new LinkedHashMap<>();
        issuance.put("givenName", patientData.get("given_name"));
        issuance.put("familyName", patientData.get("family_name"));
        issuance.put("dateOfBirth", patientData.get("date_of_birth"));
        issuance.put("sex", patientData.get("sex"));
        // Forward registry-template / Tshepo context fields for Vito (unknown keys ignored downstream if unsupported).
        for (Map.Entry<String, Object> e : patientData.entrySet()) {
            String k = e.getKey();
            if (e.getValue() == null) {
                continue;
            }
            if ("given_name".equals(k) || "family_name".equals(k) || "date_of_birth".equals(k) || "sex".equals(k)) {
                continue;
            }
            issuance.put(k, e.getValue());
        }
        return registerIdentity(issuance);
    }

    /**
     * Get staff assignments for a person by their Health ID (actor ID).
     *
     * <p>Used by the post-login identity resolution flow to discover whether
     * the person has a Staff ID / facility assignment in VITO.</p>
     */
    public JsonNode getStaffAssignments(String healthId) {
        String url = baseUrl + "/v1/identity/" + healthId + "/staff-assignments";
        log.info("VITO: Getting staff assignments for healthId={}", healthId);
        try {
            ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
            return extractData(response);
        } catch (Exception e) {
            log.debug("VITO: No staff assignments for healthId={}: {}", healthId, e.getMessage());
            return null;
        }
    }

    public JsonNode listClientRegistryClients(String query, String status, String verificationState, int page, int size) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(baseUrl + "/v1/client-registry/clients")
                .queryParam("page", page)
                .queryParam("size", size);
        if (query != null && !query.isBlank()) builder.queryParam("query", query);
        if (status != null && !status.isBlank()) builder.queryParam("status", status);
        if (verificationState != null && !verificationState.isBlank()) builder.queryParam("verificationState", verificationState);
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(builder.encode().build().toUri(), JsonNode.class);
        return extractData(response);
    }

    public JsonNode getClientRegistryProfile(String healthId) {
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(
                baseUrl + "/v1/client-registry/clients/" + healthId, JsonNode.class);
        return extractData(response);
    }

    public JsonNode createClientRegistration(Map<String, Object> body) {
        return postJson(baseUrl + "/v1/client-registry/registrations", body);
    }

    public JsonNode submitClientRegistration(String registrationId) {
        return postJson(baseUrl + "/v1/client-registry/registrations/" + registrationId + "/submit", null);
    }

    public JsonNode addClientEvidence(String healthId, Map<String, Object> body) {
        return postJson(baseUrl + "/v1/client-registry/clients/" + healthId + "/evidence", body);
    }

    /**
     * Patient-mediated share issuance (authenticated lane on VITO).
     * {@code POST /v1/clients/{healthId}/patient-shares}
     */
    public JsonNode createPatientShare(UUID healthId, Map<String, Object> body) {
        String url = baseUrl + "/v1/clients/" + healthId + "/patient-shares";
        log.info("VITO: Creating patient share for healthId={}", healthId);
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body, JsonNode.class);
        return extractData(response);
    }

    /**
     * Validates a share artifact token (public lane — no session yet).
     * {@code POST /v1/public/patient-shares/validate}
     */
    public JsonNode validatePublicPatientShareArtifact(Map<String, Object> body) {
        String url = baseUrl + "/v1/public/patient-shares/validate";
        log.info("VITO: Validating public patient-share artifact");
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body, JsonNode.class);
        return extractData(response);
    }

    public JsonNode recordClientVerificationReview(String healthId, Map<String, Object> body) {
        return postJson(baseUrl + "/v1/client-registry/clients/" + healthId + "/verification-reviews", body);
    }

    public JsonNode runClientMatching(String healthId) {
        return postJson(baseUrl + "/v1/client-registry/clients/" + healthId + "/match", null);
    }

    public JsonNode reviewClientMatchCandidate(String matchId, Map<String, Object> body) {
        return postJson(baseUrl + "/v1/client-registry/match-candidates/" + matchId + "/review", body);
    }

    public JsonNode createClientMergeCase(Map<String, Object> body) {
        return postJson(baseUrl + "/v1/client-registry/merge-cases", body);
    }

    public JsonNode decideClientMergeCase(String mergeCaseId, Map<String, Object> body) {
        return postJson(baseUrl + "/v1/client-registry/merge-cases/" + mergeCaseId + "/decision", body);
    }

    public JsonNode addClientRelationship(String healthId, Map<String, Object> body) {
        return postJson(baseUrl + "/v1/client-registry/clients/" + healthId + "/relationships", body);
    }

    public JsonNode addClientAuthorizationLink(String healthId, Map<String, Object> body) {
        return postJson(baseUrl + "/v1/client-registry/clients/" + healthId + "/authorization-links", body);
    }

    public JsonNode createClientStewardshipAction(String healthId, Map<String, Object> body) {
        return postJson(baseUrl + "/v1/client-registry/clients/" + healthId + "/stewardship-actions", body);
    }

    public JsonNode updateClientStewardshipAction(String actionId, Map<String, Object> body) {
        return postJson(baseUrl + "/v1/client-registry/stewardship-actions/" + actionId, body);
    }

    public JsonNode recordClientCorrection(String healthId, Map<String, Object> body) {
        return postJson(baseUrl + "/v1/client-registry/clients/" + healthId + "/corrections", body);
    }

    /**
     * Update a client's demographics — canonical SoR write {@code PUT /v1/clients/{healthId}}
     * (preserves all demographic fields). Typed sibling of the client-registry operations above;
     * consolidates the former raw {@code rawPut} passthrough (G14 Stack A consolidation).
     */
    public JsonNode updateClientDemographics(String healthId, Map<String, Object> body) {
        return putJson(baseUrl + "/v1/clients/" + healthId, body);
    }

    public JsonNode getClientRegistryDashboard() {
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(
                baseUrl + "/v1/client-registry/dashboard/summary", JsonNode.class);
        return extractData(response);
    }

    public JsonNode getClientStewardshipWorkspace() {
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(
                baseUrl + "/v1/client-registry/stewardship/workspace", JsonNode.class);
        return extractData(response);
    }

    private JsonNode extractData(ResponseEntity<JsonNode> response) {
        if (response.getBody() != null && response.getBody().has("data")) {
            return response.getBody().get("data");
        }
        return response.getBody();
    }

    private JsonNode postJson(String url, Map<String, Object> body) {
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body, JsonNode.class);
        return extractData(response);
    }

    private JsonNode putJson(String url, Map<String, Object> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<JsonNode> response = restTemplate.exchange(
                url, HttpMethod.PUT, new HttpEntity<>(body, headers), JsonNode.class);
        return extractData(response);
    }

    /** Raw JSON string response for BFF biometric proxying (preserves VITO {@code ApiResponse} envelope). */
    public ResponseEntity<String> rawGet(String path) {
        return restTemplate.getForEntity(baseUrl + path, String.class);
    }

    public ResponseEntity<String> rawPost(String path, Object body) {
        return restTemplate.postForEntity(baseUrl + path, body, String.class);
    }

    public ResponseEntity<String> rawPut(String path, Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return restTemplate.exchange(
                baseUrl + path, HttpMethod.PUT, new HttpEntity<>(body, headers), String.class);
    }

    public ResponseEntity<String> rawPostWithHeader(String path, Object body, String headerName, String headerValue) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(headerName, headerValue);
        return restTemplate.exchange(
                baseUrl + path, HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
    }

    public ResponseEntity<String> rawGetWithHeader(String path, String headerName, String headerValue) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(headerName, headerValue);
        return restTemplate.exchange(baseUrl + path, HttpMethod.GET, new HttpEntity<>(headers), String.class);
    }

    public ResponseEntity<byte[]> rawGetBytes(String path) {
        return restTemplate.getForEntity(baseUrl + path, byte[].class);
    }
}
