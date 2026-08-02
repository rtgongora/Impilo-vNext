package zw.gov.mohcc.impilo.shared.auth;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;
import java.util.Objects;

/**
 * Rejects a token that was not minted for this service.
 *
 * <p>Without this, a resource server validating only signature and issuer accepts <em>any</em>
 * token the realm issued — a token minted for one service is accepted by every other. That is
 * cross-service token replay, and it is what "JWT audience validation: ABSENT" costs.</p>
 *
 * <h2>This is one half of a control</h2>
 *
 * <p>The other half is a Keycloak {@code oidc-audience-mapper}, without which tokens carry no
 * {@code aud} claim at all. The realm currently has none, on any client or client scope — so
 * enabling this validator before provisioning mappers would reject every token in the estate.
 * {@code scripts/keycloak/provision-workload-clients.sh} creates them; this validator is
 * <strong>opt-in per service</strong> so the two can be sequenced.</p>
 *
 * <p>An empty configured audience means "not yet enforced" and the validator passes, rather than
 * failing closed on every request. That is deliberate: a service that has not been told its
 * audience has not opted in, and silently rejecting all its traffic would be a self-inflicted
 * outage rather than a security control. The moment an audience IS configured, a token lacking it
 * is refused.</p>
 */
public class WorkloadAudienceValidator implements OAuth2TokenValidator<Jwt> {

    private final String requiredAudience;

    public WorkloadAudienceValidator(String requiredAudience) {
        this.requiredAudience = requiredAudience == null ? "" : requiredAudience.trim();
    }

    /** Whether this validator will actually reject anything. */
    public boolean isEnforcing() {
        return !requiredAudience.isEmpty();
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt token) {
        if (!isEnforcing()) {
            return OAuth2TokenValidatorResult.success();
        }
        List<String> audiences = token.getAudience();
        if (audiences != null && audiences.stream().filter(Objects::nonNull)
                .anyMatch(requiredAudience::equals)) {
            return OAuth2TokenValidatorResult.success();
        }
        // The message names the EXPECTED audience only. Echoing the presented audiences back
        // would tell an attacker which services a stolen token is accepted by.
        return OAuth2TokenValidatorResult.failure(new OAuth2Error(
                "invalid_token",
                "The required audience " + requiredAudience + " is missing",
                "https://tools.ietf.org/html/rfc6750#section-3.1"));
    }
}
