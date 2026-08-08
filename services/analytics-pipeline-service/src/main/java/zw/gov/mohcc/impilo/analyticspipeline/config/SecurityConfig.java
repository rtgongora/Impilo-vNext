package zw.gov.mohcc.impilo.analyticspipeline.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Analytics Pipeline service security configuration.
 *
 * <p>This service had <b>no authentication of any kind</b> until Phase 0 E. Spring Security was
 * not on the classpath; the {@code SecurityBaselineConfig} beside this one supplies rate
 * limiting, secret access and an exception handler, and nothing else — which is why probes saw
 * {@code X-RateLimit-*} headers on responses that had never been authenticated. The estate
 * probe sweep measured the consequence: POST /internal/v1/telemedicine/events returned 202 ACCEPTED and minted an event id for an unauthenticated in-cluster caller.</p>
 *
 * <p>Business endpoints now require an authenticated principal. Actuator probes stay open
 * deliberately — requiring a credential on the readiness probe fails the pod and takes the
 * service down, which is a self-inflicted outage in the name of a fix.</p>
 *
 * <p>Callers are unaffected: the registry records this service as {@code exposes_to:
 * experience-bff}, and the BFF's outbound interceptor forwards the inbound user token or mints
 * its own client_credentials token, so BFF-originated calls already carry a bearer.</p>
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /**
     * @param disableOauthForTests estate idiom: lets the in-JVM suites drive the service without
     *        a token issuer. When true no {@code oauth2ResourceServer} is configured, so no
     *        {@code JwtDecoder} bean is required and the context still starts (the G046 trap).
     */
    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            @Value("${impilo.security.disable-oauth-for-tests:false}") boolean disableOauthForTests)
            throws Exception {

        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS));

        if (disableOauthForTests) {
            http.authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
            return http.build();
        }

        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(
                        "/actuator/health",
                        "/actuator/health/**",
                        "/actuator/info",
                        "/actuator/prometheus").permitAll()
                .requestMatchers(
                        "/v3/api-docs/**",
                        "/swagger-ui/**",
                        "/swagger-ui.html").permitAll()
                .anyRequest().authenticated())
            .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()));

        return http.build();
    }
}
