package zw.gov.mohcc.impilo.ndila.config;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import zw.gov.mohcc.impilo.shared.auth.TrustContextFilter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Ndila security:
 *  - When JwtDecoder is present: validates JWT and enforces RBAC role groups.
 *  - When JwtDecoder is absent + allow-anonymous=true: dev/test mode.
 *
 * Sensitive Ndila operations (live tracking, restricted layers, audience
 * resolution, AI context packets) are routed through NdilaTshepoGuard for
 * fine-grained purpose/role/sensitivity checks regardless of the role group
 * matched here.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

    // Role groups (Keycloak realm roles). SYSTEM_ADMIN overrides everywhere.
    public static final String[] NDILA_VIEWER_ROLES = {
            "NDILA_VIEWER", "MOH_VIEWER", "PUBLIC_HEALTH_OFFICER",
            "DISTRICT_HEALTH_OFFICER", "PROVINCIAL_HEALTH_OFFICER",
            "DISPATCH_COORDINATOR", "EMERGENCY_RESPONDER",
            "CITIZEN", "PROVIDER", "SYSTEM_ADMIN", "DEVELOPER"
    };

    public static final String[] NDILA_EDITOR_ROLES = {
            "NDILA_EDITOR", "REGISTRY_ADMIN", "LICENSING_OFFICER",
            "FACILITY_ADMIN", "INSPECTOR", "ENVIRONMENTAL_HEALTH_OFFICER",
            "DISPATCH_COORDINATOR", "SYSTEM_ADMIN", "DEVELOPER"
    };

    public static final String[] NDILA_ADMIN_ROLES = {
            "NDILA_ADMIN", "SYSTEM_ADMIN", "DEVELOPER"
    };

    public static final String[] NDILA_TRACKING_ROLES = {
            "DISPATCH_COORDINATOR", "FLEET_MANAGER", "EMERGENCY_RESPONDER",
            "INSPECTOR", "FIELD_SUPERVISOR", "NDILA_ADMIN",
            "SYSTEM_ADMIN", "DEVELOPER"
    };

    public static final String[] NDILA_INTELLIGENCE_ROLES = {
            "PUBLIC_HEALTH_OFFICER", "DISTRICT_HEALTH_OFFICER",
            "PROVINCIAL_HEALTH_OFFICER", "NATIONAL_HEALTH_OFFICER",
            "DATA_ANALYST", "EMERGENCY_RESPONDER", "NDILA_ADMIN",
            "SYSTEM_ADMIN", "DEVELOPER"
    };

    @Autowired(required = false)
    private JwtDecoder jwtDecoder;

    @Value("${impilo.security.allow-anonymous:false}")
    private boolean allowAnonymous;

    @Bean
    public TrustContextFilter trustContextFilter(ObjectMapper objectMapper) {
        return new TrustContextFilter(objectMapper);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, TrustContextFilter trustContextFilter) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(trustContextFilter, UsernamePasswordAuthenticationFilter.class);

        if (jwtDecoder != null) {
            log.info("Ndila JWT validation ENABLED — enforcing RBAC");
            http
                    .authorizeHttpRequests(auth -> auth
                            .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                            .requestMatchers("/actuator/health", "/actuator/info", "/actuator/prometheus").permitAll()
                            .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()

                            // Public street-tile lane (gateway-public-lane-security ADR §3):
                            // anonymous MVT street geometry for the citizen find-care map —
                            // no PII, served via PublicStreetTileController.
                            .requestMatchers(HttpMethod.GET, "/v1/public/ndila/tiles/**").permitAll()

                            // Read access (geocode, search, nearby, route preview, public layers)
                            .requestMatchers(HttpMethod.POST, "/api/v1/ndila/geocode",
                                    "/api/v1/ndila/reverse-geocode",
                                    "/api/v1/ndila/routes",
                                    "/api/v1/ndila/routes/multi-stop",
                                    "/api/v1/ndila/distance-matrix",
                                    "/api/v1/ndila/eta",
                                    "/api/v1/ndila/validate-location",
                                    "/api/v1/ndila/spatial/**",
                                    "/api/v1/ndila/geofences/check-point",
                                    "/api/v1/ndila/geofences/intersections",
                                    "/api/v1/ndila/catchments/check-point",
                                    "/api/v1/ndila/catchments/nearest-service",
                                    "/api/v1/ndila/catchments/coverage-summary").hasAnyRole(NDILA_VIEWER_ROLES)
                            .requestMatchers(HttpMethod.GET, "/api/v1/ndila/search",
                                    "/api/v1/ndila/locations/**",
                                    "/api/v1/ndila/geofences/**",
                                    "/api/v1/ndila/catchments/**",
                                    "/api/v1/ndila/tiles/**").hasAnyRole(NDILA_VIEWER_ROLES)

                            // Edit access (create/update locations, geofences, catchments)
                            .requestMatchers(HttpMethod.POST, "/api/v1/ndila/locations",
                                    "/api/v1/ndila/geofences",
                                    "/api/v1/ndila/catchments").hasAnyRole(NDILA_EDITOR_ROLES)
                            .requestMatchers(HttpMethod.PUT, "/api/v1/ndila/**").hasAnyRole(NDILA_EDITOR_ROLES)
                            .requestMatchers(HttpMethod.DELETE, "/api/v1/ndila/**").hasAnyRole(NDILA_ADMIN_ROLES)

                            // Tracking — fleet, dispatch, emergency, inspection
                            .requestMatchers("/api/v1/ndila/tracking/**").hasAnyRole(NDILA_TRACKING_ROLES)

                            // Intelligence + AI context packets
                            .requestMatchers("/api/v1/ndila/intelligence/**").hasAnyRole(NDILA_INTELLIGENCE_ROLES)
                            .requestMatchers("/api/v1/ndila/audience/**").hasAnyRole(NDILA_INTELLIGENCE_ROLES)

                            // Internal HPA location import (service-to-service, in-cluster only;
                            // not gateway-routed externally). Mirrors the tuso/varapi internal HPA
                            // import endpoints: grants no authority and writes only UNVERIFIED
                            // candidate locations keyed to a tuso facility.
                            .requestMatchers("/internal/v1/locations/hpa-import/**").permitAll()

                            // Provider configs / ops
                            .requestMatchers("/internal/v1/ndila/providers/**").hasAnyRole(NDILA_ADMIN_ROLES)
                            .requestMatchers("/internal/v1/ndila/**").hasAnyRole(NDILA_VIEWER_ROLES)

                            // Legacy /api/v1/maps/* alias — same scopes as /api/v1/ndila/*
                            .requestMatchers("/api/v1/maps/**").hasAnyRole(NDILA_VIEWER_ROLES)

                            .anyRequest().authenticated()
                    )
                    .oauth2ResourceServer(oauth2 -> oauth2
                            .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
                    );
        } else if (allowAnonymous) {
            log.warn("Ndila JWT validation DISABLED (allow-anonymous=true) — dev/test mode");
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

        List<GrantedAuthority> authorities = new ArrayList<>();
        for (String r : roles) {
            authorities.add(new SimpleGrantedAuthority("ROLE_" + r));
        }
        return authorities;
    }
}
