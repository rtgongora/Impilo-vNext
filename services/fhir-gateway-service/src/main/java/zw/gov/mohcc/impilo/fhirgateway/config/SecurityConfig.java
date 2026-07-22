package zw.gov.mohcc.impilo.fhirgateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * FHIR gateway security configuration.
 *
 * <p>Honours the platform-wide {@code impilo.security.disable-oauth-for-tests}
 * (env {@code IMPILO_SECURITY_DISABLE_OAUTH_FOR_TESTS}) switch exactly like every
 * other service (PCT/OROS/…): when set, the JWT resource-server is bypassed and the
 * {@code SecurityBaselineConfig} trust-header filter governs {@code /internal/v1/*}.
 * Previously this class hard-wired {@code oauth2ResourceServer(jwt)} regardless of the
 * flag, so in preview/test estates — where the flag is on for the whole platform —
 * the gateway alone 401'd service-to-service callers (e.g. the BFF's governed FHIR
 * writes), silently breaking every governed FHIR write. In production the flag is off
 * and JWT enforcement is unchanged.</p>
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
            @Value("${impilo.security.disable-oauth-for-tests:false}") boolean disableOauthForTests) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS));

        if (!disableOauthForTests) {
            http.authorizeHttpRequests(auth -> auth
                    .requestMatchers("/actuator/health", "/actuator/info", "/actuator/prometheus").permitAll()
                    .requestMatchers("/v3/api-docs/**", "/swagger-ui/**").permitAll()
                    .requestMatchers("/internal/v1/**").authenticated()
                    .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()));
        } else {
            http.authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        }
        return http.build();
    }
}
