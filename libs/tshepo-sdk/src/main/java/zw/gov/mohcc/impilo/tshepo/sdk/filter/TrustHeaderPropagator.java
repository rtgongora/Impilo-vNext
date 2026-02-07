package zw.gov.mohcc.impilo.tshepo.sdk.filter;

import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import zw.gov.mohcc.impilo.tshepo.contracts.headers.TrustHeaders;
import zw.gov.mohcc.impilo.tshepo.sdk.TrustContext;
import zw.gov.mohcc.impilo.tshepo.sdk.TrustContextHolder;

import java.io.IOException;

/**
 * RestClient / RestTemplate interceptor that propagates trust headers
 * from the current thread's {@link TrustContext} to outbound HTTP calls.
 *
 * <p>Register on any RestClient used for service-to-service calls:</p>
 * <pre>{@code
 * RestClient client = RestClient.builder()
 *     .requestInterceptor(new TrustHeaderPropagator())
 *     .build();
 * }</pre>
 */
public class TrustHeaderPropagator implements ClientHttpRequestInterceptor {

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body,
                                         ClientHttpRequestExecution execution) throws IOException {
        TrustContext ctx = TrustContextHolder.get();
        if (ctx != null) {
            request.getHeaders().set(TrustHeaders.TENANT_ID, ctx.tenantId().toString());
            request.getHeaders().set(TrustHeaders.ACTOR_ID, ctx.actorId());
            request.getHeaders().set(TrustHeaders.ACTOR_TYPE, ctx.actorType());
            request.getHeaders().set(TrustHeaders.PURPOSE_OF_USE, ctx.purposeOfUse());
            request.getHeaders().set(TrustHeaders.DEVICE_FINGERPRINT, ctx.deviceFingerprint());
            request.getHeaders().set(TrustHeaders.CORRELATION_ID, ctx.correlationId().toString());
            request.getHeaders().set(TrustHeaders.ACCESS_MODE, ctx.mode().name());

            if (ctx.facilityId() != null) {
                request.getHeaders().set(TrustHeaders.FACILITY_ID, ctx.facilityId().toString());
            }
            if (ctx.workspaceId() != null) {
                request.getHeaders().set(TrustHeaders.WORKSPACE_ID, ctx.workspaceId().toString());
            }
            if (ctx.shiftId() != null) {
                request.getHeaders().set(TrustHeaders.SHIFT_ID, ctx.shiftId());
            }
        }
        return execution.execute(request, body);
    }
}
