package zw.gov.mohcc.impilo.tshepo.audit.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import zw.gov.mohcc.impilo.shared.auth.TrustContextFilter;

/**
 * Security for the audit trail.
 *
 * <h2>What was open</h2>
 * <p>{@code /v1/audit/events} — the canonical audit-trail write — and {@code /v1/audit/verify-chain}
 * were both {@code permitAll}, justified in a comment as "called service-to-service behind Envoy".
 * Envoy fronts only experience-bff; it has no route to this service, so nothing in the estate was
 * enforcing the boundary that comment relied on. Any pod that could resolve the service over cluster
 * DNS could append to the audit chain, and — because the write took its actor from the request body
 * — could name any actor, action and outcome it liked. See {@code AuditIngestController} for why a
 * forgeable audit trail is worse than an ordinary forgeable write.</p>
 *
 * <p>Both now fall through to {@code anyRequest().authenticated()}. {@link TrustContextFilter} is
 * registered so the ingest controller can attribute the event to the authenticated caller rather
 * than to whoever the body claims.</p>
 *
 * <h2>What the wildcards actually exposed</h2>
 * <p>The Phase 0 brief flagged {@code /internal/v1/**} and {@code /external/v1/**} as unscoped path
 * trees on the production chain. Measured, they are neither:</p>
 *
 * <ul>
 *   <li>They live on {@link #testFilterChain}, which is {@code @ConditionalOnProperty} on
 *       {@code impilo.security.disable-oauth-for-tests=true}. The production chain is disabled under
 *       that flag and vice versa, so the two never both apply. {@code values-full-preview.yaml} sets
 *       {@code IMPILO_SECURITY_DISABLE_OAUTH_FOR_TESTS: "false"} for this service explicitly, so the
 *       deployed estate runs the production chain.</li>
 *   <li>{@code /external/v1/**} matched <b>nothing</b>: no controller in this service has ever
 *       mapped that prefix. It is removed rather than kept, because a matcher for a path tree that
 *       does not exist is an invitation to add one under it and inherit the exemption by accident.</li>
 *   <li>{@code /internal/v1/**} matches exactly one controller, {@code TshepoAuditV11ProbeController}
 *       ({@code GET /health}, {@code POST /test-command}) — the golden-contract probe pair the v1.1
 *       suite drives without a token issuer. It is narrowed to those two paths for the same reason.</li>
 * </ul>
 *
 * <p>So the production exposure closed here is the two {@code /v1/audit} endpoints. The wildcards
 * were a test-chain concern, and are tightened on the way past rather than left as a wider blast
 * radius than the test needs.</p>
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public TrustContextFilter trustContextFilter(ObjectMapper objectMapper) {
        return new TrustContextFilter(objectMapper);
    }

    @Bean
    @ConditionalOnProperty(name = "impilo.security.disable-oauth-for-tests",
            havingValue = "false", matchIfMissing = true)
    public SecurityFilterChain filterChain(HttpSecurity http, TrustContextFilter trustContextFilter)
            throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // Actuator probes. Requiring a credential here fails readiness and takes the
                // service down — a self-inflicted outage in the name of a fix.
                .requestMatchers("/actuator/health", "/actuator/info", "/actuator/prometheus").permitAll()
                // OpenAPI
                .requestMatchers("/v3/api-docs/**", "/swagger-ui/**").permitAll()
                // Everything else — ingest, verify-chain, query, access-history, export.
                .anyRequest().authenticated()
            )
            .addFilterAfter(trustContextFilter, UsernamePasswordAuthenticationFilter.class)
            .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> {}));

        return http.build();
    }

    /**
     * Test-only chain (estate idiom): opened by disable-oauth-for-tests so the golden
     * contract suite can drive the v1.1 probe endpoints without a token issuer. The
     * production chain (with oauth2ResourceServer/JwtDecoder) is disabled under this
     * flag, so no JwtDecoder bean is required. Never active in production — see the class
     * javadoc for how that is established rather than assumed.
     */
    @Bean
    @ConditionalOnProperty(name = "impilo.security.disable-oauth-for-tests", havingValue = "true")
    public SecurityFilterChain testFilterChain(HttpSecurity http, TrustContextFilter trustContextFilter)
            throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // Exactly the two probe endpoints the golden contract suite drives, not the tree
                // they sit in.
                .requestMatchers("/internal/v1/health", "/internal/v1/test-command").permitAll()
                .anyRequest().authenticated())
            .addFilterAfter(trustContextFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
