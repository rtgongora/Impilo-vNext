package zw.gov.mohcc.impilo.msika.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import zw.gov.mohcc.impilo.shared.auth.TrustContextFilter;

/**
 * Msika security configuration implementing the Tshepo-Msika Handshake.
 *
 * Dual-mode access:
 *
 * INTERNAL MODE (platform services via Envoy):
 *   Envoy has already called TSHEPO ext_authz and validated the request.
 *   Trust headers are injected by Envoy. TrustContextFilter extracts them.
 *   JWT validation still applies (defense-in-depth).
 *
 * EXTERNAL MODE (3rd-party national service consumers):
 *   External app authenticates with Keycloak, receives a Tshepo-scoped JWT.
 *   JWT must contain the 'msika:read' or 'msika:write' scope.
 *   TrustContextFilter sets AccessMode.EXTERNAL so controllers can
 *   restrict write operations or apply rate limiting.
 *
 * Endpoint access matrix:
 *   /v1/products/**    — authenticated (both modes)
 *   /v1/tariffs/**     — authenticated (both modes)
 *   /v1/services/**    — authenticated (both modes)
 *   /actuator/health   — open (probes)
 *   /v3/api-docs/**    — open (documentation)
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public TrustContextFilter trustContextFilter() {
        return new TrustContextFilter();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .addFilterBefore(trustContextFilter(), UsernamePasswordAuthenticationFilter.class)
            .authorizeHttpRequests(auth -> auth
                // Probes and documentation — open
                .requestMatchers("/actuator/health", "/actuator/info", "/actuator/prometheus").permitAll()
                .requestMatchers("/v3/api-docs/**", "/swagger-ui/**").permitAll()
                // All API endpoints require a valid Tshepo-issued JWT
                .anyRequest().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> {}));

        return http.build();
    }
}
