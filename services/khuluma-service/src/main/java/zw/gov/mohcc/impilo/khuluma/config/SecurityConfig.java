package zw.gov.mohcc.impilo.khuluma.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import zw.gov.mohcc.impilo.shared.auth.TrustContextFilter;

/**
 * OAuth2 JWT + trust-header extraction. Production is always secure; the open chain is reachable
 * only via the test-only {@code impilo.security.disable-oauth-for-tests} flag (no production
 * auth off-switch) — consistent with the G046-remediated pattern.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public TrustContextFilter trustContextFilter(ObjectMapper objectMapper) {
        return new TrustContextFilter(objectMapper);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            TrustContextFilter trustContextFilter,
            @Value("${impilo.security.disable-oauth-for-tests:false}") boolean disableOauthForTests) throws Exception {

        http.csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(trustContextFilter, UsernamePasswordAuthenticationFilter.class);

        if (!disableOauthForTests) {
            http.authorizeHttpRequests(auth -> auth
                            .requestMatchers(
                                    "/actuator/health", "/actuator/health/**", "/actuator/info",
                                    "/actuator/prometheus", "/actuator/metrics", "/actuator/metrics/**",
                                    "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html"
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
