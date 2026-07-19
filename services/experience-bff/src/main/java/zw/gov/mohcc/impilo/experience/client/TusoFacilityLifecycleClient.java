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
 * HTTP client for the TUSO facility lifecycle self-service lanes beyond the
 * administrator claim (which has its own {@link TusoFacilityClaimClient}):
 * new-facility REGISTRATION, place VERIFICATION cases, the graded TRUST
 * dimensions, and GOVERNANCE cases (high-risk update / transfer / duplicate
 * report). TUSO is the facility SoR; this client is composition only and is
 * used exclusively by the BFF {@code FacilityLifecycleController}.
 *
 * <p>Trust/context headers ride the shared forwarding {@code RestTemplate}
 * interceptor — the actor identity is never taken from a request body.</p>
 */
@Component
public class TusoFacilityLifecycleClient {

    private static final Logger log = LoggerFactory.getLogger(TusoFacilityLifecycleClient.class);

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public TusoFacilityLifecycleClient(RestTemplate serviceRestTemplate,
                                       @Value("${impilo.services.tuso-base-url:http://localhost:8084}") String tusoBaseUrl) {
        this.restTemplate = serviceRestTemplate;
        this.baseUrl = tusoBaseUrl;
    }

    // ── Registration (new facility) ────────────────────────────────────────

    /** Submit a new-facility registration application → RegistrationReceipt (202). */
    public JsonNode submitRegistration(Map<String, Object> body) {
        String url = baseUrl + "/v1/internal/facility-registrations";
        log.info("TUSO: submitting facility registration");
        return extractData(restTemplate.postForEntity(url, new HttpEntity<>(body), JsonNode.class));
    }

    /** Steward-gated registration review queue by outcome. */
    public JsonNode registrationCases(String outcome) {
        String url = baseUrl + "/v1/internal/facility-registrations?outcome=" + enc(outcome);
        log.info("TUSO: listing facility-registration cases outcome={}", outcome);
        return extractData(restTemplate.getForEntity(url, JsonNode.class));
    }

    // ── Verification cases ─────────────────────────────────────────────────

    /** Open a facility verification case (evidence + claimed GPS). */
    public JsonNode openVerificationCase(String facilityUuid, Map<String, Object> body) {
        String url = baseUrl + "/v1/internal/facilities/" + enc(facilityUuid) + "/verification-cases";
        log.info("TUSO: opening facility verification case facility={}", facilityUuid);
        return extractData(restTemplate.postForEntity(url, new HttpEntity<>(body), JsonNode.class));
    }

    /** List verification cases for a facility (verifier-gated upstream). */
    public JsonNode verificationCases(String facilityUuid) {
        String url = baseUrl + "/v1/internal/facilities/" + enc(facilityUuid) + "/verification-cases";
        log.info("TUSO: listing facility verification cases facility={}", facilityUuid);
        return extractData(restTemplate.getForEntity(url, JsonNode.class));
    }

    /** Record a verification decision (verifier-gated upstream). */
    public JsonNode decideVerificationCase(String facilityUuid, String caseId, Map<String, Object> body) {
        String url = baseUrl + "/v1/internal/facilities/" + enc(facilityUuid)
                + "/verification-cases/" + enc(caseId) + "/decision";
        log.info("TUSO: deciding facility verification case facility={} case={}", facilityUuid, caseId);
        return extractData(restTemplate.postForEntity(url, new HttpEntity<>(body), JsonNode.class));
    }

    // ── Trust dimensions ───────────────────────────────────────────────────

    /** The facility's graded trust dimensions (transparency read). */
    public JsonNode trustDimensions(String facilityUuid) {
        String url = baseUrl + "/v1/internal/facilities/" + enc(facilityUuid) + "/trust-dimensions";
        log.info("TUSO: reading facility trust dimensions facility={}", facilityUuid);
        return extractData(restTemplate.getForEntity(url, JsonNode.class));
    }

    /** Recompute (re-materialise) the facility's trust dimensions. */
    public JsonNode recomputeTrustDimensions(String facilityUuid) {
        String url = baseUrl + "/v1/internal/facilities/" + enc(facilityUuid) + "/trust-dimensions/recompute";
        log.info("TUSO: recomputing facility trust dimensions facility={}", facilityUuid);
        return extractData(restTemplate.postForEntity(url, new HttpEntity<>(Map.of()), JsonNode.class));
    }

    // ── Governance cases ───────────────────────────────────────────────────

    /** Open a governance case of the given action (high-risk-update|transfer|duplicate-report). */
    public JsonNode openGovernanceCase(String facilityUuid, String action, Map<String, Object> body) {
        String url = baseUrl + "/v1/internal/facilities/" + enc(facilityUuid) + "/governance/" + enc(action);
        log.info("TUSO: opening facility governance case facility={} action={}", facilityUuid, action);
        return extractData(restTemplate.postForEntity(url, new HttpEntity<>(body), JsonNode.class));
    }

    /** Record a governance-case decision (steward-gated upstream). */
    public JsonNode decideGovernanceCase(String facilityUuid, String caseId, Map<String, Object> body) {
        String url = baseUrl + "/v1/internal/facilities/" + enc(facilityUuid)
                + "/governance/cases/" + enc(caseId) + "/decision";
        log.info("TUSO: deciding facility governance case facility={} case={}", facilityUuid, caseId);
        return extractData(restTemplate.postForEntity(url, new HttpEntity<>(body), JsonNode.class));
    }

    /** List governance cases for a facility by type (steward-gated upstream). */
    public JsonNode governanceCases(String facilityUuid, String caseType) {
        String url = baseUrl + "/v1/internal/facilities/" + enc(facilityUuid) + "/governance/cases?caseType=" + enc(caseType);
        log.info("TUSO: listing facility governance cases facility={} type={}", facilityUuid, caseType);
        return extractData(restTemplate.getForEntity(url, JsonNode.class));
    }

    // ── Completeness + data-gap tasks (V036 / HPA enrichment) ───────────────

    /** Per-dimension completeness for a facility. */
    public JsonNode completeness(String facilityUuid) {
        String url = baseUrl + "/v1/internal/facilities/" + enc(facilityUuid) + "/facility-data-gaps/completeness";
        return extractData(restTemplate.getForEntity(url, JsonNode.class));
    }

    /** Open + resolved data-gap / verification tasks for a facility. */
    public JsonNode dataGaps(String facilityUuid) {
        String url = baseUrl + "/v1/internal/facilities/" + enc(facilityUuid) + "/facility-data-gaps";
        return extractData(restTemplate.getForEntity(url, JsonNode.class));
    }

    /** Move a data-gap task forward with an audit note. */
    public JsonNode resolveDataGap(String facilityUuid, String taskId, Map<String, Object> body) {
        String url = baseUrl + "/v1/internal/facilities/" + enc(facilityUuid)
                + "/facility-data-gaps/" + enc(taskId) + "/resolve";
        return extractData(restTemplate.postForEntity(url, new HttpEntity<>(body), JsonNode.class));
    }

    private static String enc(String v) {
        return URLEncoder.encode(v == null ? "" : v, StandardCharsets.UTF_8);
    }

    private JsonNode extractData(ResponseEntity<JsonNode> response) {
        if (response.getBody() != null && response.getBody().has("data")) {
            return response.getBody().get("data");
        }
        return response.getBody();
    }
}
