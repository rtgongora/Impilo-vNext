package zw.gov.mohcc.impilo.msika.config;


import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import zw.gov.mohcc.impilo.shared.auth.TrustContextFilter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public TrustContextFilter trustContextFilter(ObjectMapper objectMapper) {
        return new TrustContextFilter(objectMapper);
    }

    /**


     * JVM tests (MockMvc, @ActiveProfiles("test")) do not send Bearer tokens; open the chain


     * while keeping TrustContextFilter so v1.1 header / idempotency behaviour stays testable.


     */


    @Bean


    @ConditionalOnProperty(name = "impilo.security.disable-oauth-for-tests", havingValue = "true")


    public SecurityFilterChain testFilterChain(HttpSecurity http, TrustContextFilter trustContextFilter) throws Exception {


            http


                    .csrf(csrf -> csrf.disable())


                    .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))


                    .addFilterBefore(trustContextFilter, UsernamePasswordAuthenticationFilter.class)


                    .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());


            return http.build();


        }



        @Bean


        @ConditionalOnProperty(name = "impilo.security.disable-oauth-for-tests", havingValue = "false", matchIfMissing = true)


        public SecurityFilterChain productionFilterChain(HttpSecurity http, TrustContextFilter trustContextFilter) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .addFilterBefore(trustContextFilter, UsernamePasswordAuthenticationFilter.class)
            .authorizeHttpRequests(auth -> {
                auth
                .requestMatchers("/actuator/health", "/actuator/info", "/actuator/prometheus").permitAll()
                .requestMatchers("/swagger-ui/**").permitAll()
                .requestMatchers("/v3/api-docs/**").permitAll()
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll();

                if (allowAnonymous) {
                    auth.requestMatchers("/v1/**").permitAll();
                } else {
                    auth.requestMatchers("/v1/**").authenticated();
                }

                // The error dispatch must be permitted: without this, any controller
                // exception re-enters the chain as an unauthenticated /error request
                // and Http403ForbiddenEntryPoint masks the real failure as an
                // empty-body 403 (the "silent 403" defect).
                auth.requestMatchers("/error").permitAll();
                auth.anyRequest().authenticated();
            })
            .oauth2ResourceServer(oauth2 -> oauth2
                    .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())));

        return http.build();
    }@Value("${impilo.security.allow-anonymous:false}")
    boolean allowAnonymous;

    // Spring injects impilo.security.allow-anonymous; used only for test/dev.

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> extractAuthorities(jwt.getClaims()));
        return converter;
    }

    private Collection<GrantedAuthority> extractAuthorities(Map<String, Object> claims) {
        List<GrantedAuthority> authorities = new ArrayList<>();

        // Keycloak realm roles: realm_access.roles[]
        Object realmAccess = claims.get("realm_access");
        if (realmAccess instanceof Map<?, ?> ra) {
            Object roles = ra.get("roles");
            if (roles instanceof Collection<?> rc) {
                for (Object r : rc) {
                    if (r != null) {
                        authorities.add(new SimpleGrantedAuthority("ROLE_" + r.toString()));
                    }
                }
            }
        }

        // Resource roles: resource_access.<client>.roles[]
        Object resourceAccess = claims.get("resource_access");
        if (resourceAccess instanceof Map<?, ?> res) {
            for (Object v : res.values()) {
                if (v instanceof Map<?, ?> client) {
                    Object roles = client.get("roles");
                    if (roles instanceof Collection<?> rc) {
                        for (Object r : rc) {
                            if (r != null) {
                                authorities.add(new SimpleGrantedAuthority("ROLE_" + r.toString()));
                            }
                        }
                    }
                }
            }
        }

        // Scopes: scope string -> SCOPE_x
        Object scope = claims.get("scope");
        if (scope instanceof String s) {
            for (String sc : s.split(" ")) {
                if (!sc.isBlank()) authorities.add(new SimpleGrantedAuthority("SCOPE_" + sc.trim()));
            }
        }

        // Ensure baseline roles exist for ops if present as "roles"
        Object roles = claims.get("roles");
        if (roles instanceof Collection<?> rc) {
            for (Object r : rc) {
                if (r != null) authorities.add(new SimpleGrantedAuthority("ROLE_" + r.toString()));
            }
        }

        // Default for internal tokens that only carry scopes; keep method security explicit.
        if (authorities.isEmpty()) {
            authorities.addAll(List.of(new SimpleGrantedAuthority("ROLE_AUTHENTICATED")));
        }

        return authorities;
    }
}
