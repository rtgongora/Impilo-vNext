package zw.gov.mohcc.impilo.support.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Support service security configuration.
 *
 * <p>This chain previously ended in {@code anyRequest().permitAll()} with no
 * {@code oauth2ResourceServer} at all, justified in a comment as "trust enforcement is
 * handled upstream by Envoy ext_authz and TSHEPO". <b>Envoy does not front this service.</b>
 * It fronts {@code experience-bff} only; every other service is reachable directly over
 * cluster DNS by any pod in the namespace. The comment described a control that did not
 * exist on this path, and the Phase 0 E probe sweep measured the consequence: support tickets and articles
 * accepted writes from an unauthenticated in-cluster caller.</p>
 *
 * <p>Business endpoints now require an authenticated principal. Actuator probes stay open
 * deliberately — requiring a credential on the readiness probe fails the pod and takes the
 * service down, which is a self-inflicted outage in the name of a fix.</p>
 *
 * <p>{@code /internal/v1/test-command} is <b>not</b> permitted here. It is driven only by
 * the in-JVM golden-contract suite, which runs on the test chain below, so exempting it on
 * the production chain bought nothing and returned 201 to anonymous callers.</p>
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /**
     * @param disableOauthForTests estate idiom: lets the in-JVM suites drive the service
     *        without a token issuer. When true no {@code oauth2ResourceServer} is configured,
     *        so no {@code JwtDecoder} bean is required and the context still starts.
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
