package zw.gov.mohcc.impilo.khuluma.client;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.companion.context.RequestContext;
import zw.gov.mohcc.impilo.companion.context.RequestContextHolder;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * Outbound HTTP client for downstream systems-of-record (rtc-gateway, notification, …).
 * The {@code trustHeaderForwardingInterceptor} propagates the v1.1 mandatory companion
 * headers + actor identity from the current request so downstream authz/audit see the
 * original caller. Mirrors experience-bff's {@code serviceRestTemplate} pattern.
 */
@Configuration
public class ServiceHttpConfig {

    @Bean
    public ClientHttpRequestInterceptor trustHeaderForwardingInterceptor() {
        return (request, body, execution) -> {
            RequestContext rc = RequestContextHolder.get();
            TrustContext tc = TrustContextHolder.get();
            var headers = request.getHeaders();

            String tenantId = tc != null && tc.tenantId() != null ? tc.tenantId().toString()
                    : (rc != null ? rc.tenantId() : null);
            putIfAbsent(headers, CompanionHeaders.TENANT_ID, tenantId);
            // Synthesize the mandatory identifiers when the call originates service-side
            // (Kafka/scheduler threads carry no RequestContext) — downstream companion
            // filters fail-closed on MISSING_REQUIRED_HEADER otherwise.
            putIfAbsent(headers, CompanionHeaders.POD_ID,
                    rc != null && rc.podId() != null && !rc.podId().isBlank() ? rc.podId() : "khuluma-service");
            putIfAbsent(headers, CompanionHeaders.REQUEST_ID,
                    rc != null && rc.requestId() != null ? rc.requestId() : UUID.randomUUID().toString());
            putIfAbsent(headers, CompanionHeaders.CORRELATION_ID,
                    rc != null && rc.correlationId() != null ? rc.correlationId() : UUID.randomUUID().toString());
            // Downstream v1.1 IdempotencyFilter requires Idempotency-Key on every
            // POST/PUT/PATCH to /internal/v1/**. Khuluma commands are already idempotent
            // at the domain layer, so a fresh key per outbound attempt is correct (and a
            // stable key would wrongly replay across distinct commands).
            if (isCommand(request.getMethod())) {
                putIfAbsent(headers, CompanionHeaders.IDEMPOTENCY_KEY, "khuluma:" + UUID.randomUUID());
            }
            if (tc != null) {
                putIfAbsent(headers, CompanionHeaders.ACTOR_ID, tc.actorId());
                putIfAbsent(headers, CompanionHeaders.ACTOR_TYPE, tc.actorType());
            }
            if (rc != null && rc.authToken() != null && !headers.containsKey(CompanionHeaders.AUTHORIZATION)) {
                headers.set(CompanionHeaders.AUTHORIZATION, CompanionHeaders.BEARER_PREFIX + rc.authToken());
            }
            return execution.execute(request, body);
        };
    }

    private static boolean isCommand(org.springframework.http.HttpMethod method) {
        return org.springframework.http.HttpMethod.POST.equals(method)
                || org.springframework.http.HttpMethod.PUT.equals(method)
                || org.springframework.http.HttpMethod.PATCH.equals(method);
    }

    private static void putIfAbsent(org.springframework.http.HttpHeaders headers, String name, String value) {
        if (value != null && !value.isBlank() && !headers.containsKey(name)) {
            headers.set(name, value);
        }
    }

    @Bean
    public RestTemplate serviceRestTemplate(ClientHttpRequestInterceptor trustHeaderForwardingInterceptor) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(Duration.ofSeconds(5));
        RestTemplate restTemplate = new RestTemplate(factory);
        restTemplate.setInterceptors(List.of(trustHeaderForwardingInterceptor));
        return restTemplate;
    }
}
