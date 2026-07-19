package zw.gov.mohcc.impilo.experience.client;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;

import java.util.Map;

/**
 * HTTP client for the MVUMO consent orchestration service.
 */
@Component
public class MvumoServiceClient {

    private static final Logger log = LoggerFactory.getLogger(MvumoServiceClient.class);

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public MvumoServiceClient(RestTemplate serviceRestTemplate,
                              ServiceClientConfig.ServiceEndpoints endpoints) {
        this.restTemplate = serviceRestTemplate;
        this.baseUrl = endpoints.mvumoBaseUrl();
    }

    public JsonNode createConsentRequest(Map<String, Object> body) {
        String url = baseUrl + "/internal/v1/mvumo/consent-requests";
        log.info("MVUMO: creating teleconsult consent request");
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body, JsonNode.class);
        return extractData(response);
    }

    /**
     * Create an act-on-behalf delegation relationship (guardian / caregiver — CJ14/CJ15).
     * The caller's X-Actor-ID / X-Tenant-ID are forwarded by the shared trust interceptor;
     * mvumo (the act-of-record) requires an authenticated actor to attribute the grant.
     */
    public JsonNode createDelegation(Map<String, Object> body) {
        String url = baseUrl + "/internal/v1/mvumo/delegations";
        log.info("MVUMO: creating delegation relationship type={}", body.get("relationshipType"));
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body, JsonNode.class);
        return extractData(response);
    }

    public JsonNode listTemplates() {
        String url = baseUrl + "/internal/v1/mvumo/templates";
        log.info("MVUMO: listing consent templates");
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    public JsonNode createTemplate(Map<String, Object> body) {
        String url = baseUrl + "/internal/v1/mvumo/templates";
        log.info("MVUMO: creating consent template");
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body, JsonNode.class);
        return extractData(response);
    }

    public JsonNode updateTemplate(String templateId, Map<String, Object> body) {
        String url = baseUrl + "/internal/v1/mvumo/templates/" + templateId;
        log.info("MVUMO: updating consent template {}", templateId);
        ResponseEntity<JsonNode> response = restTemplate.exchange(url, HttpMethod.PUT, new HttpEntity<>(body), JsonNode.class);
        return extractData(response);
    }

    public JsonNode createRemoteSession(Map<String, Object> body) {
        String url = baseUrl + "/internal/v1/mvumo/remote-sessions";
        log.info("MVUMO: creating remote consent session");
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body, JsonNode.class);
        return extractData(response);
    }

    public JsonNode getRemoteSession(String sessionId) {
        String url = baseUrl + "/internal/v1/mvumo/remote-sessions/" + sessionId;
        log.debug("MVUMO: get remote session {}", sessionId);
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    public JsonNode verifyRemoteSession(String sessionId, Map<String, Object> body) {
        String url = baseUrl + "/internal/v1/mvumo/remote-sessions/" + sessionId + "/verify";
        log.info("MVUMO: verify remote session {}", sessionId);
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, new HttpEntity<>(body), JsonNode.class);
        return extractData(response);
    }

    public JsonNode grantRemoteSession(String sessionId, Map<String, Object> body) {
        String url = baseUrl + "/internal/v1/mvumo/remote-sessions/" + sessionId + "/grant";
        log.info("MVUMO: grant remote session {}", sessionId);
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, new HttpEntity<>(body), JsonNode.class);
        return extractData(response);
    }

    public JsonNode refuseRemoteSession(String sessionId, Map<String, Object> body) {
        String url = baseUrl + "/internal/v1/mvumo/remote-sessions/" + sessionId + "/refuse";
        log.info("MVUMO: refuse remote session {}", sessionId);
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, new HttpEntity<>(body), JsonNode.class);
        return extractData(response);
    }

    public JsonNode withdrawRemoteSession(String sessionId, Map<String, Object> body) {
        String url = baseUrl + "/internal/v1/mvumo/remote-sessions/" + sessionId + "/withdraw";
        log.info("MVUMO: withdraw remote session {}", sessionId);
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, new HttpEntity<>(body), JsonNode.class);
        return extractData(response);
    }

    public JsonNode getConsentRequest(String consentRequestId) {
        String url = baseUrl + "/internal/v1/mvumo/consent-requests/" + consentRequestId;
        log.debug("MVUMO: get consent request {}", consentRequestId);
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    public JsonNode getConsentProof(String consentRequestId) {
        String url = baseUrl + "/internal/v1/mvumo/consents/" + consentRequestId + "/proof";
        log.debug("MVUMO: get consent proof {}", consentRequestId);
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    public JsonNode provideConsentExplanation(String consentRequestId, Map<String, Object> body) {
        String url = baseUrl + "/internal/v1/mvumo/consent-requests/" + consentRequestId + "/explanation";
        log.info("MVUMO: provide explanation for consent {}", consentRequestId);
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body != null ? body : Map.of(), JsonNode.class);
        return extractData(response);
    }

    public JsonNode verifyConsentIdentity(String consentRequestId, Map<String, Object> body) {
        String url = baseUrl + "/internal/v1/mvumo/consent-requests/" + consentRequestId + "/verify-identity";
        log.info("MVUMO: verify identity for consent {}", consentRequestId);
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body != null ? body : Map.of(), JsonNode.class);
        return extractData(response);
    }

    public JsonNode grantConsentRequest(String consentRequestId, Map<String, Object> body) {
        String url = baseUrl + "/internal/v1/mvumo/consent-requests/" + consentRequestId + "/grant";
        log.info("MVUMO: grant consent {}", consentRequestId);
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body != null ? body : Map.of(), JsonNode.class);
        return extractData(response);
    }

    public JsonNode refuseConsentRequest(String consentRequestId, Map<String, Object> body) {
        String url = baseUrl + "/internal/v1/mvumo/consent-requests/" + consentRequestId + "/refuse";
        log.info("MVUMO: refuse consent {}", consentRequestId);
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body != null ? body : Map.of(), JsonNode.class);
        return extractData(response);
    }

    // ── Legal/platform agreement acceptance (Privacy Policy, Terms of Use) ──────────────

    public JsonNode acceptLegalAgreement(Map<String, Object> body) {
        String url = baseUrl + "/internal/v1/mvumo/legal-agreements/accept";
        log.info("MVUMO: accept legal agreement {}", body.get("documentType"));
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, new HttpEntity<>(body), JsonNode.class);
        return extractData(response);
    }

    public JsonNode listLegalAgreements(String actorId) {
        String url = baseUrl + "/internal/v1/mvumo/legal-agreements/actors/" + actorId;
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    public JsonNode withdrawLegalAgreement(String agreementId) {
        String url = baseUrl + "/internal/v1/mvumo/legal-agreements/" + agreementId + "/withdraw";
        log.info("MVUMO: withdraw legal agreement {}", agreementId);
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, null, JsonNode.class);
        return extractData(response);
    }

    /**
     * Delegated relationships where {@code actorId} is the delegate (i.e. the people this person
     * may act for). Read-only composition surface for the person health wallet's dependants card —
     * Mvumo remains the sovereign owner of the delegation records.
     */
    public JsonNode listDelegationsForDelegate(String actorId) {
        String url = baseUrl + "/internal/v1/mvumo/delegations/delegate/" + actorId;
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    /**
     * Delegated relationships where {@code subjectRef} is the subject (i.e. who may act for this
     * person). Read-only composition surface for the wallet's "who can act for me" card.
     */
    public JsonNode listDelegationsForSubject(String subjectRef) {
        String url = baseUrl + "/internal/v1/mvumo/delegations/subject/" + subjectRef;
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    private JsonNode extractData(ResponseEntity<JsonNode> response) {
        if (response.getBody() != null && response.getBody().has("data")) {
            return response.getBody().get("data");
        }
        return response.getBody();
    }
}
