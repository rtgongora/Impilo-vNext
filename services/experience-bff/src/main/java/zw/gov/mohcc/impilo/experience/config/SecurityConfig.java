package zw.gov.mohcc.impilo.experience.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Security configuration with Keycloak JWT validation.
 *
 * <p>Uses Spring Security OAuth2 Resource Server to validate JWT tokens
 * against the Keycloak JWKS endpoint (automatic RS256 signature verification
 * and expiration checking).</p>
 *
 * <p>Path-based access rules:</p>
 * <ul>
 *   <li>Auth endpoints (/internal/v1/auth/**) — public (login/register/refresh)</li>
 *   <li>Admin endpoints (/internal/v1/admin/**) — ADMIN role group required</li>
 *   <li>Finance endpoints (/internal/v1/finance/**) — FINANCE role group required</li>
 *   <li>Actuator/health — public</li>
 *   <li>All other endpoints — authenticated (valid JWT required)</li>
 * </ul>
 *
 * <p>A custom JwtAuthenticationConverter extracts Keycloak realm_access.roles
 * as Spring Security GrantedAuthority objects (prefixed with ROLE_).</p>
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // Public endpoints — no authentication required
                .requestMatchers("/internal/v1/auth/**").permitAll()
                .requestMatchers("/actuator/**").permitAll()

                // Admin endpoints — require ADMIN role group
                .requestMatchers("/internal/v1/admin/**").hasAnyRole(
                        "SYSTEM_ADMIN", "FACILITY_ADMIN", "DEVELOPER")

                // Finance endpoints — require FINANCE role group
                .requestMatchers("/internal/v1/finance/**").hasAnyRole(
                        "SYSTEM_ADMIN", "FACILITY_ADMIN", "FINANCE")

                // All other endpoints — require valid JWT (any authenticated user)
                .anyRequest().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt.jwtAuthenticationConverter(keycloakJwtConverter()))
            );

        return http.build();
    }

    /**
     * Converts Keycloak realm_access.roles from the JWT into Spring Security
     * GrantedAuthority objects with the ROLE_ prefix.
     *
     * <p>Keycloak stores realm roles in the claim path: realm_access → roles (array).
     * Spring Security's hasRole("X") checks for "ROLE_X" in the authorities.</p>
     */
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
        @SuppressWarnings("unchecked")
        public Collection<GrantedAuthority> convert(Jwt jwt) {
            List<GrantedAuthority> authorities = new ArrayList<>();

            Map<String, Object> realmAccess = jwt.getClaim("realm_access");
            if (realmAccess != null) {
                Object rolesObj = realmAccess.get("roles");
                if (rolesObj instanceof List<?> roles) {
                    for (Object role : roles) {
                        String r = role.toString();
                        // Skip Keycloak default roles
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
