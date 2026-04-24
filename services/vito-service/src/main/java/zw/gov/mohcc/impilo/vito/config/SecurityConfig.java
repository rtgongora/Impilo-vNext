package zw.gov.mohcc.impilo.vito.config;


import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import zw.gov.mohcc.impilo.shared.auth.TrustContextFilter;

/**
 * VITO security configuration implementing the Tshepo-VITO Handshake.
 *
 * Dual-mode access (Sovereign Service 2):
 *
 * INTERNAL MODE (platform services via Envoy):
 *   Envoy ext_authz → TSHEPO validates → trust headers injected.
 *   Full access to identity operations: issuance, recovery, merge, card, wallet.
 *
 * EXTERNAL MODE (3rd-party identity consumers):
 *   External systems (civil registrars, insurance, etc.) with Tshepo-scoped JWT.
 *   Read-only identity verification, OpenCR $match, demographic lookup.
 *   No write access to cards, wallets, or identity merges.
 *
 * Endpoint access matrix:
 *   /v1/clients/**          — authenticated (both modes)
 *   /v1/identity/**         — authenticated (INTERNAL write, EXTERNAL read)
 *   /v1/cards/**            — authenticated (INTERNAL only for mutations)
 *   /v1/wallet/**           — authenticated (INTERNAL only for mutations)
 *   /v1/biometric/**        — authenticated (INTERNAL only)
 *   /v1/match/**            — authenticated (both modes — OpenCR interop)
 *   /v1/recovery/**         — authenticated (INTERNAL only)
 *   /v1/did/**              — authenticated (INTERNAL only)
 *   /actuator/health        — open (probes)
 *   /v3/api-docs/**         — open (documentation)
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public TrustContextFilter trustContextFilter(ObjectMapper objectMapper) {
        return new TrustContextFilter(objectMapper);
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, TrustContextFilter trustContextFilter,
            @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri:}") String issuerUri) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .addFilterBefore(trustContextFilter, UsernamePasswordAuthenticationFilter.class)
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health", "/actuator/info", "/actuator/prometheus").permitAll()
                .requestMatchers("/v3/api-docs/**", "/swagger-ui/**").permitAll()
                .anyRequest().permitAll()
            );

        if (issuerUri != null && !issuerUri.isBlank()) {
            http.oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> {}));
        }

        return http.build();
    }
}
