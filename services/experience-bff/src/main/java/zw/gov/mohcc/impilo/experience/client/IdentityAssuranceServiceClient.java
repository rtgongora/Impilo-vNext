package zw.gov.mohcc.impilo.experience.client;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;

import java.util.Map;

/**
 * HTTP client for the identity-assurance-service — the canonical owner of identity assurance
 * (assurance level + upgrade-request workflow). Trust headers (tenant/actor) are forwarded by
 * the shared {@link ServiceClientConfig} interceptor, so the service resolves the caller's
 * identity itself. Replaces the BFF's previous fabricated assurance responses.
 */
@Component
public class IdentityAssuranceServiceClient {

    private static final Logger log = LoggerFactory.getLogger(IdentityAssuranceServiceClient.class);

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public IdentityAssuranceServiceClient(RestTemplate serviceRestTemplate,
                                          ServiceClientConfig.ServiceEndpoints endpoints) {
        this.restTemplate = serviceRestTemplate;
        this.baseUrl = endpoints.identityAssuranceBaseUrl();
    }

    /** The caller's assurance status (level, permissions, upgrade pathways). */
    public JsonNode getAssuranceStatus() {
        String url = baseUrl + "/internal/v1/assurance/status";
        log.info("IDENTITY-ASSURANCE: getting assurance status");
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    /** Raise an assurance upgrade request for the caller. */
    public JsonNode requestUpgrade(Map<String, Object> request) {
        String url = baseUrl + "/internal/v1/assurance/upgrade/request";
        log.info("IDENTITY-ASSURANCE: requesting assurance upgrade");
        ResponseEntity<JsonNode> response =
                restTemplate.postForEntity(url, new HttpEntity<>(request), JsonNode.class);
        return extractData(response);
    }

    /** Pending assurance upgrade requests for the caller's tenant (Trust Console queue). */
    public JsonNode listUpgradeRequests() {
        String url = baseUrl + "/internal/v1/assurance/upgrade/requests";
        log.info("IDENTITY-ASSURANCE: listing pending assurance upgrade requests");
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    /** Decide an assurance upgrade request ({@code {approve: boolean, reason: string}}). */
    public JsonNode decideUpgradeRequest(long id, Map<String, Object> body) {
        String url = baseUrl + "/internal/v1/assurance/upgrade/requests/" + id + "/decide";
        log.info("IDENTITY-ASSURANCE: deciding assurance upgrade request {}", id);
        ResponseEntity<JsonNode> response =
                restTemplate.postForEntity(url, new HttpEntity<>(body), JsonNode.class);
        return extractData(response);
    }

    /**
     * Record a CONTACT_VERIFIED attestation (R1 "Reachable" rung) for {@code accountId}
     * after the BFF completed the contact OTP check. Only the masked value crosses this
     * boundary — never the raw phone/email.
     *
     * <p>{@code overrideHeaders} lets the pre-auth registration flow supply a
     * service-account bearer + synthesized actor identity; when the inbound request is
     * authenticated (ATTACH), the shared trust-header interceptor forwards the caller's
     * own Authorization/actor headers over these values.</p>
     */
    public JsonNode recordContactVerified(String accountId, String channel, String maskedValue,
                                          org.springframework.http.HttpHeaders overrideHeaders) {
        String url = baseUrl + "/internal/v1/attestations/contact-verified";
        log.info("IDENTITY-ASSURANCE: recording CONTACT_VERIFIED attestation channel={}", channel);
        Map<String, Object> body = Map.of(
                "accountId", accountId,
                "channel", channel,
                "maskedValue", maskedValue,
                "method", "OTP");
        ResponseEntity<JsonNode> response =
                restTemplate.postForEntity(url, new HttpEntity<>(body, overrideHeaders), JsonNode.class);
        return extractData(response);
    }

    /**
     * Raise the caller's assurance level from an authoritative proofing outcome (Identity
     * Journey Doctrine §4). INTERNAL-only on the IA side — the mode is set explicitly here;
     * the caller's actor/tenant are forwarded by the shared trust-header interceptor, so IA
     * applies the raise to the acting citizen (never another actor).
     */
    public JsonNode recordProofingOutcome(String targetLevel, String provenance) {
        return recordProofingOutcome(targetLevel, provenance, null);
    }

    /**
     * Officer-witnessed variant: raise an <b>explicit</b> actor's assurance (the reviewed
     * citizen, not the acting officer). IA accepts the explicit actorId only because the
     * endpoint is INTERNAL-only and the raise is audited with its provenance.
     */
    public JsonNode recordProofingOutcome(String targetLevel, String provenance, String actorId) {
        String url = baseUrl + "/internal/v1/assurance/proofing-outcome";
        log.info("IDENTITY-ASSURANCE: recording proofing outcome level={} provenance={} explicitActor={}",
                targetLevel, provenance, actorId != null);
        org.springframework.http.HttpHeaders h = new org.springframework.http.HttpHeaders();
        h.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        h.set("X-Access-Mode", "INTERNAL");
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("targetLevel", targetLevel);
        body.put("provenance", provenance);
        if (actorId != null && !actorId.isBlank()) {
            body.put("actorId", actorId);
        }
        ResponseEntity<JsonNode> response =
                restTemplate.postForEntity(url, new HttpEntity<>(body, h), JsonNode.class);
        return extractData(response);
    }

    /** Contact-verification status of an account (verified contact + channels). */
    public JsonNode getContactVerification(String accountId) {
        String url = baseUrl + "/internal/v1/attestations/contact-verified/" + accountId;
        log.debug("IDENTITY-ASSURANCE: reading contact-verification status");
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
