package zw.gov.mohcc.impilo.tshepo.authz.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Spring Security configuration for the TSHEPO Authz service.
 *
 * <p>The {@code /v1/authorize} endpoint is permitAll because Envoy calls it
 * before any authentication has been verified (that IS the authentication
 * check). All management endpoints (step-up, break-glass, device, policy)
 * require a valid JWT.</p>
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
                // ext_authz endpoint — Envoy calls this unauthenticated
                .requestMatchers("/v1/authorize", "/v1/authorize/**").permitAll()
                // Actuator probes
                .requestMatchers("/actuator/health", "/actuator/info", "/actuator/prometheus").permitAll()
                // OpenAPI / Swagger
                .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                // The error dispatch must be permitted: without this, any controller
                // exception re-enters the chain as an unauthenticated /error request
                // and Http403ForbiddenEntryPoint masks the real failure as an
                // empty-body 403 (the "silent 403" defect).
                .requestMatchers("/error").permitAll()
                // Everything else requires JWT authentication
                .anyRequest().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> {}));

        return http.build();
    }
}
