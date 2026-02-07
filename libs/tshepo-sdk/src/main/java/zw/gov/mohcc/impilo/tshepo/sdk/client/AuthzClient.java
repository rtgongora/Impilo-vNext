package zw.gov.mohcc.impilo.tshepo.sdk.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import zw.gov.mohcc.impilo.tshepo.contracts.dto.AuthzRequest;
import zw.gov.mohcc.impilo.tshepo.contracts.dto.AuthzResponse;
import zw.gov.mohcc.impilo.tshepo.contracts.dto.ConsentDecision;
import zw.gov.mohcc.impilo.tshepo.contracts.enums.Verdict;

import java.util.UUID;

/**
 * Client SDK for calling TSHEPO decision endpoints from downstream services.
 *
 * <p>Used when a service needs to make a secondary authorization check
 * (e.g., consent evaluation, break-glass validation) beyond what Envoy ext_authz
 * handles at the gateway level.</p>
 *
 * <p>Configuration:</p>
 * <pre>{@code
 * tshepo.sdk.authz-url=http://tshepo-authz-service:8081
 * tshepo.sdk.consent-url=http://tshepo-consent-service:8182
 * }</pre>
 */
public class AuthzClient {

    private static final Logger log = LoggerFactory.getLogger(AuthzClient.class);

    private final RestClient authzClient;
    private final RestClient consentClient;
    private final ObjectMapper objectMapper;

    public AuthzClient(String authzBaseUrl, String consentBaseUrl, ObjectMapper objectMapper) {
        this.authzClient = RestClient.builder()
                .baseUrl(authzBaseUrl)
                .build();
        this.consentClient = RestClient.builder()
                .baseUrl(consentBaseUrl)
                .build();
        this.objectMapper = objectMapper;
    }

    /**
     * Check authorization for an action (bypasses Envoy — for internal secondary checks).
     */
    public AuthzResponse checkAuthorization(AuthzRequest request) {
        try {
            return authzClient.post()
                    .uri("/v1/authorize")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(AuthzResponse.class);
        } catch (Exception e) {
            log.error("TSHEPO authz check failed, defaulting to DENY: {}", e.getMessage());
            return AuthzResponse.deny("AUTHZ_UNAVAILABLE",
                    "Authorization service unavailable — default deny", 100);
        }
    }

    /**
     * Evaluate consent for a specific patient + actor + purpose combination.
     */
    public ConsentDecision evaluateConsent(UUID tenantId, String actorId,
                                            String subjectRef, String purpose, String scope) {
        try {
            return consentClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/v1/consent/evaluate")
                            .queryParam("tenantId", tenantId)
                            .queryParam("actorId", actorId)
                            .queryParam("subjectRef", subjectRef)
                            .queryParam("purpose", purpose)
                            .queryParam("scope", scope)
                            .build())
                    .retrieve()
                    .body(ConsentDecision.class);
        } catch (Exception e) {
            log.error("Consent evaluation failed, defaulting to deny: {}", e.getMessage());
            return ConsentDecision.deny("CONSENT_SERVICE_UNAVAILABLE");
        }
    }

    /**
     * Quick check: is this request allowed?
     */
    public boolean isAllowed(AuthzRequest request) {
        AuthzResponse response = checkAuthorization(request);
        return response != null && response.verdict() == Verdict.ALLOW;
    }
}
