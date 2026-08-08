package zw.gov.mohcc.impilo.nhume.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.shared.auth.WorkloadTokenProperties;
import zw.gov.mohcc.impilo.shared.auth.WorkloadTokenProvider;

/**
 * Nhume's own workload credential, used by the delivery write-back gateway.
 *
 * <p>Nhume reports courier milestones to the services that own the cargo truth — OROS
 * specimens, MADI blood orders, PCT referrals and Msika Flow marketplace orders/selections.
 * Those calls carried only the signing actor's bearer <em>when one happened to be on the
 * inbound request</em>, and nothing at all on a scheduled retry, which runs outside any
 * request context. A callee therefore could not verify the caller, and msika-flow's two
 * callback paths were exempted from its resource server to compensate.</p>
 *
 * <p>The token provider is given a separate, <strong>un-intercepted</strong>
 * {@link RestTemplate}: handing it the intercepted one would mean acquiring a token
 * requires a token.</p>
 */
@Configuration
@EnableConfigurationProperties(WorkloadTokenProperties.class)
public class WorkloadTokenConfig {

    /** Un-intercepted, and used only to reach the identity provider. */
    @Bean
    public RestTemplate workloadTokenRestTemplate() {
        return new RestTemplate();
    }

    @Bean
    public WorkloadTokenProvider workloadTokenProvider(RestTemplate workloadTokenRestTemplate,
                                                       WorkloadTokenProperties properties) {
        return new WorkloadTokenProvider(workloadTokenRestTemplate, properties);
    }
}
