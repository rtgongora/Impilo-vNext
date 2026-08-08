package zw.gov.mohcc.impilo.costa.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Registers {@link MoneyWriteGuardInterceptor} across every path this service serves.
 *
 * <p>Registered with no path restriction on purpose. The gate's scope is decided in
 * {@link MoneyWriteAuthorization} — one place, next to the reasoning — rather than split between a
 * pattern list here and a duty map there, where the two could drift and the narrower one would win
 * silently.</p>
 */
@Configuration
public class MoneyWriteGuardConfig implements WebMvcConfigurer {

    /** OFF | SHADOW | ENFORCE — see {@code DutyShadow}. Defaults to SHADOW. */
    @Value("${impilo.security.duty.mode:SHADOW}")
    private String dutyMode;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new MoneyWriteGuardInterceptor(dutyMode)).addPathPatterns("/**");
    }
}
