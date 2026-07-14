package zw.gov.mohcc.impilo.tshepo.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    @ConditionalOnProperty(name = "impilo.security.disable-oauth-for-tests",
            havingValue = "false", matchIfMissing = true)
    public SecurityFilterChain filterChain(HttpSecurity http,
            @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri:}") String issuerUri) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/v1/authorize", "/v1/authorize/**").permitAll()
                .requestMatchers("/actuator/health", "/actuator/info", "/actuator/prometheus").permitAll()
                .requestMatchers("/v3/api-docs/**", "/swagger-ui/**").permitAll()
                .anyRequest().authenticated()
            );

        if (issuerUri != null && !issuerUri.isBlank()) {
            http.oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> {}));
        }

        return http.build();
    }

    /**
     * Test-only chain (estate idiom, see msika/coverage SecurityConfig): opened by
     * {@code impilo.security.disable-oauth-for-tests=true} so the surefire suite can
     * exercise v1.1 companion behaviour (header enforcement, idempotency, envelopes)
     * without a token issuer. Never enabled in production profiles.
     */
    @Bean
    @ConditionalOnProperty(name = "impilo.security.disable-oauth-for-tests", havingValue = "true")
    public SecurityFilterChain testFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/internal/v1/**").permitAll()
                .requestMatchers("/v1/authorize", "/v1/authorize/**").permitAll()
                .requestMatchers("/actuator/health", "/actuator/info", "/actuator/prometheus").permitAll()
                .anyRequest().authenticated());
        return http.build();
    }
}
