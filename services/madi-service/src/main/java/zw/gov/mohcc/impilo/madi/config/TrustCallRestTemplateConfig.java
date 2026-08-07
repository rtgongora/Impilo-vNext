package zw.gov.mohcc.impilo.madi.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Duration;

/**
 * A {@link RestTemplate} for calls into the trust plane, which forwards the caller's own bearer
 * token.
 *
 * <h2>Why a separate template</h2>
 * <p>MADI's shared {@code restTemplate} carries no {@code Authorization} header. Adding forwarding to
 * it would attach the clinician's token to every outbound integration — Oros, Nhume, Butano,
 * surveillance — widening where that credential travels for no reason. This template is used by the
 * grant validator alone.</p>
 *
 * <h2>Why forward the caller's token rather than a workload token</h2>
 * <p>The question being asked is "does <em>this clinician</em> hold an escalation grant". The grant
 * is bound to the clinician, not to MADI, so the identity that must reach tshepo-authz is theirs.
 * Forwarding also lets the endpoint bind the answer to the token subject and refuse to answer about
 * anybody else — which is what stops it being an oracle for other people's grants.</p>
 *
 * <p>MADI already requires a bearer on its own API ({@code anyRequest().authenticated()}), so a real
 * request always carries one to forward. If it does not, no token is attached, tshepo-authz answers
 * 401, and the validator treats that as {@code UNREACHABLE} rather than as a refusal — see
 * {@code TshepoEscalationGrantValidator} for why an unanswered question must not read as "no grant".</p>
 *
 * <p>Timeouts are deliberately short. This call sits in front of an emergency clinical action; a slow
 * trust plane must degrade to the recorded-override path quickly rather than making a clinician wait.</p>
 */
@Configuration
public class TrustCallRestTemplateConfig {

    private static final Logger log = LoggerFactory.getLogger(TrustCallRestTemplateConfig.class);

    @Bean("trustCallRestTemplate")
    public RestTemplate trustCallRestTemplate(RestTemplateBuilder builder) {
        return builder
                .setConnectTimeout(Duration.ofSeconds(2))
                .setReadTimeout(Duration.ofSeconds(3))
                .additionalInterceptors((request, body, execution) -> {
                    String bearer = inboundAuthorization();
                    if (bearer != null) {
                        request.getHeaders().set(HttpHeaders.AUTHORIZATION, bearer);
                    } else {
                        // Not fatal here. The validator maps the resulting 401 to UNREACHABLE, so an
                        // emergency proceeds and is recorded rather than being silently blocked.
                        log.warn("No inbound Authorization to forward to the trust plane; the grant "
                                + "check will not be answerable and will be treated as unreachable.");
                    }
                    return execution.execute(request, body);
                })
                .build();
    }

    private static String inboundAuthorization() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs)) {
            return null; // not on a request thread — scheduler, consumer, test
        }
        return attrs.getRequest().getHeader(HttpHeaders.AUTHORIZATION);
    }
}
