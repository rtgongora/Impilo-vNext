package zw.gov.mohcc.impilo.tshepo.audit.config;

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
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // Internal ingest endpoint — called by other TSHEPO services behind Envoy
                .requestMatchers("/v1/audit/events").permitAll()
                // Chain integrity verification — internal operations
                .requestMatchers("/v1/audit/verify-chain").permitAll()
                // Actuator probes
                .requestMatchers("/actuator/health", "/actuator/info", "/actuator/prometheus").permitAll()
                // OpenAPI
                .requestMatchers("/v3/api-docs/**", "/swagger-ui/**").permitAll()
                // Everything else requires JWT (query, access-history, export, verify)
                .anyRequest().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> {}));

        return http.build();
    }
}
