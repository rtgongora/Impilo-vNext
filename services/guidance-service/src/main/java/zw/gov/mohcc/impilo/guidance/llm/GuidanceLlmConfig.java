package zw.gov.mohcc.impilo.guidance.llm;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Duration;

/**
 * Dedicated RestTemplate for calling the governed llm-orchestration-service from guidance.
 * Mirrors the CKP/experience-bff trust-header-forwarding pattern: copies the inbound caller's
 * Authorization + trust headers onto the outbound call so llm-orchestration (oauth2-protected by
 * default) can authenticate. When no real provider/key is reachable the call simply fails and the
 * reasoner falls back to knowledge-base retrieval — never fabricates.
 */
@Configuration
public class GuidanceLlmConfig {

    @Bean
    public RestTemplate guidanceLlmRestTemplate(
            RestTemplateBuilder builder,
            @Value("${guidance.llm.connect-timeout-ms:3000}") long connectMs,
            @Value("${guidance.llm.read-timeout-ms:12000}") long readMs) {
        return builder
                .setConnectTimeout(Duration.ofMillis(connectMs))
                .setReadTimeout(Duration.ofMillis(readMs))
                .additionalInterceptors(forwardHeadersInterceptor())
                .build();
    }

    private static ClientHttpRequestInterceptor forwardHeadersInterceptor() {
        return (request, body, execution) -> {
            ServletRequestAttributes attrs =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                HttpServletRequest in = attrs.getRequest();
                copy(in, request, "Authorization");
                copy(in, request, "X-Tenant-ID");
                copy(in, request, "X-Request-ID");
                copy(in, request, "X-Correlation-ID");
                copy(in, request, "X-Actor-ID");
            }
            request.getHeaders().set("X-Access-Mode", "INTERNAL");
            if (!request.getHeaders().containsKey("X-Service-Id")) {
                request.getHeaders().set("X-Service-Id", "guidance-service");
            }
            return execution.execute(request, body);
        };
    }

    private static void copy(HttpServletRequest in, HttpRequest out, String name) {
        String v = in.getHeader(name);
        if (v != null && !v.isBlank() && !out.getHeaders().containsKey(name)) {
            out.getHeaders().set(name, v);
        }
    }
}
