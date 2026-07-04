package zw.gov.mohcc.impilo.experience.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import zw.gov.mohcc.impilo.sessiontemplates.SessionTemplateRegistry;

/**
 * Session-template doctrine registry for experience composition (join-flow
 * hints, lobby behaviour, role vocabularies). Fail-closed at boot: the BFF and
 * rtc-gateway always agree on the same bundled template documents.
 */
@Configuration
public class SessionTemplateConfig {

    @Bean
    public SessionTemplateRegistry sessionTemplateRegistry() {
        return new SessionTemplateRegistry();
    }
}
