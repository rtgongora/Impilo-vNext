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
    @org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
            name = "impilo.security.disable-oauth-for-tests", havingValue = "false", matchIfMissing = true)
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
                // Everything else requires JWT authentication
                .anyRequest().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> {}));

        return http.build();
    }

    /**
     * Test-only chain (estate idiom): opened by disable-oauth-for-tests so the
     * golden contract suite can drive v1.1 probe endpoints without a JWT issuer.
     * The production chain (with oauth2ResourceServer/JwtDecoder) is disabled under
     * this flag, so no JwtDecoder bean is required. Never active in production.
     */
    @Bean
    @org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
            name = "impilo.security.disable-oauth-for-tests", havingValue = "true")
    public SecurityFilterChain testFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/internal/v1/**", "/external/v1/**").permitAll()
                .anyRequest().authenticated());
        return http.build();
    }
}
