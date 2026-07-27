package zw.gov.mohcc.impilo.experience.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configuration for the BFF's own service workload credential (OAuth2 client_credentials).
 *
 * <p>Bound from {@code impilo.auth.service-token.*} in {@code application.yml}, which in turn
 * reads the estate-standard env vars: {@code KEYCLOAK_BACKEND_CLIENT_ID} (default
 * {@code impilo-backend}) and {@code KEYCLOAK_BACKEND_SECRET} (helm: k8s secret
 * {@code impilo-app-secrets} / key {@code keycloak-backend-secret}).</p>
 *
 * <p>The secret is deliberately allowed to be absent (preview environments): the provider then
 * degrades to "no token" with an honest {@code SERVICE_TOKEN_MINT_FAILED} WARN instead of
 * failing requests.</p>
 */
@ConfigurationProperties(prefix = "impilo.auth.service-token")
public record ServiceTokenProperties(
        @DefaultValue("true") boolean enabled,
        String tokenUri,
        String clientId,
        String clientSecret,
        String scope) {

    public ServiceTokenProperties {
        if (tokenUri == null || tokenUri.isBlank()) {
            tokenUri = "http://localhost:8080/realms/impilo/protocol/openid-connect/token";
        }
        if (clientId == null || clientId.isBlank()) {
            clientId = "impilo-backend";
        }
    }

    /** True when a client secret is configured — the precondition for attempting a mint. */
    public boolean hasSecret() {
        return clientSecret != null && !clientSecret.isBlank();
    }
}
