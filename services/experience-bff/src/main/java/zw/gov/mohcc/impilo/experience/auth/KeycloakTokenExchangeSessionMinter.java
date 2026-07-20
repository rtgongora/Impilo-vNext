package zw.gov.mohcc.impilo.experience.auth;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

/**
 * {@link SessionMinter} backed by Keycloak's OAuth2 token-exchange grant
 * ({@code urn:ietf:params:oauth:grant-type:token-exchange}).
 *
 * <p>Only present when {@code impilo.auth.biometric-login.enabled=true}. It obtains a
 * token for a resolved person WITHOUT a password by authenticating as the BFF
 * service-account client (which must hold the {@code token-exchange} +
 * {@code impersonation} permissions) and passing {@code requested_subject=<subject>}.
 * The person never enters a credential — their identity was already proven by a
 * single strong 1:N biometric match, gated in the controller.</p>
 *
 * <p>Fail-closed: any non-2xx, empty body, or transport error returns {@code null}
 * so the caller can never mint a session on a failed exchange.</p>
 */
@Component
@ConditionalOnProperty(name = "impilo.auth.biometric-login.enabled", havingValue = "true")
public class KeycloakTokenExchangeSessionMinter implements SessionMinter {

    private static final Logger log = LoggerFactory.getLogger(KeycloakTokenExchangeSessionMinter.class);

    private static final String TOKEN_EXCHANGE_GRANT = "urn:ietf:params:oauth:grant-type:token-exchange";
    private static final String ACCESS_TOKEN_TYPE = "urn:ietf:params:oauth:token-type:access_token";

    private final RestTemplate restTemplate;

    @Value("${KEYCLOAK_URL:http://localhost:8080}")
    private String keycloakUrl;

    @Value("${KEYCLOAK_REALM:impilo}")
    private String realm;

    /** BFF service-account client that holds the token-exchange / impersonation permission. */
    @Value("${impilo.auth.biometric-login.token-exchange-client-id:${KEYCLOAK_BACKEND_CLIENT_ID:impilo-backend}}")
    private String clientId;

    @Value("${impilo.auth.biometric-login.token-exchange-client-secret:${KEYCLOAK_BACKEND_SECRET:impilo-backend-secret}}")
    private String clientSecret;

    /** Optional audience/scope the minted token should carry (matches the ROPC login scope). */
    @Value("${impilo.auth.biometric-login.token-exchange-scope:openid profile email impilo-trust-headers}")
    private String scope;

    public KeycloakTokenExchangeSessionMinter(@Qualifier("serviceRestTemplate") RestTemplate serviceRestTemplate) {
        this.restTemplate = serviceRestTemplate;
    }

    @Override
    public KeycloakTokens mintForSubject(String subjectUsernameOrId) {
        if (subjectUsernameOrId == null || subjectUsernameOrId.isBlank()) {
            return null;
        }
        try {
            String tokenUrl = keycloakUrl + "/realms/" + realm + "/protocol/openid-connect/token";

            MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
            form.add("grant_type", TOKEN_EXCHANGE_GRANT);
            form.add("client_id", clientId);
            form.add("client_secret", clientSecret);
            form.add("requested_subject", subjectUsernameOrId);
            form.add("requested_token_type", ACCESS_TOKEN_TYPE);
            if (scope != null && !scope.isBlank()) {
                form.add("scope", scope.trim());
            }

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            ResponseEntity<JsonNode> response = restTemplate.exchange(
                    tokenUrl, HttpMethod.POST,
                    new HttpEntity<>(form, headers),
                    JsonNode.class);

            if (response.getStatusCode().is2xxSuccessful()
                    && response.getBody() != null
                    && response.getBody().hasNonNull("access_token")) {
                log.info("BIOMETRIC-LOGIN: token exchange succeeded for a resolved subject (no password)");
                return new KeycloakTokens(response.getBody());
            }
            log.warn("BIOMETRIC-LOGIN: token exchange returned no access_token — fail closed");
            return null;
        } catch (org.springframework.web.client.HttpClientErrorException e) {
            log.warn("BIOMETRIC-LOGIN: token exchange rejected ({}) — fail closed", e.getStatusCode());
            return null;
        } catch (Exception e) {
            log.warn("BIOMETRIC-LOGIN: token exchange failed ({}) — fail closed", e.getMessage());
            return null;
        }
    }
}
