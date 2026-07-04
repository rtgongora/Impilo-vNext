package zw.gov.mohcc.impilo.fhirgateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                    .requestMatchers("/actuator/health", "/actuator/info", "/actuator/prometheus").permitAll()
                    .requestMatchers("/v3/api-docs/**", "/swagger-ui/**").permitAll()
                    .requestMatchers("/internal/v1/**").authenticated()
                    // The error dispatch must be permitted: without this, any controller
                    // exception re-enters the chain as an unauthenticated /error request
                    // and Http403ForbiddenEntryPoint masks the real failure as an
                    // empty-body 403 (the "silent 403" defect).
                    .requestMatchers("/error").permitAll()
                    .anyRequest().authenticated())
            .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()));
        return http.build();
    }
}
