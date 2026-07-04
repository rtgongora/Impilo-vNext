package zw.gov.mohcc.impilo.madi.config;

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

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public TrustContextFilter trustContextFilter(ObjectMapper objectMapper) {
        return new TrustContextFilter(objectMapper);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http, TrustContextFilter trustContextFilter,
            @Value("${impilo.security.disable-oauth-for-tests:false}") boolean disableOauthForTests) throws Exception {

        http.csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(trustContextFilter, UsernamePasswordAuthenticationFilter.class);

        // Honour the estate-wide preview/test flag so internal trust-plane calls (e.g. BFF) are
        // permitted in the sandbox, consistent with pharmacy/campaigns. Production keeps oauth2.
        if (!disableOauthForTests) {
            http.authorizeHttpRequests(auth -> auth
                            .requestMatchers(
                                    "/actuator/health",
                                    "/actuator/health/**",
                                    "/actuator/info",
                                    "/actuator/prometheus",
                                    "/actuator/metrics",
                                    "/actuator/metrics/**",
                                    "/v3/api-docs/**",
                                    "/swagger-ui/**",
                                    "/swagger-ui.html",
                                    "/internal/v1/health",
                                    "/internal/v1/test-command"
                            ).permitAll()
                            .requestMatchers("/internal/v1/madi/**").authenticated()
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
