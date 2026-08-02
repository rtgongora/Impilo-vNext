package zw.gov.mohcc.impilo.learning.config;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
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

@Configuration
@EnableWebSecurity
public class SecurityConfig {

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
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            Collection<GrantedAuthority> authorities = new ArrayList<>();

            // Extract from realm_access.roles (Keycloak/TSHEPO standard)
            Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
            if (realmAccess != null && realmAccess.containsKey("roles")) {
                @SuppressWarnings("unchecked")
                List<String> realmRoles = (List<String>) realmAccess.get("roles");
                if (realmRoles != null) {
                    for (String role : realmRoles) {
                        if (role != null && !role.trim().isEmpty()) {
                            authorities.add(new SimpleGrantedAuthority("ROLE_" + role.trim().toUpperCase()));
                        }
                    }
                }
            }

            // Extract from top-level roles claim (array)
            Object rolesObj = jwt.getClaim("roles");
            if (rolesObj instanceof Collection<?> roles) {
                for (Object role : roles) {
                    if (role != null && !role.toString().trim().isEmpty()) {
                        authorities.add(new SimpleGrantedAuthority("ROLE_" + role.toString().trim().toUpperCase()));
                    }
                }
            }

            // Extract from top-level role claim (single value)
            Object roleObj = jwt.getClaim("role");
            if (roleObj != null && !roleObj.toString().trim().isEmpty()) {
                authorities.add(new SimpleGrantedAuthority("ROLE_" + roleObj.toString().trim().toUpperCase()));
            }

            // Extract from authorities claim
            Object authoritiesObj = jwt.getClaim("authorities");
            if (authoritiesObj instanceof Collection<?> auths) {
                for (Object auth : auths) {
                    if (auth != null && !auth.toString().trim().isEmpty()) {
                        authorities.add(new SimpleGrantedAuthority("ROLE_" + auth.toString().trim().toUpperCase()));
                    }
                }
            }

            return authorities;
        });
        return converter;
    }

    @Bean
    @Order(1)
    public SecurityFilterChain integrationChain(HttpSecurity http) throws Exception {
        http.securityMatcher(
                        "/internal/v1/learning/integrations/**",
                        "/internal/v1/learning/orchestration/**",
                        "/v1/internal/fundo/**")
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
            @Value("${impilo.security.disable-oauth-for-tests:false}") boolean disableOauthForTests) throws Exception {
        http.securityMatcher("/**")
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
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
