package zw.gov.mohcc.impilo.governance.config;


import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.Ordered;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import zw.gov.mohcc.impilo.shared.auth.TrustContextFilter;

@Configuration
@EnableWebSecurity
// Audience validation for this service's resource server. shared-core lives outside this
// application's component scan, so the import is explicit -- and the config itself is
// @ConditionalOnProperty(impilo.s2s.required-audience), making activation doubly opt-in:
// a service must both import it AND be told its audience.
@Import(zw.gov.mohcc.impilo.shared.auth.WorkloadAudienceJwtConfig.class)
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
                        // Checkpoint 7 first enforced cohort.
                        //
                        // This was `.anyRequest().permitAll()` unconditionally, which made
                        // `impilo.security.disable-oauth-for-tests` a flag that could not enforce
                        // anything: with it false the resource server validated a token IF one was
                        // presented, but no endpoint ever required one, so an anonymous caller was
                        // still served. Flipping the flag was the documented way to "enable OAuth"
                        // across 96 services and it would have changed nothing measurable.
                        //
                        // The probe and docs paths above stay open deliberately: /actuator/health
                        // is the kubelet's liveness and readiness probe, and requiring a token
                        // there would kill the pod rather than secure it.
                        .anyRequest().authenticated());
        if (!disableOauthForTests && issuerUri != null && !issuerUri.isBlank()) {
            http.oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> {
            }));
        }
        return http.build();
    }

    @Bean
    public FilterRegistrationBean<TrustContextFilter> trustContextFilter(ObjectMapper objectMapper) {
        FilterRegistrationBean<TrustContextFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new TrustContextFilter(objectMapper));
        // /internal/v1/* covers the Platform Origin governance API (IATG WS-A),
        // matching the RateLimitFilter registration in SecurityBaselineConfig.
        registration.addUrlPatterns("/v1/*", "/internal/v1/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        return registration;
    }
}
