package zw.gov.mohcc.impilo.vito.config;


import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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
 * Preview/test profile ({@code impilo.security.disable-oauth-for-tests=true}) opens
 * {@code /v1/client-registry/**} for first-party BFF S2S with trust headers only.
 * Production chain always requires authenticated JWT for business APIs.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public TrustContextFilter trustContextFilter(ObjectMapper objectMapper) {
        return new TrustContextFilter(objectMapper);
    }

    /**
     * Preview/test chain: permit client-registry reads for BFF S2S without Bearer JWT.
     * Flag-gated — never active when {@code impilo.security.disable-oauth-for-tests=false}.
     */
    @Bean
    @ConditionalOnProperty(name = "impilo.security.disable-oauth-for-tests", havingValue = "true")
    public SecurityFilterChain testFilterChain(HttpSecurity http, TrustContextFilter trustContextFilter,
            @org.springframework.beans.factory.annotation.Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri:}") String issuerUri) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .addFilterBefore(trustContextFilter, UsernamePasswordAuthenticationFilter.class)
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health", "/actuator/info", "/actuator/prometheus").permitAll()
                .requestMatchers("/v3/api-docs/**", "/swagger-ui/**").permitAll()
                .requestMatchers("/v1/client-registry/**").permitAll()
                // Preview/test only: the internal service-plane (v1.1) endpoints — provisional
                // identity mint, patient merge, etc. — are reachable without a broker/Envoy so
                // runtime-proof rigs can exercise the identity-reconcile path. Production keeps the
                // strict chain (authenticated + upstream ext_authz).
                .requestMatchers("/internal/v1/**").permitAll()
                .anyRequest().authenticated());

        // Without this, anyRequest().authenticated() is unsatisfiable on the test
        // chain — no JWT processing means every bearer-carrying business call
        // (e.g. BFF-relayed citizen registration) dies 403 as anonymous.
        if (issuerUri != null && !issuerUri.isBlank()) {
            http.oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> {}));
        }

        return http.build();
    }

    @Bean
    @ConditionalOnProperty(name = "impilo.security.disable-oauth-for-tests", havingValue = "false", matchIfMissing = true)
    public SecurityFilterChain productionFilterChain(HttpSecurity http, TrustContextFilter trustContextFilter,
            @org.springframework.beans.factory.annotation.Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri:}") String issuerUri) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .addFilterBefore(trustContextFilter, UsernamePasswordAuthenticationFilter.class)
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health", "/actuator/info", "/actuator/prometheus").permitAll()
                .requestMatchers("/v3/api-docs/**", "/swagger-ui/**").permitAll()
                .anyRequest().authenticated()
            );

        if (issuerUri != null && !issuerUri.isBlank()) {
            http.oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> {}));
        }

        return http.build();
    }
}
