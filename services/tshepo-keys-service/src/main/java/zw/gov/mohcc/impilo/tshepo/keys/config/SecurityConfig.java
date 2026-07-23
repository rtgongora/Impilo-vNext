package zw.gov.mohcc.impilo.tshepo.keys.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
            @Value("${impilo.security.disable-oauth-for-tests:false}") boolean disableOauthForTests)
            throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));

        // Platform convention (same as pct/oros; the fhir-gateway had this exact gap until
        // Wave A4): environments fronting all traffic with Envoy ext_authz (preview/tests)
        // may disable the in-service OAuth layer. Previously this service hard-wired JWT and
        // ignored the flag — on preview every service-to-service signing call 401'd, so
        // OF-B2 prescription signing (fail-closed by design) could never succeed there.
        if (!disableOauthForTests) {
            http.authorizeHttpRequests(auth -> auth
                // JWKS endpoint: public — any service can fetch public keys
                .requestMatchers(HttpMethod.GET, "/v1/keys/jwks").permitAll()
                // Actuator probes
                .requestMatchers("/actuator/health", "/actuator/info", "/actuator/prometheus").permitAll()
                // OpenAPI
                .requestMatchers("/v3/api-docs/**", "/swagger-ui/**").permitAll()
                // Signing endpoint: internal service-to-service only (requires JWT)
                .requestMatchers("/v1/sign/**").authenticated()
                // Key management and certificate endpoints: admin only
                .requestMatchers("/v1/keys/**").hasAnyRole("ADMIN", "KEY_ADMIN")
                .requestMatchers("/v1/certificates/**").hasAnyRole("ADMIN", "KEY_ADMIN")
                // Everything else requires authentication
                .anyRequest().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> {}));
        } else {
            http.authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        }

        return http.build();
    }
}
