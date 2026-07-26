package zw.gov.mohcc.impilo.pct.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.Optional;

/**
 * Caches a Keycloak {@code client_credentials} access token for pct-service's own outbound calls.
 *
 * <p>Some platform services in the mesh are OAuth2 resource servers that authenticate every caller,
 * including other services — the clinical knowledge platform is one. A service-originated call
 * therefore needs a token of its own; it cannot borrow the user's, because the request that
 * triggered it may have finished, may have been a Kafka event, or may be a scheduled job with no
 * user at all.</p>
 *
 * <p>Unconfigured is a supported state, not an error: the provider returns empty and callers
 * degrade explicitly rather than the service failing to start. What it must never do is fail
 * quietly in a way a caller reads as a clinical answer.</p>
 *
 * <p>Modelled on {@code mvumo-service}'s ClientCredentialsTokenProvider, which solved the same
 * problem for the TSHEPO call.</p>
 */
@Component
public class ServiceTokenProvider {

    private static final Logger log = LoggerFactory.getLogger(ServiceTokenProvider.class);

    /** Plain {@link RestTemplate}: the shared one may carry interceptors that would recurse here. */
    private final RestTemplate tokenClient = new RestTemplate();
    private final ObjectMapper objectMapper;

    private final String tokenUri;
    private final String clientId;
    private final String clientSecret;

    private String cached;
    private volatile Instant expiresAt = Instant.EPOCH;

    public ServiceTokenProvider(
            ObjectMapper objectMapper,
            @Value("${pct.integration.service-token.token-uri:${KEYCLOAK_URL:http://localhost:8080}/realms/${KEYCLOAK_REALM:impilo}/protocol/openid-connect/token}")
            String tokenUri,
            @Value("${pct.integration.service-token.client-id:${KEYCLOAK_BACKEND_CLIENT_ID:}}") String clientId,
            @Value("${pct.integration.service-token.client-secret:${KEYCLOAK_BACKEND_SECRET:}}") String clientSecret) {
        this.objectMapper = objectMapper;
        this.tokenUri = tokenUri;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
    }

    public boolean isConfigured() {
        return clientId != null && !clientId.isBlank() && clientSecret != null && !clientSecret.isBlank();
    }

    /** @return a bearer token value, or empty when unconfigured or the token endpoint is down */
    public Optional<String> accessToken() {
        if (!isConfigured()) {
            return Optional.empty();
        }
        if (cached != null && Instant.now().isBefore(expiresAt)) {
            return Optional.of(cached);
        }
        synchronized (this) {
            if (cached != null && Instant.now().isBefore(expiresAt)) {
                return Optional.of(cached);
            }
            return refresh();
        }
    }

    private Optional<String> refresh() {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
            form.add("grant_type", "client_credentials");
            form.add("client_id", clientId);
            form.add("client_secret", clientSecret);

            ResponseEntity<String> response =
                    tokenClient.postForEntity(tokenUri, new HttpEntity<>(form, headers), String.class);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                log.warn("Service token request failed: status={}", response.getStatusCode());
                return Optional.empty();
            }
            JsonNode node = objectMapper.readTree(response.getBody());
            String token = node.path("access_token").asText(null);
            if (token == null) {
                log.warn("Service token response carried no access_token");
                return Optional.empty();
            }
            int ttlSeconds = node.path("expires_in").asInt(300);
            cached = token;
            expiresAt = Instant.now().plusSeconds(Math.max(30, ttlSeconds - 15));
            return Optional.of(cached);
        } catch (Exception e) {
            log.warn("Service token request failed: {}", e.getMessage());
            return Optional.empty();
        }
    }
}
