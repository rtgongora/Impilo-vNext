package zw.gov.mohcc.impilo.productregistry.config;

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
    public SecurityFilterChain securityFilterChain(HttpSecurity http, TrustContextFilter trustContextFilter,
            @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri:}") String issuerUri) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .addFilterBefore(trustContextFilter, UsernamePasswordAuthenticationFilter.class)
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health", "/actuator/info", "/actuator/prometheus").permitAll()
                .requestMatchers("/v3/api-docs/**", "/swagger-ui/**").permitAll()
                // The error dispatch must be permitted: without this, any controller
                // exception re-enters the chain as an unauthenticated /error request
                // and Http403ForbiddenEntryPoint masks the real failure as an
                // empty-body 403 (the "silent 403" defect).
                .requestMatchers("/error").permitAll()
                .anyRequest().authenticated());
        if (issuerUri != null && !issuerUri.isBlank()) {
            http.oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> {}));
        }
        return http.build();
    }
}
