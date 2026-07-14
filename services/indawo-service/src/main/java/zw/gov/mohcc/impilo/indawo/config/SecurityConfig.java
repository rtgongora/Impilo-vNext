package zw.gov.mohcc.impilo.indawo.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

import java.util.*;

/**
 * Indawo security:
 * - When JwtDecoder is present: validates JWT and enforces RBAC.
 * - When JwtDecoder is absent: can allow-anonymous for dev/tests.
 *
 * Indawo protects non-facility site-registry operations (public health premises).
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

    // ── Role groups (Keycloak realm roles) ──────────────────────────────────
    // SYSTEM_ADMIN overrides everything.

    public static final String[] SITE_APPLICANT_ROLES = {
            "SITE_APPLICANT", "CITIZEN", "SYSTEM_ADMIN", "DEVELOPER"
    };

    public static final String[] SITE_OPERATOR_ROLES = {
            "SITE_OPERATOR", "SYSTEM_ADMIN", "DEVELOPER"
    };

    public static final String[] INSPECTOR_ROLES = {
            "INSPECTOR", "ENVIRONMENTAL_HEALTH_OFFICER", "PUBLIC_HEALTH_OFFICER", "ENV_HEALTH",
            "SYSTEM_ADMIN", "DEVELOPER"
    };

    public static final String[] REVIEWER_ROLES = {
            "REGULATORY_REVIEWER", "COMMITTEE_MEMBER", "PUBLIC_HEALTH_OFFICER", "SYSTEM_ADMIN", "DEVELOPER"
    };

    public static final String[] LICENSING_ROLES = {
            "LICENSING_OFFICER", "REGISTRY_ADMIN", "SYSTEM_ADMIN", "DEVELOPER"
    };

    public static final String[] MOH_VIEWER_ROLES = {
            "MOH_VIEWER", "PUBLIC_HEALTH_OFFICER", "SYSTEM_ADMIN", "DEVELOPER"
    };

    @Autowired(required = false)
    private JwtDecoder jwtDecoder;

    @Value("${impilo.security.allow-anonymous:false}")
    private boolean allowAnonymous;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS));

        if (jwtDecoder != null) {
            log.info("Indawo JWT validation ENABLED — enforcing RBAC");
            http
                    .authorizeHttpRequests(auth -> auth
                            .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                            .requestMatchers("/actuator/health", "/actuator/info", "/actuator/prometheus").permitAll()

                            // Legacy registry endpoints (internal read) — require viewer roles
                            .requestMatchers(HttpMethod.GET, "/internal/v1/sites/**").hasAnyRole(MOH_VIEWER_ROLES)
                            .requestMatchers(HttpMethod.PUT, "/internal/v1/sites/**").hasAnyRole(LICENSING_ROLES)
                            .requestMatchers(HttpMethod.GET, "/internal/v1/snapshots/**").hasAnyRole(MOH_VIEWER_ROLES)

                            // Site-registry ops (fine-grained via method security)
                            .requestMatchers("/internal/v1/site-registry/**").authenticated()

                            .anyRequest().authenticated()
                    )
                    .oauth2ResourceServer(oauth2 -> oauth2
                            .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
                    );
        } else if (allowAnonymous) {
            log.warn("Indawo JWT validation DISABLED (allow-anonymous=true) — dev/test mode");
            http.authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        } else {
            throw new IllegalStateException("No JwtDecoder bean and impilo.security.allow-anonymous=false");
        }

        return http.build();
    }

    private JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(this::extractAuthorities);
        return converter;
    }

    private Collection<GrantedAuthority> extractAuthorities(Jwt jwt) {
        Set<String> roles = new LinkedHashSet<>();

        Object realmAccess = jwt.getClaims().get("realm_access");
        if (realmAccess instanceof Map<?, ?> ra) {
            Object rolesObj = ra.get("roles");
            if (rolesObj instanceof List<?> list) {
                for (Object r : list) {
                    if (r == null) continue;
                    String role = r.toString();
                    if (role.startsWith("default-roles-") || role.equals("offline_access") || role.equals("uma_authorization")) {
                        continue;
                    }
                    roles.add(role);
                }
            }
        }

        // Spring Security expects ROLE_ prefix for hasRole/hasAnyRole.
        List<GrantedAuthority> authorities = new ArrayList<>();
        for (String r : roles) {
            authorities.add(new SimpleGrantedAuthority("ROLE_" + r));
        }
        return authorities;
    }
}

