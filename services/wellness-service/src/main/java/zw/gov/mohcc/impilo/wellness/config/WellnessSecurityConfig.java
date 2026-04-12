package zw.gov.mohcc.impilo.wellness.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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
 * Mirrors Experience BFF rules for citizen wellness, wallet, and Health Connect paths
 * so the BFF can forward the same JWT.
 */
@Configuration
@EnableWebSecurity
public class WellnessSecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(WellnessSecurityConfig.class);

    private static final String[] CITIZEN_ROLES = {"CITIZEN", "SYSTEM_ADMIN", "DEVELOPER"};

    @Autowired(required = false)
    private JwtDecoder jwtDecoder;

    @Value("${impilo.security.allow-anonymous:false}")
    private boolean allowAnonymous;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.disable());
        http.sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS));

        if (jwtDecoder != null) {
            http.authorizeHttpRequests(auth -> auth
                            .requestMatchers("/actuator/**").permitAll()
                            .requestMatchers("/internal/v1/wellness/connect/**")
                            .authenticated()
                            .requestMatchers("/internal/v1/mobile/citizen/wellness/**")
                            .authenticated()
                            .requestMatchers("/internal/v1/mobile/citizen/monitoring/**")
                            .authenticated()
                            .requestMatchers("/internal/v1/mobile/citizen/**")
                            .hasAnyRole(CITIZEN_ROLES)
                            .anyRequest()
                            .denyAll())
                    .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(keycloakJwtConverter())));
        } else if (allowAnonymous) {
            log.warn("SECURITY: JWT validation DISABLED — impilo.security.allow-anonymous=true on wellness-service.");
            http.authorizeHttpRequests(a -> a.anyRequest().permitAll());
        } else {
            throw new IllegalStateException(
                    "No JwtDecoder and impilo.security.allow-anonymous is false. "
                            + "Configure OAuth2 resource server or set impilo.security.allow-anonymous=true for tests.");
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
