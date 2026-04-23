package zw.gov.mohcc.impilo.shareslip.config;


import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import zw.gov.mohcc.impilo.shared.auth.TrustContextFilter;

/**
 * Share Slip security configuration.
 *
 * Dual-mode access:
 *
 * INTERNAL MODE (platform services via Envoy):
 *   Envoy ext_authz -> TSHEPO validates -> trust headers injected.
 *   Full access to share link CRUD, PDF generation, audit trails.
 *
 * PUBLIC MODE (delegated pickup / share claim):
 *   Public endpoints for claiming shared documents with OTP verification.
 *   Rate-limited to prevent brute-force OTP attacks.
 *
 * Endpoint access matrix:
 *   /v1/internal/share-links/**   -- authenticated (internal services only)
 *   /v1/public/share/claim/**     -- open (rate-limited, OTP protected)
 *   /v1/public/share/verify/**    -- open (read-only link validation)
 *   /actuator/health              -- open (probes)
 *   /v3/api-docs/**               -- open (documentation)
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public TrustContextFilter trustContextFilter(ObjectMapper objectMapper) {
        return new TrustContextFilter(objectMapper);
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, TrustContextFilter trustContextFilter) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .addFilterBefore(trustContextFilter, UsernamePasswordAuthenticationFilter.class)
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health", "/actuator/info", "/actuator/prometheus").permitAll()
                .requestMatchers("/v3/api-docs/**", "/swagger-ui/**").permitAll()
                .requestMatchers("/v1/public/share/claim/**").permitAll()
                .requestMatchers("/v1/public/share/verify/**").permitAll()
                .anyRequest().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> {}));

        return http.build();
    }
}
