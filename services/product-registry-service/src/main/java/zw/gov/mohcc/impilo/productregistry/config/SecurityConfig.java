package zw.gov.mohcc.impilo.productregistry.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
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
 * Product Registry service security configuration.
 *
 * <p>All business endpoints require authentication. Probes and API docs remain open.
 * Trust context is extracted from inbound headers via shared TrustContextFilter.</p>
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public TrustContextFilter trustContextFilter(ObjectMapper objectMapper) {
        return new TrustContextFilter(objectMapper);
    }

    @Bean
    @ConditionalOnProperty(name = "impilo.security.disable-oauth-for-tests",
            havingValue = "false", matchIfMissing = true)
    public SecurityFilterChain securityFilterChain(HttpSecurity http, TrustContextFilter trustContextFilter,
            @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri:}") String issuerUri) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .addFilterBefore(trustContextFilter, UsernamePasswordAuthenticationFilter.class)
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health", "/actuator/info", "/actuator/prometheus").permitAll()
                .requestMatchers("/v3/api-docs/**", "/swagger-ui/**").permitAll()
                .anyRequest().authenticated());
        if (issuerUri != null && !issuerUri.isBlank()) {
            http.oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> {}));
        }
        return http.build();
    }

    /**
     * Test-only chain (estate idiom): opened by
     * {@code impilo.security.disable-oauth-for-tests=true} so the golden contract
     * suite can drive the v1.1 probe endpoints without a token issuer. TrustContextFilter
     * still runs so the companion header/idempotency filters behave as in production.
     * Never active under production profiles.
     */
    @Bean
    @ConditionalOnProperty(name = "impilo.security.disable-oauth-for-tests", havingValue = "true")
    public SecurityFilterChain testSecurityFilterChain(HttpSecurity http,
            TrustContextFilter trustContextFilter) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .addFilterBefore(trustContextFilter, UsernamePasswordAuthenticationFilter.class)
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/internal/v1/**", "/external/v1/**").permitAll()
                .requestMatchers("/actuator/**", "/v3/api-docs/**", "/swagger-ui/**").permitAll()
                .anyRequest().authenticated());
        return http.build();
    }
}
