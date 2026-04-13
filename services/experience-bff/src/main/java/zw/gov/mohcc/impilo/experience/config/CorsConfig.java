package zw.gov.mohcc.impilo.experience.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * CORS configuration for mobile apps and web experience calling the BFF
 * from different origins.
 *
 * <p>Exposes a {@link CorsConfigurationSource} bean so that both Spring MVC
 * and Spring Security pick up the same CORS rules. Spring Security's
 * {@code .cors(Customizer.withDefaults())} discovers this bean automatically.</p>
 */
@Configuration
public class CorsConfig {

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(List.of(
                "http://localhost:*",
                "https://*.impilo.health",
                "https://*.impilo.gov.zw",
                "capacitor://*",     // mobile Capacitor apps
                "ionic://*"          // mobile Ionic apps
        ));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setExposedHeaders(List.of(
                "X-Request-ID",
                "X-Correlation-ID",
                "X-Policy-Decision",
                "X-Policy-Version"
        ));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);

        return source;
    }
}
