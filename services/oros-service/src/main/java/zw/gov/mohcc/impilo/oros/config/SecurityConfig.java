package zw.gov.mohcc.impilo.oros.config;

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
 * OROS security configuration.
 *
 * <p>Endpoint access matrix:</p>
 * <ul>
 *   <li>{@code /actuator/**} — open (health probes, Prometheus metrics)</li>
 *   <li>{@code /swagger-ui/**}, {@code /v3/api-docs/**} — open (API documentation)</li>
 *   <li>{@code /v1/**} — authenticated (all OROS business endpoints)</li>
 *   <li>Everything else — authenticated</li>
 * </ul>
 *
 * <p>The {@link TrustContextFilter} from shared-core is registered before
 * {@link UsernamePasswordAuthenticationFilter} to ensure trust context
 * (tenant, actor, facility, correlation ID, etc.) is available in all
 * downstream processing.</p>
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public TrustContextFilter trustContextFilter() {
        return new TrustContextFilter();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
            @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri:}") String issuerUri) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .addFilterBefore(trustContextFilter(), UsernamePasswordAuthenticationFilter.class)
            .authorizeHttpRequests(auth -> auth
                // Actuator — health probes and metrics
                .requestMatchers("/actuator/health", "/actuator/info", "/actuator/prometheus").permitAll()
                // API documentation
                .requestMatchers("/swagger-ui/**").permitAll()
                .requestMatchers("/v3/api-docs/**").permitAll()
                // All OROS business endpoints require authentication
                .requestMatchers("/v1/**").authenticated()
                // Everything else requires authentication
                .anyRequest().permitAll()
            );

        if (issuerUri != null && !issuerUri.isBlank()) {
            http.oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> {}));
        }

        return http.build();
    }
}
