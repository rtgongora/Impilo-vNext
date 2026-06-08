package zw.gov.mohcc.impilo.booking.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import zw.gov.mohcc.impilo.shared.auth.TrustContextFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
            @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri:}") String issuerUri,
            @Value("${impilo.security.disable-oauth-for-tests:false}") boolean disableOauthForTests) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health", "/actuator/info", "/actuator/prometheus").permitAll()
                .requestMatchers("/v3/api-docs/**", "/swagger-ui/**").permitAll()
                .requestMatchers(disableOauthForTests ? "/v1/**" : "/__disabled_test_auth_bypass__").permitAll()
                .anyRequest().authenticated()
            );
        if (!disableOauthForTests && issuerUri != null && !issuerUri.isBlank()) {
            http.oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> {}));
        }

        return http.build();
    }

    @Bean
    public FilterRegistrationBean<TrustContextFilter> trustContextFilter(ObjectMapper objectMapper) {
        FilterRegistrationBean<TrustContextFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new TrustContextFilter(objectMapper));
        registration.addUrlPatterns("/v1/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        return registration;
    }
}
