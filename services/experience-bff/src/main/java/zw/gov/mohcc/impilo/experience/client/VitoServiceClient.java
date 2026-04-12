package zw.gov.mohcc.impilo.experience.client;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;

import java.util.LinkedHashMap;
import java.util.Map;

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

    /** Register a new patient identity and get a CPID. */
    public JsonNode registerIdentity(Map<String, Object> patientData) {
        String url = baseUrl + "/v1/identity/register";
        log.info("VITO: Registering new patient identity");
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, patientData, JsonNode.class);
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
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
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
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(builder.toUriString(), JsonNode.class);
        return extractData(response);
    }

    /** Get a single patient by ID. */
    public JsonNode getPatient(String id) {
        String url = baseUrl + "/v1/internal/clients/" + id;
        log.info("VITO: Getting patient id={}", id);
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    /** Register a new patient. */
    public JsonNode registerPatient(Map<String, Object> patientData) {
        String url = baseUrl + "/v1/internal/clients";
        log.info("VITO: Registering new patient");
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, patientData, JsonNode.class);
        return extractData(response);
    }

    private JsonNode extractData(ResponseEntity<JsonNode> response) {
        if (response.getBody() != null && response.getBody().has("data")) {
            return response.getBody().get("data");
        }
        return response.getBody();
    }
}
