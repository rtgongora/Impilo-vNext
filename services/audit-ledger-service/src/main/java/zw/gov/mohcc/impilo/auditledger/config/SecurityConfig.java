package zw.gov.mohcc.impilo.auditledger.config;

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
 * Authentication for the audit ledger.
 *
 * <h2>What was open, and how it was measured</h2>
 * <p>This service had <b>no security dependencies and therefore no filter chain at all</b> — its
 * {@code SecurityBaselineConfig} registers a rate limiter and a secret provider and nothing else.
 * Measured 2026-08-07 from an unrelated pod, with no credential of any kind:</p>
 *
 * <pre>
 * GET /internal/v1/audit/chain/verify?from_seq=1&amp;to_seq=1   ->  200
 * {"tenant_id":"…","from_seq":1,"to_seq":1,"valid":true}
 * </pre>
 *
 * <p>The 400s returned by a bare request were {@code MISSING_REQUIRED_HEADER} from the v1.1 header
 * check — a <em>well-formedness</em> gate, not an authentication one. Every one of those headers is
 * client-supplied and unverified, so supplying them is not proof of anything; it reads like a
 * refusal and is not one. That is why the exposure survived a grep: the service answered 400, and
 * 400 looks like a rejection.</p>
 *
 * <h2>Why an unauthenticated audit ledger is worse than an unauthenticated ordinary service</h2>
 * <p>This is the append-only record of who accessed what. Unauthenticated <em>writes</em> mean the
 * record can be forged; unauthenticated writes plus a rate limiter mean it can also be flooded, and
 * a ledger that can be flooded is one that can be used to bury an entry. Unauthenticated
 * <em>reads</em> disclose the access history of every subject in the tenant. The integrity
 * machinery — hash chaining, {@code /chain/verify} — protects against tampering with existing rows
 * and does nothing whatsoever about who may add or read them.</p>
 *
 * <h2>Shape</h2>
 * <p>Deliberately identical to {@code general-ledger-service}'s: authenticate everything, permit
 * only liveness/readiness and API docs, and honour the same {@code disable-oauth-for-tests} switch
 * so this service is configured the way its neighbours already are. Authentication only — no
 * {@code @PreAuthorize}, no role model invented here. Establishing <em>who is calling</em> is the
 * whole of this change; deciding <em>what each caller may do</em> is a separate question that
 * belongs with the estate's authorization model, and conflating them is how a security fix turns
 * into a redesign that never ships.</p>
 *
 * <p>The probe paths must stay permitted. Requiring a credential on {@code /actuator/health} would
 * fail readiness and take the service down — a self-inflicted outage in the name of a fix.</p>
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
            @Value("${impilo.security.disable-oauth-for-tests:false}") boolean disableOauthForTests)
            throws Exception {

        http.csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // The controller's actor-type guard reads TrustContextHolder; without this filter it
                // would see an empty context on every request and refuse everything, which is the
                // safe direction to fail but not a working service.
                .addFilterAfter(trustContextFilter, UsernamePasswordAuthenticationFilter.class);

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
                                    "/swagger-ui.html"
                            ).permitAll()
                            .anyRequest().authenticated())
                    .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()));
        } else {
            // Preview smoke and integration tests only. Note this branch is what
            // IMPILO_SECURITY_DISABLE_OAUTH_FOR_TESTS selects, and it must never be set in an
            // environment holding real audit records.
            //
            // What enables it, established rather than assumed (Phase 0 workstream A):
            //   - The property defaults to FALSE, so absence of configuration is fail-CLOSED. A
            //     service that is never told about the flag runs the authenticated chain.
            //   - deploy/helm/impilo-vnext/values-full-preview.yaml gives audit-ledger-service an
            //     env block (issuer-uri, jwk-set-uri) and does NOT set the flag, so the deployed
            //     preview runs the authenticated branch. The two services that do pin it —
            //     tshepo-audit-service and tshepo-authz-service — pin it to "false".
            //   - It is set "true" only in compose/*-e2e and compose/experience/docker-compose
            //     .sovereign.yml, and in src/test resources.
            // So this is the estate's standard test switch, not a fail-open fallback reachable in
            // the deployed estate. It is worth stating explicitly because a permitAll escape hatch
            // in a security config is exactly the kind of thing that should never be taken on
            // trust — including this one, which is why the list above is a measurement and not a
            // reassurance.
            //
            // AuditLedgerAuthenticationTest overrides the flag back to false for that reason: the
            // 'test' profile sets it TRUE, and every assertion in that class would otherwise pass
            // against a wide-open service.
            http.authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        }

        return http.build();
    }
}
