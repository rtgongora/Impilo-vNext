package zw.gov.mohcc.impilo.learning.config;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.client.RestTemplate;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

    static {
        System.err.println("!!! SecurityConfig class is being LOADED !!!");
    }

    public SecurityConfig() {
        System.err.println("!!! SecurityConfig() constructor called !!!");
    }

    /**
     * Custom JwtAuthenticationConverter that extracts roles from multiple JWT claim paths:
     * - realm_access.roles (Keycloak/TSHEPO format)
     * - roles (flat array)
     * - role (single value)
     * - authorities (alternative format)
     *
     * This ensures that SYSTEM_ADMIN role from TSHEPO authorization response is properly
     * recognized by Spring Security and available for access control decisions.
     */
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        log.warn("=== JwtAuthenticationConverter bean being created ===");
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            log.warn("!!! JwtAuthenticationConverter.setJwtGrantedAuthoritiesConverter: Processing JWT !!!");
            log.warn("  JWT subject: {}, issuer: {}", jwt.getSubject(), jwt.getIssuer());
            log.warn("  JWT claims keys: {}", jwt.getClaims().keySet());

            Collection<GrantedAuthority> authorities = new ArrayList<>();

            // Extract from realm_access.roles (Keycloak/TSHEPO standard)
            Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
            log.info("  realm_access claim: {}", realmAccess);
            if (realmAccess != null && realmAccess.containsKey("roles")) {
                @SuppressWarnings("unchecked")
                List<String> realmRoles = (List<String>) realmAccess.get("roles");
                if (realmRoles != null) {
                    log.info("  Found realm_access.roles: {}", realmRoles);
                    for (String role : realmRoles) {
                        if (role != null && !role.trim().isEmpty()) {
                            String grantedRole = "ROLE_" + role.trim().toUpperCase();
                            authorities.add(new SimpleGrantedAuthority(grantedRole));
                            log.info("    Added authority from realm_access: {}", grantedRole);
                        }
                    }
                }
            } else {
                log.info("  No realm_access or realm_access.roles found in JWT");
            }

            // Extract from top-level roles claim (array)
            Object rolesObj = jwt.getClaim("roles");
            if (rolesObj instanceof Collection<?> roles) {
                log.info("  Found top-level roles claim: {}", rolesObj);
                for (Object role : roles) {
                    if (role != null && !role.toString().trim().isEmpty()) {
                        String grantedRole = "ROLE_" + role.toString().trim().toUpperCase();
                        authorities.add(new SimpleGrantedAuthority(grantedRole));
                        log.info("    Added authority from roles: {}", grantedRole);
                    }
                }
            }

            // Extract from top-level role claim (single value)
            Object roleObj = jwt.getClaim("role");
            if (roleObj != null && !roleObj.toString().trim().isEmpty()) {
                log.info("  Found top-level role claim: {}", roleObj);
                String grantedRole = "ROLE_" + roleObj.toString().trim().toUpperCase();
                authorities.add(new SimpleGrantedAuthority(grantedRole));
                log.info("    Added authority from role: {}", grantedRole);
            }

            // Extract from authorities claim
            Object authoritiesObj = jwt.getClaim("authorities");
            if (authoritiesObj instanceof Collection<?> auths) {
                log.info("  Found authorities claim: {}", authoritiesObj);
                for (Object auth : auths) {
                    if (auth != null && !auth.toString().trim().isEmpty()) {
                        String grantedRole = "ROLE_" + auth.toString().trim().toUpperCase();
                        authorities.add(new SimpleGrantedAuthority(grantedRole));
                        log.info("    Added authority from authorities: {}", grantedRole);
                    }
                }
            }

            log.info("  Total authorities extracted: {}", authorities.size());
            for (GrantedAuthority auth : authorities) {
                log.info("    - {}", auth.getAuthority());
            }
            return authorities;
        });
        return converter;
    }

    @Bean
    @Order(1)
    public SecurityFilterChain integrationChain(HttpSecurity http) throws Exception {
        http.securityMatcher(
                        "/internal/v1/learning/integrations/**", "/internal/v1/learning/orchestration/**")
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a.anyRequest().permitAll());
        return http.build();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain defaultChain(
            HttpSecurity http,
            JwtAuthenticationConverter jwtAuthenticationConverter,
            @Value("${learning.security.oauth2-enabled:true}") boolean oauth2Enabled) throws Exception {
        System.err.println("!!! defaultChain SecurityFilterChain being configured. oauth2Enabled = " + oauth2Enabled);
        log.warn("!!! defaultChain SecurityFilterChain being configured. oauth2Enabled = {}", oauth2Enabled);
        http.securityMatcher("/**")
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS));

        if (oauth2Enabled) {
            System.err.println("!!! oauth2Enabled is TRUE - configuring JWT resource server");
            // Add a filter to log incoming Authorization headers
            http.addFilterBefore((request, response, chain) -> {
                var servletRequest = (jakarta.servlet.http.HttpServletRequest) request;
                String authHeader = servletRequest.getHeader("Authorization");
                System.err.println("!!! SECURITY FILTER: Authorization header = " + (authHeader == null ? "null" : authHeader.substring(0, Math.min(50, authHeader.length())) + "..."));
                log.warn("!!! SecurityConfig filter: Authorization header = {}",
                         authHeader == null ? "null" : (authHeader.length() > 50 ? authHeader.substring(0, 50) + "..." : authHeader));
                chain.doFilter(request, response);
            }, org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter.class);

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
                                    "/swagger-ui.html")
                            .permitAll()
                            .anyRequest()
                            .authenticated())
                    .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter)));
        } else {
            http.authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        }
        return http.build();
    }
}
