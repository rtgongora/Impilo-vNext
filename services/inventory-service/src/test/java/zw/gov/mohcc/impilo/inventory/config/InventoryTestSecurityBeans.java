package zw.gov.mohcc.impilo.inventory.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import java.time.Instant;

/**
 * Supplies beans omitted when the test profile excludes OAuth2 auto-configuration.
 * Imported only from test classes under {@code src/test/java}.
 */
@Configuration
public class InventoryTestSecurityBeans {

    @Bean
    public JwtDecoder jwtDecoder() {
        return token -> Jwt.withTokenValue(token)
                .header("alg", "none")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .claim("sub", "test-user")
                .build();
    }
}
