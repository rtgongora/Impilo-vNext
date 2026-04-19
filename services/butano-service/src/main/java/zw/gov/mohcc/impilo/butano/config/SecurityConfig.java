package zw.gov.mohcc.impilo.butano.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import zw.gov.mohcc.impilo.shared.auth.TrustContextFilter;

/**
 * BUTANO security configuration — dual-mode access for the Shared Health Record.
 *
 * <p>Endpoint access matrix:</p>
 * <ul>
 *   <li>{@code /fhir/**} — open (FHIR servlet handles its own auth via HAPI interceptors;
 *       trust headers are validated by {@link zw.gov.mohcc.impilo.butano.interceptor.HeaderValidationInterceptor})</li>
 *   <li>{@code /actuator/**} — open (health probes, Prometheus metrics)</li>
 *   <li>{@code /v1/public/**} — open (public SHR metadata endpoints)</li>
 *   <li>{@code /swagger-ui/**}, {@code /v3/api-docs/**} — open (API documentation)</li>
 *   <li>{@code /v1/internal/**} — authenticated (internal admin/reconciliation endpoints)</li>
 *   <li>{@code /v1/summary/**} — authenticated (patient summary endpoints)</li>
 * </ul>
 *
 * <p>The FHIR endpoints are intentionally permitted at the Spring Security level
 * because the HAPI FHIR interceptor chain provides its own security enforcement
 * (header validation, tenant isolation, PII prevention). Double-gating through
 * both Spring Security and HAPI interceptors would complicate error handling
 * and is unnecessary since all FHIR requests pass through the interceptor chain.</p>
 *
 * <p>The {@link TrustContextFilter} from shared-core is registered before
 * {@link UsernamePasswordAuthenticationFilter} to ensure trust context is
 * available in all downstream processing.</p>
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
                // FHIR endpoints — secured by HAPI interceptor chain
                .requestMatchers("/fhir/**").permitAll()
                // Actuator — health probes and metrics
                .requestMatchers("/actuator/health", "/actuator/info", "/actuator/prometheus").permitAll()
                // Public metadata endpoints
                .requestMatchers("/v1/public/**").permitAll()
                // API documentation
                .requestMatchers("/swagger-ui/**").permitAll()
                .requestMatchers("/v3/api-docs/**").permitAll()
                // Internal admin and reconciliation endpoints require authentication
                .requestMatchers("/v1/internal/**").authenticated()
                // Patient summary endpoints require authentication
                .requestMatchers("/v1/summary/**").authenticated()
                // Everything else requires authentication
                .anyRequest().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> {}));

        return http.build();
    }
}
