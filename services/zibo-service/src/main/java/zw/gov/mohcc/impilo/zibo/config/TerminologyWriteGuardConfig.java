package zw.gov.mohcc.impilo.zibo.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Registers {@link TerminologyWriteGuardInterceptor} across every path this service serves.
 *
 * <p>Registered with no path restriction on purpose. The gate's scope is decided in
 * {@link TerminologyWriteAuthorization} — one place, next to the reasoning — rather than split
 * between a pattern list here and a duty map there, where the two could drift and the narrower one
 * would win silently.</p>
 *
 * <p>Note the contrast with {@code SecurityBaselineConfig}, which registers the rate-limit filter on
 * {@code /v1/*} only and therefore does not cover {@code /api/v1/terminology/validate} or the
 * {@code /internal/v1/**} routes. Scoping by pattern is exactly how a control ends up not applying
 * where it was assumed to.</p>
 */
@Configuration
public class TerminologyWriteGuardConfig implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new TerminologyWriteGuardInterceptor()).addPathPatterns("/**");
    }
}
