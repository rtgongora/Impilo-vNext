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
    private final AssuranceLevelResolutionInterceptor assuranceLevelResolutionInterceptor;

    public WebMvcConfig(RoleGuardInterceptor roleGuardInterceptor,
                        AssuranceLevelResolutionInterceptor assuranceLevelResolutionInterceptor) {
        this.roleGuardInterceptor = roleGuardInterceptor;
        this.assuranceLevelResolutionInterceptor = assuranceLevelResolutionInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(roleGuardInterceptor)
                .addPathPatterns(
                        "/internal/v1/admin/**",
                        "/internal/v1/finance/**"
                );

        // Resolve a citizen caller's current identity-assurance level once per request so the
        // trust-header interceptor can propagate it as X-Assurance-Level (closes G-CZO-01).
        // Excludes the assurance-status proxy path to avoid a redundant self-call.
        registry.addInterceptor(assuranceLevelResolutionInterceptor)
                .addPathPatterns("/internal/v1/**")
                .excludePathPatterns("/internal/v1/identity/assurance/**");
    }
}
