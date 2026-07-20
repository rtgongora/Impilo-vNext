package zw.gov.mohcc.impilo.tshepo.identity.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Security configuration for TSHEPO Identity service.
 *
 * <p>All /v1/identity/** endpoints require a valid JWT bearer token (issued by
 * Keycloak via Envoy). Actuator health/info/prometheus and OpenAPI docs are
 * open for infrastructure probes and developer tooling.</p>
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // Actuator probes
                .requestMatchers("/actuator/health", "/actuator/info", "/actuator/prometheus").permitAll()
                // OpenAPI
                .requestMatchers("/v3/api-docs/**", "/swagger-ui/**").permitAll()
                // Token introspection is an internal service-to-service call (the PDP resolves a
                // WORK_CONTEXT duty token here). It only reports validity + the context claims to a
                // caller that already holds the token, so it adds no disclosure beyond possession.
                // Not publicly routed (envoy fronts the BFF only). See tshepo-authz binding.
                .requestMatchers("/v1/identity/tokens/introspect").permitAll()
                // Everything else requires JWT
                .anyRequest().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> {}));

        return http.build();
    }
}
