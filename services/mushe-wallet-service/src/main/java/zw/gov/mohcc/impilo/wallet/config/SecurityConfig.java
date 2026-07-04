package zw.gov.mohcc.impilo.wallet.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * OAuth2 JWT for business APIs; actuator, probes, and internal paths permitted.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            @Value("${impilo.security.disable-oauth-for-tests:false}") boolean disableOauthForTests ) throws Exception {

        http.csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS));

        if (!disableOauthForTests) {
            http.authorizeHttpRequests(auth -> auth
                            .requestMatchers(
                                    "/actuator/**",
                                    "/internal/v1/health",
                                    "/internal/v1/test-command",
                                    "/v3/api-docs/**",
                                    "/swagger-ui/**",
                                    "/swagger-ui.html"
                            ).permitAll()
                            // The error dispatch must be permitted: without this, any controller
                            // exception re-enters the chain as an unauthenticated /error request
                            // and Http403ForbiddenEntryPoint masks the real failure as an
                            // empty-body 403 (the "silent 403" defect).
                            .requestMatchers("/error").permitAll()
                            .anyRequest().authenticated())
                    .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()));
        } else {
            http.authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        }

        return http.build();
    }
}
