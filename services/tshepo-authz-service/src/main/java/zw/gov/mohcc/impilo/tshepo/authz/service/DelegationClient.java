package zw.gov.mohcc.impilo.tshepo.authz.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import zw.gov.mohcc.impilo.tshepo.authz.config.AuthzProperties;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Resolves a delegated "actor acting for subject" relationship from Mvumo (the delegation
 * act-of-record) for the PolicyEngine's Step 4.5. Returns the relationship facts; the PDP decides.
 * Fail-closed: on any error the caller treats the request as not delegated-authorised (DENY).
 */
@Service
public class DelegationClient {

    private static final Logger log = LoggerFactory.getLogger(DelegationClient.class);

    private final RestTemplate restTemplate;
    private final AuthzProperties properties;

    public DelegationClient(RestTemplate restTemplate, AuthzProperties properties) {
        this.restTemplate = restTemplate;
        this.properties = properties;
    }

    public DelegationResolution resolve(UUID tenantId, String delegateActorId, String subjectRef) {
        String url = UriComponentsBuilder.fromHttpUrl(properties.getMvumoServiceUrl())
                .path("/internal/v1/mvumo/delegations/resolve")
                .queryParam("delegate", delegateActorId)
                .queryParam("subject", subjectRef)
                .build().encode().toUriString();
        HttpHeaders headers = new HttpHeaders();
        if (tenantId != null) {
            headers.set("X-Tenant-ID", tenantId.toString());
        }
        ResponseEntity<JsonNode> response =
                restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), JsonNode.class);
        JsonNode body = response.getBody();
        JsonNode data = (body != null && body.has("data")) ? body.get("data") : body;
        if (data == null || !data.path("active").asBoolean(false)) {
            return DelegationResolution.inactive();
        }
        List<String> scope = new ArrayList<>();
        if (data.has("scope") && data.get("scope").isArray()) {
            data.get("scope").forEach(n -> scope.add(n.asText()));
        }
        int floor = data.path("assuranceFloor").asInt(0);
        log.debug("Delegation resolved: delegate={} subject={} floor={} scope={}",
                delegateActorId, subjectRef, floor, scope);
        return new DelegationResolution(true, scope, floor, data.path("relationshipType").asText(null));
    }
}
