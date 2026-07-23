package zw.gov.mohcc.impilo.msikaflow.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            @Value("${impilo.security.disable-oauth-for-tests:false}") boolean disableOauthForTests)
            throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));

        if (disableOauthForTests) {
            // Estate/test convention (fail-closed, default false): every other
            // sovereign service honors this flag; msika-flow ignoring it made
            // BFF marketplace calls 401 in preview despite the env being set.
            http.authorizeHttpRequests(authz -> authz.anyRequest().permitAll());
            return http.build();
        }

        http.authorizeHttpRequests(authz -> authz
                .requestMatchers(
                    "/actuator/**",
                    "/v3/api-docs/**",
                    "/swagger-ui/**",
                    "/swagger-ui.html"
                ).permitAll()
                .requestMatchers("/v1/internal/**").hasAuthority("SCOPE_internal")
                // Nhume dispatch-status callback: authenticated at the Envoy ext_authz
                // mesh edge, not by a bearer JWT — same precedent as mushex-service's
                // /internal/v1/claims/*/appeal-resubmit.
                .requestMatchers(org.springframework.http.HttpMethod.POST,
                        "/internal/v1/msika-flow/orders/*/dispatch-status").permitAll()
                // OF-B17: Nhume selection delivery-status callback — same mesh-edge
                // authentication precedent as the order dispatch-status callback.
                .requestMatchers(org.springframework.http.HttpMethod.POST,
                        "/internal/v1/msika-flow/selections/*/delivery-status").permitAll()
                .anyRequest().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> {}));

        return http.build();
    }
}
