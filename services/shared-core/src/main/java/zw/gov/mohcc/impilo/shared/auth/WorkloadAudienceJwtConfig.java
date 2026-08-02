package zw.gov.mohcc.impilo.shared.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

/**
 * Adds audience validation to a service's resource server.
 *
 * <p>Spring Boot's auto-configured decoder validates signature, issuer and expiry but <em>not</em>
 * audience, so a token minted for any client in the realm is accepted by every service that trusts
 * the issuer — cross-service token replay. This replaces the decoder with one that also requires
 * the service's own audience.</p>
 *
 * <p><strong>Opt-in — but the gate is the validator, not the annotation.</strong> The sentence
 * that used to sit here said "the bean only exists when {@code impilo.s2s.required-audience} is
 * set". That is <em>false</em> wherever the property is declared as
 * {@code ${IMPILO_S2S_REQUIRED_AUDIENCE:}}: an unset environment variable binds to the empty
 * string, and {@code @ConditionalOnProperty} with no {@code havingValue} matches on any value that
 * is present and not {@code false} — the empty string is present. So the bean is constructed and
 * <strong>this decoder replaces the auto-configured one</strong> whenever the config is imported.</p>
 *
 * <p>What actually prevents an outage is {@link WorkloadAudienceValidator#isEnforcing()}: a blank
 * audience validates every token successfully. Both directions are pinned in
 * {@code WorkloadAudienceValidatorTest} — blank passes, configured enforces — so the safety is
 * tested rather than incidental. The annotation is defence in depth; the blank check is the
 * control.</p>
 *
 * <p><strong>Two consequences when widening this beyond the CP7 cohort</strong> (only
 * {@code workforce-governance-service} imports it today):</p>
 * <ol>
 *   <li>Importing this config replaces the {@code JwtDecoder} even with no audience configured, so
 *       any service adopting it must genuinely want this decoder.</li>
 *   <li>It requires {@code jwk-set-uri} and {@code issuer-uri} to resolve. A service importing it
 *       without those fails to start — loud and fail-closed, which is the right direction, but it
 *       is a startup failure rather than the "inert until configured" the old wording implied.</li>
 * </ol>
 *
 * <p>Ordering still matters for the same reason as before: until Keycloak mints an {@code aud}
 * claim for a caller, setting a non-empty audience rejects that caller. Provision the mapper,
 * verify a real token carries {@code aud}, and only then set the property.</p>
 *
 * <p>The JWK set is fetched from {@code jwk-set-uri} rather than discovered from the issuer:
 * Keycloak advertises its public issuer even when reached in-cluster, and this estate's nodes
 * cannot reach their own public address, so issuer discovery would hang. The issuer is still
 * validated — it is simply asserted rather than dereferenced.</p>
 */
@Configuration
@ConditionalOnProperty(name = "impilo.s2s.required-audience")
public class WorkloadAudienceJwtConfig {

    @Bean
    public JwtDecoder jwtDecoder(
            @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}") String jwkSetUri,
            @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String issuerUri,
            @Value("${impilo.s2s.required-audience}") String requiredAudience) {

        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<Jwt>(
                // Keeps the default timestamp + issuer checks; the audience check is additive,
                // never a replacement for them.
                JwtValidators.createDefaultWithIssuer(issuerUri),
                new WorkloadAudienceValidator(requiredAudience)));
        return decoder;
    }
}
