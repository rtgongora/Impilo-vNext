package zw.gov.mohcc.impilo.abis.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import zw.gov.mohcc.impilo.abis.engine.BiometricMatchingEngine;
import zw.gov.mohcc.impilo.abis.engine.FailClosedMatchingEngine;

/**
 * Registers the fail-closed default {@link BiometricMatchingEngine} unless a vendor SDK
 * engine bean is present. Vendor engines register themselves as {@code @Primary}
 * {@link BiometricMatchingEngine} beans and automatically displace this default.
 */
@Configuration
public class MatchingEngineConfig {

    @Bean
    @ConditionalOnMissingBean(BiometricMatchingEngine.class)
    public BiometricMatchingEngine failClosedMatchingEngine(
            @Value("${abis.matching.liveness-min-score:0}") double livenessMinScore) {
        return new FailClosedMatchingEngine(livenessMinScore);
    }
}
