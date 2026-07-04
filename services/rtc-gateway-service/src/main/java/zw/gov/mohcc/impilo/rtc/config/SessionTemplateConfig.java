package zw.gov.mohcc.impilo.rtc.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import zw.gov.mohcc.impilo.sessiontemplates.SessionTemplateRegistry;

/**
 * Session-template doctrine registry. Fail-closed: the service refuses to boot
 * when any of the five mode documents is missing or malformed, so token policy
 * can never silently degrade to defaults.
 */
@Configuration
public class SessionTemplateConfig {

    @Bean
    public SessionTemplateRegistry sessionTemplateRegistry() {
        return new SessionTemplateRegistry();
    }
}
