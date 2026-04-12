package zw.gov.mohcc.impilo.experience.client;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;

import java.util.Map;

/**
 * HTTP client for the Coverage sovereign service.
 * Provides access to plans, eligibility, claims, preauth, and remittance.
 */
@Component
public class CoverageServiceClient {

    private static final Logger log = LoggerFactory.getLogger(CoverageServiceClient.class);
    private final RestTemplate restTemplate;
    private final String baseUrl;

    public CoverageServiceClient(RestTemplate serviceRestTemplate, ServiceClientConfig.ServiceEndpoints endpoints) {
        this.restTemplate = serviceRestTemplate;
        this.baseUrl = endpoints.coverageBaseUrl();
    }

    public JsonNode listPlans() {
        String url = baseUrl + "/internal/v1/coverage";
        log.info("COVERAGE: Listing plans");
        return extractData(restTemplate.getForEntity(url, JsonNode.class));
    }

    public JsonNode listPlansForMember(String memberCpid) {
        String url = UriComponentsBuilder.fromHttpUrl(baseUrl + "/internal/v1/coverage/plans")
                .queryParam("member_cpid", memberCpid)
                .toUriString();
        log.info("COVERAGE: Listing plans for member");
        return extractData(restTemplate.getForEntity(url, JsonNode.class));
    }

    public JsonNode getPlan(String id) {
        String url = baseUrl + "/internal/v1/coverage/" + id;
        return extractData(restTemplate.getForEntity(url, JsonNode.class));
    }

    public JsonNode getMemberCoverage(String clientId) {
        String url = baseUrl + "/internal/v1/coverage/member/" + clientId;
        return extractData(restTemplate.getForEntity(url, JsonNode.class));
    }

    public JsonNode checkEligibility(Map<String, Object> body) {
        String url = baseUrl + "/internal/v1/coverage/eligibility";
        log.info("COVERAGE: Checking eligibility");
        return extractData(restTemplate.postForEntity(url, body, JsonNode.class));
    }

    public JsonNode checkEligibilityCheckPath(Map<String, Object> body) {
        String url = baseUrl + "/internal/v1/coverage/eligibility/check";
        return extractData(restTemplate.postForEntity(url, body, JsonNode.class));
    }

    public JsonNode listEligibilityForMember(String memberCpid) {
        String url = UriComponentsBuilder.fromHttpUrl(baseUrl + "/internal/v1/coverage/eligibility")
                .queryParam("member_cpid", memberCpid)
                .toUriString();
        return extractData(restTemplate.getForEntity(url, JsonNode.class));
    }

    public JsonNode submitClaim(Map<String, Object> body) {
        String url = baseUrl + "/internal/v1/coverage/claims";
        log.info("COVERAGE: Submitting claim");
        return extractData(restTemplate.postForEntity(url, body, JsonNode.class));
    }

    public JsonNode getClaim(String id) {
        String url = baseUrl + "/internal/v1/coverage/claims/" + id;
        return extractData(restTemplate.getForEntity(url, JsonNode.class));
    }

    public JsonNode listClaims(String coverageId) {
        String url = UriComponentsBuilder.fromHttpUrl(baseUrl + "/internal/v1/coverage/claims")
                .queryParam("coverageId", coverageId)
                .toUriString();
        return extractData(restTemplate.getForEntity(url, JsonNode.class));
    }

    public JsonNode listClaimsForMember(String memberCpid) {
        String url = UriComponentsBuilder.fromHttpUrl(baseUrl + "/internal/v1/coverage/claims")
                .queryParam("member_cpid", memberCpid)
                .toUriString();
        return extractData(restTemplate.getForEntity(url, JsonNode.class));
    }

    public JsonNode listContributionsForMember(String memberCpid) {
        String url = UriComponentsBuilder.fromHttpUrl(baseUrl + "/internal/v1/coverage/contributions")
                .queryParam("member_cpid", memberCpid)
                .toUriString();
        return extractData(restTemplate.getForEntity(url, JsonNode.class));
    }

    public JsonNode listPreauthsForMember(String memberCpid) {
        String url = UriComponentsBuilder.fromHttpUrl(baseUrl + "/internal/v1/coverage/preauths")
                .queryParam("member_cpid", memberCpid)
                .toUriString();
        return extractData(restTemplate.getForEntity(url, JsonNode.class));
    }

    public JsonNode listAppealsForAppellant(String appellantId) {
        String url = UriComponentsBuilder.fromHttpUrl(baseUrl + "/internal/v1/coverage/appeals")
                .queryParam("appellant_id", appellantId)
                .toUriString();
        return extractData(restTemplate.getForEntity(url, JsonNode.class));
    }

    public JsonNode createAppeal(Map<String, Object> body) {
        String url = baseUrl + "/internal/v1/coverage/appeals";
        return extractData(restTemplate.postForEntity(url, body, JsonNode.class));
    }

    public JsonNode createPreauth(Map<String, Object> body) {
        String url = baseUrl + "/internal/v1/coverage/preauth";
        log.info("COVERAGE: Creating preauth");
        return extractData(restTemplate.postForEntity(url, body, JsonNode.class));
    }

    public JsonNode getPreauth(String id) {
        String url = baseUrl + "/internal/v1/coverage/preauth/" + id;
        return extractData(restTemplate.getForEntity(url, JsonNode.class));
    }

    public JsonNode enrollMember(Map<String, Object> body) {
        String url = baseUrl + "/internal/v1/coverage/members";
        log.info("COVERAGE: Enrolling member");
        return extractData(restTemplate.postForEntity(url, body, JsonNode.class));
    }

    public JsonNode listMembers(String planId) {
        String url = UriComponentsBuilder.fromHttpUrl(baseUrl + "/internal/v1/coverage/members")
                .queryParam("plan_id", planId)
                .toUriString();
        return extractData(restTemplate.getForEntity(url, JsonNode.class));
    }

    public JsonNode listRemittances() {
        String url = baseUrl + "/internal/v1/coverage/remittances";
        return extractData(restTemplate.getForEntity(url, JsonNode.class));
    }

    private JsonNode extractData(ResponseEntity<JsonNode> response) {
        if (response.getBody() != null && response.getBody().has("data")) return response.getBody().get("data");
        return response.getBody();
    }
}
