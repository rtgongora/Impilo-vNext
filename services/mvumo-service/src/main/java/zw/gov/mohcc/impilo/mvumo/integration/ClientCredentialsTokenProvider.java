package zw.gov.mohcc.impilo.mvumo.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.mvumo.config.MvumoTshepoClientCredentialsProperties;

import java.time.Instant;
import java.util.Optional;

/**
 * Caches an access token from a standard Keycloak-style token endpoint (client_credentials).
 */
@Component
public class ClientCredentialsTokenProvider {

    private static final Logger log = LoggerFactory.getLogger(ClientCredentialsTokenProvider.class);

    private final MvumoTshepoClientCredentialsProperties props;
    private final ObjectMapper objectMapper;
    /** Plain {@link RestTemplate} to avoid infinite interceptor loops. */
    private final RestTemplate tokenClient = new RestTemplate();

    private String cached;
    private volatile Instant exp = Instant.EPOCH;

    public ClientCredentialsTokenProvider(
            MvumoTshepoClientCredentialsProperties props, ObjectMapper objectMapper) {
        this.props = props;
        this.objectMapper = objectMapper;
    }

    /**
     * Returns a bearer value when client-credentials is enabled and fully configured; empty otherwise.
     */
    public Optional<String> getAccessToken() {
        if (!props.isEnabled() || !props.isConfigured()) {
            return Optional.empty();
        }
        if (cached != null && Instant.now().isBefore(exp)) {
            return Optional.of(cached);
        }
        synchronized (this) {
            if (cached != null && Instant.now().isBefore(exp)) {
                return Optional.of(cached);
            }
            return refresh();
        }
    }

    private Optional<String> refresh() {
        try {
            HttpHeaders h = new HttpHeaders();
            h.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
            form.add("grant_type", "client_credentials");
            form.add("client_id", props.getClientId());
            form.add("client_secret", props.getClientSecret());
            if (props.getScope() != null && !props.getScope().isBlank()) {
                form.add("scope", props.getScope());
            }
            ResponseEntity<String> r =
                    tokenClient.postForEntity(props.getTokenUri(), new HttpEntity<>(form, h), String.class);
            if (!r.getStatusCode().is2xxSuccessful() || r.getBody() == null) {
                log.warn("Client-credentials token request failed: status={}", r.getStatusCode());
                return Optional.empty();
            }
            JsonNode n = objectMapper.readTree(r.getBody());
            String access = n.path("access_token").asText(null);
            if (access == null) {
                log.warn("Token response has no access_token");
                return Optional.empty();
            }
            int ttlSec = n.path("expires_in").asInt(300);
            cached = access;
            exp = Instant.now().plusSeconds(Math.max(30, ttlSec - 15));
            log.debug("Obtained client-credentials token (exp approx {}s from now)", ttlSec);
            return Optional.of(cached);
        } catch (Exception e) {
            log.warn("Client-credentials token request failed: {}", e.getMessage());
            return Optional.empty();
        }
    }
}
