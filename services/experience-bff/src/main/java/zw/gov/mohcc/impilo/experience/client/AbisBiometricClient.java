package zw.gov.mohcc.impilo.experience.client;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;

/**
 * Thin BFF client for the ABIS biometric SoR (extract / enrol / verify). Uses the
 * trust-header-forwarding {@code serviceRestTemplate}, so the inbound X-Tenant-ID
 * and actor context flow through to ABIS. Base URL follows the estate convention
 * ({@code impilo.services.abis-base-url}, env override {@code ABIS_BASE_URL}) like
 * every other downstream of {@code abis-service}.
 */
@Component
public class AbisBiometricClient {

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public AbisBiometricClient(@Qualifier("serviceRestTemplate") RestTemplate serviceRestTemplate,
                               @Value("${impilo.services.abis-base-url:http://localhost:8186}") String baseUrl) {
        this.restTemplate = serviceRestTemplate;
        this.baseUrl = baseUrl;
    }

    /** Extract a template from a capture image. */
    public JsonNode extract(Map<String, Object> body) {
        return post("/v1/abis/templates:extract", body);
    }

    /** Enrol an (already-extracted) template under an opaque subject_ref. */
    public JsonNode enroll(Map<String, Object> body) {
        return post("/v1/abis/templates", body);
    }

    /** 1:1 verify a probe template against a subject's enrolled template. */
    public JsonNode verify(Map<String, Object> body) {
        return post("/v1/abis/verify", body);
    }

    /**
     * Restricted 1:N identify. Sends the mandatory {@code X-Identify-Reason} header
     * (∈ {ENROLMENT, RECOVERY, DEDUPLICATION, LOGIN}) and returns scored candidates —
     * NEVER an authentication or merge decision. The single-strong-candidate gate and
     * every governance/audit decision live in the caller.
     *
     * @param reason the identify reason header value (e.g. {@code LOGIN} for scan-to-login)
     */
    public JsonNode identify(String modality, String probeBase64, String reason) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Identify-Reason", reason);
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("modality", modality);
        body.put("probeBase64", probeBase64);
        ResponseEntity<JsonNode> resp = restTemplate.exchange(
                baseUrl + "/v1/abis/identify", HttpMethod.POST,
                new HttpEntity<>(body, headers), JsonNode.class);
        return resp.getBody();
    }

    // --- Adjudication + duplicate-investigation console (W3c/W3e) ---

    /** List the adjudication queue (optionally filtered by status). */
    public JsonNode listCases(String status, int page, int size) {
        UriComponentsBuilder b = UriComponentsBuilder.fromHttpUrl(baseUrl + "/v1/abis/adjudication/cases")
                .queryParam("page", page)
                .queryParam("size", size);
        if (status != null && !status.isBlank()) {
            b.queryParam("status", status);
        }
        return get(b.toUriString());
    }

    /** Fetch one adjudication case with its candidate list + scores. */
    public JsonNode getCase(long id) {
        return get(baseUrl + "/v1/abis/adjudication/cases/" + id);
    }

    /** Assign a reviewer to a case (→ IN_REVIEW). */
    public JsonNode assign(long id, Map<String, Object> body) {
        return post("/v1/abis/adjudication/cases/" + id + "/assign", body);
    }

    /** Record a DISTINCT / CONFIRMED_DUPLICATE decision (→ RESOLVED). Never merges. */
    public JsonNode decide(long id, Map<String, Object> body) {
        return post("/v1/abis/adjudication/cases/" + id + "/decision", body);
    }

    /** Governed, dual-control merge (the inbound X-Actor-ID is forwarded as the second reviewer). */
    public JsonNode merge(long id, Map<String, Object> body) {
        return post("/v1/abis/adjudication/cases/" + id + "/merge", body);
    }

    /** Operational stats (template volumes, quality distribution, adjudication queue). */
    public JsonNode stats() {
        return get(baseUrl + "/v1/abis/stats");
    }

    private JsonNode post(String path, Map<String, Object> body) {
        ResponseEntity<JsonNode> resp = restTemplate.postForEntity(baseUrl + path, body, JsonNode.class);
        return resp.getBody();
    }

    private JsonNode get(String url) {
        ResponseEntity<JsonNode> resp = restTemplate.getForEntity(url, JsonNode.class);
        return resp.getBody();
    }
}
