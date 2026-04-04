package zw.gov.mohcc.impilo.experience.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Registers backend policy interceptors for sensitive BFF paths.
 *
 * <p>The RoleGuardInterceptor enforces Keycloak role-based access control
 * on admin and finance endpoints, complementing the frontend AuthGuardProvider.</p>
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final RoleGuardInterceptor roleGuardInterceptor;

    public WebMvcConfig(RoleGuardInterceptor roleGuardInterceptor) {
        this.roleGuardInterceptor = roleGuardInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(roleGuardInterceptor)
                .addPathPatterns(
                        "/internal/v1/admin/**",
                        "/internal/v1/finance/**"
                );
    }
}
