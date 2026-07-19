package zw.gov.mohcc.impilo.shared.card;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * Auto-configures the shared {@link CardVerificationClient} in every service that
 * depends on shared-core, so the card-resolve seam is available by injection
 * everywhere without per-service wiring. A service can override by declaring its
 * own {@code CardVerificationClient} bean.
 */
@AutoConfiguration
public class CardAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public CardVerificationClient cardVerificationClient(
            @Value("${impilo.card.enabled:true}") boolean enabled,
            @Value("${VITO_BASE_URL:${impilo.card.vito-base-url:http://localhost:8082}}") String vitoBaseUrl) {
        return new CardVerificationClient(enabled, vitoBaseUrl);
    }
}
