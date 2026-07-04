package zw.gov.mohcc.impilo.iotingestion.config;

import org.springframework.beans.factory.annotation.Value;
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
    public SecurityFilterChain filterChain(HttpSecurity http,
                                           @Value("${impilo.security.mode:oauth2}") String securityMode) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));

        if ("permit-all".equalsIgnoreCase(securityMode)) {
            http.authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        } else {
            http.authorizeHttpRequests(auth -> auth
                            .requestMatchers("/actuator/health", "/actuator/info", "/actuator/prometheus").permitAll()
                            .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                            // The error dispatch must be permitted: without this, any controller
                            // exception re-enters the chain as an unauthenticated /error request
                            // and Http403ForbiddenEntryPoint masks the real failure as an
                            // empty-body 403 (the "silent 403" defect).
                            .requestMatchers("/error").permitAll()
                            .anyRequest().authenticated())
                    .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> {}));
        }
        return http.build();
    }
}
