package zw.gov.mohcc.impilo.auditledger.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

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
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            @Value("${impilo.security.disable-oauth-for-tests:false}") boolean disableOauthForTests)
            throws Exception {

        http.csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS));

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
            http.authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        }

        return http.build();
    }
}
