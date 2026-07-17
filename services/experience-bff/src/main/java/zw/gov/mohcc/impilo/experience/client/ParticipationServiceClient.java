package zw.gov.mohcc.impilo.experience.client;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;

import java.util.Map;
import java.util.UUID;

/**
 * HTTP client for participation-service — citizen Get-Involved &amp; co-design.
 *
 * <p>The public "Get Involved" lane has no caller trust context (Envoy strips it), so headers
 * are synthesized here exactly like {@code RitoServiceClient} / {@code DaidzaiServiceClient}.
 * Only tenant/pod/request context plus an optional BFF service-account bearer are sent —
 * never an actor.</p>
 */
@Component
public class ParticipationServiceClient {

    private static final Logger log = LoggerFactory.getLogger(ParticipationServiceClient.class);
    private static final String PUBLIC_API = "/v1/public/participation";
    private static final String PUBLIC_DEFAULT_TENANT = "00000000-0000-0000-0000-000000000001";

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public ParticipationServiceClient(RestTemplate serviceRestTemplate,
                                      ServiceClientConfig.ServiceEndpoints endpoints) {
        this.restTemplate = serviceRestTemplate;
        this.baseUrl = endpoints.participationBaseUrl();
    }

    public JsonNode createPublicContribution(Map<String, Object> body, String serviceAccountBearer) {
        return publicExchange(HttpMethod.POST, baseUrl + PUBLIC_API + "/contributions", body, serviceAccountBearer);
    }

    public JsonNode getPublicContributionStatus(String claimCode, String serviceAccountBearer) {
        return publicExchange(HttpMethod.GET, baseUrl + PUBLIC_API + "/contributions/" + claimCode,
                null, serviceAccountBearer);
    }

    public JsonNode getPublicBoard(String serviceAccountBearer) {
        return publicExchange(HttpMethod.GET, baseUrl + PUBLIC_API + "/board", null, serviceAccountBearer);
    }

    public JsonNode supportPublic(String contributionId, Map<String, Object> body, String serviceAccountBearer) {
        return publicExchange(HttpMethod.POST, baseUrl + PUBLIC_API + "/board/" + contributionId + "/support",
                body, serviceAccountBearer);
    }

    public JsonNode getPublicCohorts(String serviceAccountBearer) {
        return publicExchange(HttpMethod.GET, baseUrl + PUBLIC_API + "/cohorts", null, serviceAccountBearer);
    }

    public JsonNode enrollPublic(String cohortId, Map<String, Object> body, String serviceAccountBearer) {
        return publicExchange(HttpMethod.POST, baseUrl + PUBLIC_API + "/cohorts/" + cohortId + "/enroll",
                body, serviceAccountBearer);
    }

    private JsonNode publicExchange(HttpMethod method, String url, Object body, String bearer) {
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.set(CompanionHeaders.TENANT_ID, PUBLIC_DEFAULT_TENANT);
        headers.set(CompanionHeaders.POD_ID, "national-spine");
        headers.set(CompanionHeaders.ACTOR_ID, "public-gateway");
        headers.set(CompanionHeaders.ACTOR_TYPE, "SYSTEM");
        headers.set(CompanionHeaders.PURPOSE_OF_USE, "PARTICIPATION_INTAKE");
        headers.set(CompanionHeaders.CORRELATION_ID, UUID.randomUUID().toString());
        headers.set(CompanionHeaders.REQUEST_ID, UUID.randomUUID().toString());
        headers.set(CompanionHeaders.IDEMPOTENCY_KEY, UUID.randomUUID().toString());
        if (bearer != null && !bearer.isBlank()) {
            headers.set(CompanionHeaders.AUTHORIZATION, CompanionHeaders.BEARER_PREFIX + bearer);
        }
        log.debug("PARTICIPATION public {} {}", method, url);
        ResponseEntity<JsonNode> response = restTemplate.exchange(
                url, method, new HttpEntity<>(body, headers), JsonNode.class);
        return response.getBody();
    }
}
