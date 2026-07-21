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
    /**
     * Preview/test S2S chain (@Order 1): the internal service-plane + client-registry
     * paths are authed by TRUST HEADERS ONLY (first-party BFF S2S), never a user JWT.
     * This chain installs NO oauth2ResourceServer, so a Bearer the BFF relays from the
     * browser is IGNORED here instead of validated-and-rejected. Enabling JWT processing
     * on a single combined chain (1089af58d) regressed exactly this: an issuer-mismatched
     * or expired relayed user token 401s these permitAll paths (the "patient registry
     * unreachable" symptom), because Spring's bearer filter rejects a present-but-invalid
     * token before the permitAll authorization rule runs.
     *
     * VITO uses BOTH internal path conventions — {@code /internal/v1/**} (V11 patients,
     * legacy-phid) AND {@code /v1/internal/**} (client search, dedup, issuance, audit) —
     * so both are matched. Business paths fall through to {@link #businessTestFilterChain}.
     */
    @Bean
    @org.springframework.core.annotation.Order(1)
    @ConditionalOnProperty(name = "impilo.security.disable-oauth-for-tests", havingValue = "true")
    public SecurityFilterChain internalS2sTestFilterChain(HttpSecurity http, TrustContextFilter trustContextFilter)
            throws Exception {
        http
            .securityMatcher("/actuator/health", "/actuator/info", "/actuator/prometheus",
                    "/v3/api-docs/**", "/swagger-ui/**",
                    "/v1/client-registry/**", "/internal/v1/**", "/v1/internal/**")
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .addFilterBefore(trustContextFilter, UsernamePasswordAuthenticationFilter.class)
            .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }

    /**
     * Preview/test business chain (@Order 2): everything not claimed by the S2S chain
     * requires an authenticated JWT (identity register, portal, external MPI lookups via a
     * real user token). oauth2ResourceServer is installed here so the relayed Bearer is
     * processed — without it {@code anyRequest().authenticated()} is unsatisfiable and
     * every bearer-carrying business call dies 403 as anonymous.
     */
    @Bean
    @org.springframework.core.annotation.Order(2)
    @ConditionalOnProperty(name = "impilo.security.disable-oauth-for-tests", havingValue = "true")
    public SecurityFilterChain businessTestFilterChain(HttpSecurity http, TrustContextFilter trustContextFilter,
            @org.springframework.beans.factory.annotation.Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri:}") String issuerUri) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .addFilterBefore(trustContextFilter, UsernamePasswordAuthenticationFilter.class)
            .authorizeHttpRequests(auth -> auth.anyRequest().authenticated());

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
