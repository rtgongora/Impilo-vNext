package zw.gov.mohcc.impilo.tshepo.sdk.envelope;

import jakarta.servlet.Filter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

import java.time.Duration;

/**
 * Makes adopting D3 a dependency line rather than a hand-edit in every service.
 *
 * <p>Adding {@code tshepo-sdk} registers the filter; it stays inert until
 * {@code impilo.trust.decision-envelope.mode} is set to SHADOW or ENFORCE. Defaulting to OFF
 * is what lets the dependency land everywhere in one change and the behaviour be switched on
 * per service afterwards, instead of the two arriving together in seventy pull requests.</p>
 */
@Configuration
@ConditionalOnClass(Filter.class)
@EnableConfigurationProperties(DecisionEnvelopeProperties.class)
public class DecisionEnvelopeAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public JwksSource decisionEnvelopeJwksSource(DecisionEnvelopeProperties properties) {
        return new JwksSource(
                properties.getJwksUri(),
                Duration.ofSeconds(properties.getJwksCacheSeconds()),
                Duration.ofSeconds(properties.getJwksRefreshCooldownSeconds()));
    }

    @Bean
    @ConditionalOnMissingBean
    public DecisionEnvelopeVerifier decisionEnvelopeVerifier(JwksSource jwksSource,
                                                             DecisionEnvelopeProperties properties) {
        return new DecisionEnvelopeVerifier(jwksSource, properties.getClockSkewSeconds(),
                properties.isBindPath());
    }

    @Bean
    public FilterRegistrationBean<DecisionEnvelopeFilter> decisionEnvelopeFilter(
            DecisionEnvelopeVerifier verifier, DecisionEnvelopeProperties properties) {
        FilterRegistrationBean<DecisionEnvelopeFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new DecisionEnvelopeFilter(verifier, properties));
        // Ahead of the rate limiter and the trust-context filter: a request that never passed
        // the PDP should not consume this service's rate-limit budget or populate a trust
        // context, both of which are work done on behalf of a caller that has proven nothing.
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        return registration;
    }
}
