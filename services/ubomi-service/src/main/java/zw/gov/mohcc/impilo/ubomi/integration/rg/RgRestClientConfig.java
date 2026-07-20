package zw.gov.mohcc.impilo.ubomi.integration.rg;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * RG outbound-call configuration. Registers {@link UbomiRgProperties} and, only
 * when {@code ubomi.rg.enabled=true}, a dedicated {@link RestTemplate} for the
 * {@link RegistrarGeneralClient} with the short timeouts from the config surface
 * (fail-fast → RG_PENDING). Mirrors the {@code tshepo-authz RestClientConfig}
 * outbound posture.
 *
 * <p>When the flag is off this configuration contributes <b>no</b> HTTP client
 * bean, so ubomi gains no RG connectivity surface at all.</p>
 *
 * <p><b>mTLS (deploy-time):</b> the {@code rgRestTemplate} below carries the
 * timeouts; the mutual-TLS {@code SSLContext} is built from the tshepo-keys
 * custody refs ({@code ubomi.rg.mtls.*}) at deploy time by the integrator —
 * their presence is startup-validated in {@link RegistrarGeneralClient}. No
 * inline PEM ever lives in config.</p>
 */
@Configuration
@EnableConfigurationProperties(UbomiRgProperties.class)
public class RgRestClientConfig {

    @Bean("rgRestTemplate")
    @ConditionalOnProperty(prefix = "ubomi.rg", name = "enabled", havingValue = "true")
    public RestTemplate rgRestTemplate(RestTemplateBuilder builder, UbomiRgProperties props) {
        return builder
                .setConnectTimeout(Duration.ofMillis(props.getConnectTimeoutMs()))
                .setReadTimeout(Duration.ofMillis(props.getReadTimeoutMs()))
                .build();
    }
}
