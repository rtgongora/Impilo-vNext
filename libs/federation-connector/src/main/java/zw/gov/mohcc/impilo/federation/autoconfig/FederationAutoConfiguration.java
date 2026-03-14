package zw.gov.mohcc.impilo.federation.autoconfig;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import zw.gov.mohcc.impilo.federation.client.SpineClient;
import zw.gov.mohcc.impilo.federation.filter.FederationIdentityFilter;
import zw.gov.mohcc.impilo.federation.identity.DefaultPodIdentityVerifier;
import zw.gov.mohcc.impilo.federation.identity.JwtAudienceVerifier;
import zw.gov.mohcc.impilo.federation.identity.PodIdentityVerifier;

/**
 * Spring Boot auto-configuration for the Federation Connector.
 *
 * <p>Activates automatically when:
 * <ul>
 *   <li>Running in a servlet web application</li>
 *   <li>{@link FederationIdentityFilter} is on the classpath</li>
 *   <li>{@code impilo.federation.enabled} is not explicitly set to {@code false}</li>
 * </ul>
 *
 * <p>Registers:
 * <ul>
 *   <li>{@link JwtAudienceVerifier} — verifies JWT aud=federation</li>
 *   <li>{@link DefaultPodIdentityVerifier} — default pod identity verification</li>
 *   <li>{@link FederationIdentityFilter} at order 9 — before V11HeaderFilter (order 10)</li>
 * </ul>
 *
 * <p>Filter order 9 ensures federation identity is checked before header enforcement,
 * so that fraudulent pod identity is caught early in the chain.</p>
 *
 * <p>Services can override any bean (e.g. provide their own PodIdentityVerifier).
 * Services can disable via {@code impilo.federation.enabled=false}.</p>
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(FederationIdentityFilter.class)
@ConditionalOnProperty(name = "impilo.federation.enabled", havingValue = "true", matchIfMissing = true)
public class FederationAutoConfiguration {

    private static final String[] FEDERATION_URL_PATTERNS = {
            "/internal/v1/*",
            "/internal/v1/**",
            "/external/v1/*",
            "/external/v1/**"
    };

    @Bean
    @ConditionalOnMissingBean
    public JwtAudienceVerifier federationJwtAudienceVerifier() {
        return new JwtAudienceVerifier();
    }

    @Bean
    @ConditionalOnMissingBean
    public PodIdentityVerifier federationPodIdentityVerifier(JwtAudienceVerifier audienceVerifier) {
        return new DefaultPodIdentityVerifier(audienceVerifier);
    }

    @Bean
    @ConditionalOnMissingBean(name = "federationIdentityFilter")
    public FilterRegistrationBean<FederationIdentityFilter> federationIdentityFilter(
            PodIdentityVerifier podIdentityVerifier) {
        FilterRegistrationBean<FederationIdentityFilter> reg = new FilterRegistrationBean<>();
        reg.setFilter(new FederationIdentityFilter(podIdentityVerifier));
        reg.addUrlPatterns(FEDERATION_URL_PATTERNS);
        reg.setOrder(9); // Before V11HeaderFilter (10)
        reg.setName("federationIdentityFilter");
        return reg;
    }
}
