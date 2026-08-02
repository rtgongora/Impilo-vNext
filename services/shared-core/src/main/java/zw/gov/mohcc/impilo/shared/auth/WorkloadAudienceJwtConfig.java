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
 * <p><strong>Opt-in by property.</strong> The bean only exists when
 * {@code impilo.s2s.required-audience} is set. Until Keycloak actually mints an {@code aud} claim
 * for a caller, switching this on rejects that caller — so the ordering is: provision the audience
 * mapper, verify a real token carries {@code aud}, and only then set this property. Reversing those
 * two steps rejects every call.</p>
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
