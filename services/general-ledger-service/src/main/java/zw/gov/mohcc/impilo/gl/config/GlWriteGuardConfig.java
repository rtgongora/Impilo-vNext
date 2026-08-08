package zw.gov.mohcc.impilo.gl.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Registers {@link GlWriteGuardInterceptor} across every path this service serves.
 *
 * <p>No path restriction here on purpose: scope is decided in {@link GlWriteAuthorization}, one
 * place next to the reasoning, rather than split between a pattern list here and a duty map there
 * where the two could drift and the narrower one would win silently.</p>
 */
@Configuration
public class GlWriteGuardConfig implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new GlWriteGuardInterceptor()).addPathPatterns("/**");
    }
}
