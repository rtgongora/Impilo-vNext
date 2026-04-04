package zw.gov.mohcc.impilo.experience.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Security configuration with Keycloak JWT validation.
 *
 * <p>When a JwtDecoder bean is available (production/integration): enforces
 * RS256 signature verification, expiration checking, and path-based RBAC.
 * When no JwtDecoder bean exists (dev with OAuth2 auto-config excluded):
 * falls back to permitAll() with a prominent warning log.</p>
 *
 * <p>Path-based access rules (production mode):</p>
 * <ul>
 *   <li>Auth endpoints (/internal/v1/auth/**) — public</li>
 *   <li>Admin endpoints (/internal/v1/admin/**) — ADMIN role group</li>
 *   <li>Finance endpoints (/internal/v1/finance/**) — FINANCE role group</li>
 *   <li>Actuator/health — public</li>
 *   <li>All other endpoints — authenticated (valid JWT required)</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

    @Autowired(required = false)
    private JwtDecoder jwtDecoder;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));

        if (jwtDecoder != null) {
            // Production mode: full JWT validation + RBAC
            log.info("JWT validation ENABLED — enforcing role-based access control");
            http
                .authorizeHttpRequests(auth -> auth
                    .requestMatchers("/internal/v1/auth/**").permitAll()
                    .requestMatchers("/actuator/**").permitAll()
                    .requestMatchers("/internal/v1/admin/**").hasAnyRole(
                            "SYSTEM_ADMIN", "FACILITY_ADMIN", "DEVELOPER")
                    .requestMatchers("/internal/v1/finance/**").hasAnyRole(
                            "SYSTEM_ADMIN", "FACILITY_ADMIN", "FINANCE")
                    .anyRequest().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                    .jwt(jwt -> jwt.jwtAuthenticationConverter(keycloakJwtConverter()))
                );
        } else {
            // Dev mode: OAuth2 auto-config excluded, no JwtDecoder available
            log.warn("SECURITY: JWT validation DISABLED — no JwtDecoder bean found. "
                    + "All endpoints are open. This is acceptable only in development. "
                    + "Ensure OAuth2ResourceServerAutoConfiguration is NOT excluded in production.");
            http.authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        }

        return http.build();
    }

    @Bean
    public JwtAuthenticationConverter keycloakJwtConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(new KeycloakRealmRoleConverter());
        return converter;
    }

    /**
     * Extracts realm_access.roles from the Keycloak JWT and converts them
     * to Spring Security authorities with the ROLE_ prefix.
     */
    static class KeycloakRealmRoleConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

        @Override
        public Collection<GrantedAuthority> convert(Jwt jwt) {
            List<GrantedAuthority> authorities = new ArrayList<>();

            Map<String, Object> realmAccess = jwt.getClaim("realm_access");
            if (realmAccess != null) {
                Object rolesObj = realmAccess.get("roles");
                if (rolesObj instanceof List<?> roles) {
                    for (Object role : roles) {
                        String r = role.toString();
                        if (!r.startsWith("default-roles-")
                                && !r.equals("offline_access")
                                && !r.equals("uma_authorization")) {
                            authorities.add(new SimpleGrantedAuthority("ROLE_" + r));
                        }
                    }
                }
            }

            return authorities;
        }
    }
}
