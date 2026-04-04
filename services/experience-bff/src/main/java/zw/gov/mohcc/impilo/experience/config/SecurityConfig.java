package zw.gov.mohcc.impilo.experience.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
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
 * Security configuration with Keycloak JWT validation and role-based access control.
 *
 * <p>When a JwtDecoder bean is available (production/integration): enforces
 * RS256 signature verification, expiration checking, and path-based RBAC.
 * When no JwtDecoder bean exists (dev with OAuth2 auto-config excluded):
 * falls back to permitAll() with a prominent warning log.</p>
 *
 * <h3>Role Groups (aligned with frontend AuthGuardProvider ROLE_GROUPS)</h3>
 * <ul>
 *   <li>ADMIN: SYSTEM_ADMIN, FACILITY_ADMIN, DEVELOPER</li>
 *   <li>FINANCE: SYSTEM_ADMIN, FACILITY_ADMIN, FINANCE</li>
 *   <li>CLINICAL: CLINICIAN, NURSE, FACILITY_ADMIN, SYSTEM_ADMIN, DEVELOPER</li>
 *   <li>PRESCRIBER: CLINICIAN, FACILITY_ADMIN, SYSTEM_ADMIN, DEVELOPER</li>
 *   <li>DISPENSER: PHARMACIST, FACILITY_ADMIN, SYSTEM_ADMIN, DEVELOPER</li>
 *   <li>QUEUE: CLINICIAN, NURSE, SUPPORT_AGENT, FACILITY_ADMIN, SYSTEM_ADMIN, DEVELOPER</li>
 *   <li>CITIZEN: CITIZEN, SYSTEM_ADMIN, DEVELOPER</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

    // ── Role group arrays ────────────────────────────────────────────
    // Each array lists the Keycloak realm roles that grant access to the group.
    // SYSTEM_ADMIN and DEVELOPER have override access to all groups.

    private static final String[] ADMIN_ROLES = {
            "SYSTEM_ADMIN", "FACILITY_ADMIN", "DEVELOPER"};

    private static final String[] FINANCE_ROLES = {
            "SYSTEM_ADMIN", "FACILITY_ADMIN", "FINANCE"};

    private static final String[] CLINICAL_ROLES = {
            "CLINICIAN", "NURSE", "FACILITY_ADMIN", "SYSTEM_ADMIN", "DEVELOPER"};

    private static final String[] PRESCRIBER_ROLES = {
            "CLINICIAN", "FACILITY_ADMIN", "SYSTEM_ADMIN", "DEVELOPER"};

    private static final String[] DISPENSER_ROLES = {
            "PHARMACIST", "FACILITY_ADMIN", "SYSTEM_ADMIN", "DEVELOPER"};

    private static final String[] QUEUE_ROLES = {
            "CLINICIAN", "NURSE", "SUPPORT_AGENT", "FACILITY_ADMIN", "SYSTEM_ADMIN", "DEVELOPER"};

    private static final String[] CITIZEN_ROLES = {
            "CITIZEN", "SYSTEM_ADMIN", "DEVELOPER"};

    @Autowired(required = false)
    private JwtDecoder jwtDecoder;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));

        if (jwtDecoder != null) {
            log.info("JWT validation ENABLED — enforcing role-based access control");
            http
                .authorizeHttpRequests(auth -> auth
                    // ── Public endpoints ──────────────────────────────────
                    .requestMatchers("/internal/v1/auth/**").permitAll()
                    .requestMatchers("/actuator/**").permitAll()

                    // ── Admin zone ────────────────────────────────────────
                    .requestMatchers("/internal/v1/admin/**").hasAnyRole(ADMIN_ROLES)

                    // ── Finance zone ──────────────────────────────────────
                    .requestMatchers("/internal/v1/finance/**").hasAnyRole(FINANCE_ROLES)

                    // ── Queue management ──────────────────────────────────
                    .requestMatchers(HttpMethod.POST, "/internal/v1/queue/**")
                            .hasAnyRole(QUEUE_ROLES)

                    // ── Pharmacy: prescriptions require prescriber role ───
                    .requestMatchers(HttpMethod.POST, "/internal/v1/pharmacy/prescriptions")
                            .hasAnyRole(PRESCRIBER_ROLES)
                    .requestMatchers(HttpMethod.POST, "/internal/v1/pharmacy/prescriptions/*/cancel")
                            .hasAnyRole(PRESCRIBER_ROLES)
                    // ── Pharmacy: dispense requires dispenser role ────────
                    .requestMatchers(HttpMethod.POST, "/internal/v1/pharmacy/dispense")
                            .hasAnyRole(DISPENSER_ROLES)

                    // ── Mobile provider: prescriptions → prescriber ───────
                    .requestMatchers(HttpMethod.POST, "/internal/v1/mobile/provider/prescriptions")
                            .hasAnyRole(PRESCRIBER_ROLES)
                    .requestMatchers(HttpMethod.POST, "/internal/v1/mobile/provider/prescriptions/*/cancel")
                            .hasAnyRole(PRESCRIBER_ROLES)

                    // ── Workspace selection requires clinical staff ──────
                    .requestMatchers(HttpMethod.GET, "/internal/v1/workspaces")
                            .hasAnyRole(CLINICAL_ROLES)
                    .requestMatchers(HttpMethod.POST, "/internal/v1/workspaces/*/activate")
                            .hasAnyRole(CLINICAL_ROLES)

                    // ── Clinical write endpoints (broad clinical staff) ───
                    .requestMatchers(HttpMethod.POST, "/internal/v1/encounters/**")
                            .hasAnyRole(CLINICAL_ROLES)
                    .requestMatchers(HttpMethod.POST, "/internal/v1/triage/**")
                            .hasAnyRole(CLINICAL_ROLES)
                    .requestMatchers(HttpMethod.POST, "/internal/v1/vitals/**")
                            .hasAnyRole(CLINICAL_ROLES)
                    .requestMatchers(HttpMethod.POST, "/internal/v1/clinical-notes/**")
                            .hasAnyRole(CLINICAL_ROLES)
                    .requestMatchers(HttpMethod.POST, "/internal/v1/lab-orders/**")
                            .hasAnyRole(CLINICAL_ROLES)
                    .requestMatchers(HttpMethod.POST, "/internal/v1/conditions/**")
                            .hasAnyRole(CLINICAL_ROLES)
                    .requestMatchers(HttpMethod.POST, "/internal/v1/allergies/**")
                            .hasAnyRole(CLINICAL_ROLES)
                    .requestMatchers(HttpMethod.DELETE, "/internal/v1/allergies/**")
                            .hasAnyRole(CLINICAL_ROLES)
                    .requestMatchers(HttpMethod.POST, "/internal/v1/immunizations/**")
                            .hasAnyRole(CLINICAL_ROLES)
                    .requestMatchers(HttpMethod.POST, "/internal/v1/referrals/**")
                            .hasAnyRole(CLINICAL_ROLES)
                    .requestMatchers(HttpMethod.POST, "/internal/v1/clinical-documents/**")
                            .hasAnyRole(CLINICAL_ROLES)
                    .requestMatchers(HttpMethod.POST, "/internal/v1/appointments/**")
                            .hasAnyRole(CLINICAL_ROLES)

                    // ── Mobile provider clinical operations ───────────────
                    .requestMatchers(HttpMethod.POST, "/internal/v1/mobile/provider/encounters/**")
                            .hasAnyRole(CLINICAL_ROLES)
                    .requestMatchers(HttpMethod.POST, "/internal/v1/mobile/provider/triage/**")
                            .hasAnyRole(CLINICAL_ROLES)
                    .requestMatchers(HttpMethod.POST, "/internal/v1/mobile/provider/vitals/**")
                            .hasAnyRole(CLINICAL_ROLES)
                    .requestMatchers(HttpMethod.DELETE, "/internal/v1/mobile/provider/vitals/**")
                            .hasAnyRole(CLINICAL_ROLES)
                    .requestMatchers(HttpMethod.POST, "/internal/v1/mobile/provider/discharge")
                            .hasAnyRole(CLINICAL_ROLES)

                    // ── Citizen mobile endpoints ──────────────────────────
                    .requestMatchers("/internal/v1/mobile/citizen/**")
                            .hasAnyRole(CITIZEN_ROLES)

                    // ── All other endpoints — authenticated ───────────────
                    .anyRequest().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                    .jwt(jwt -> jwt.jwtAuthenticationConverter(keycloakJwtConverter()))
                );
        } else {
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
