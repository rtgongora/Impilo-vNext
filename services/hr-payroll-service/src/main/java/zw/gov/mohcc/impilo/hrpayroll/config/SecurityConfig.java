package zw.gov.mohcc.impilo.hrpayroll.config;


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
    public SecurityFilterChain securityFilterChain(HttpSecurity http, TrustContextFilter trustContextFilter,
                                                  @Value("${impilo.security.disable-oauth-for-tests:false}") boolean disableOauthForTests ) throws Exception {
        http.csrf(c -> c.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(trustContextFilter, UsernamePasswordAuthenticationFilter.class);
        if (!disableOauthForTests) {
            http.authorizeHttpRequests(a -> a.requestMatchers(
                            "/actuator/health", "/actuator/health/**", "/actuator/info",
                            "/actuator/prometheus", "/actuator/metrics", "/actuator/metrics/**",
                            "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html",
                            "/internal/v1/health", "/internal/v1/test-command"
                    ).permitAll()
                    // The error dispatch must be permitted: without this, any controller
                    // exception re-enters the chain as an unauthenticated /error request
                    // and Http403ForbiddenEntryPoint masks the real failure as an
                    // empty-body 403 (the "silent 403" defect).
                    .requestMatchers("/error").permitAll()
                    .anyRequest().authenticated())
                    .oauth2ResourceServer(o -> o.jwt(Customizer.withDefaults()));
        } else {
            http.authorizeHttpRequests(a -> a.anyRequest().permitAll());
        }
        return http.build();
    }
}
