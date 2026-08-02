package zw.gov.mohcc.impilo.shared.auth;

import org.springframework.http.HttpHeaders;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;

/**
 * Attaches this workload's own bearer token to outbound service-to-service calls.
 *
 * <p>Only when no {@code Authorization} header is already present. That ordering is load-bearing:
 * a request already carrying a human's token is a delegated call, and overwriting it with the
 * workload's own credential would silently convert "acting for a human" into "acting as the
 * service" — an escalation the callee could not detect, because the workload credential is the
 * stronger one.</p>
 *
 * <p>When no token can be obtained the header is simply absent and the callee rejects the call.
 * The interceptor never substitutes an unauthenticated fallback: failing closed at the callee is
 * the point.</p>
 */
public class WorkloadTokenInterceptor implements ClientHttpRequestInterceptor {

    private final WorkloadTokenProvider tokenProvider;

    public WorkloadTokenInterceptor(WorkloadTokenProvider tokenProvider) {
        this.tokenProvider = tokenProvider;
    }

    @Override
    public ClientHttpResponse intercept(org.springframework.http.HttpRequest request, byte[] body,
                                        ClientHttpRequestExecution execution) throws IOException {
        if (!request.getHeaders().containsKey(HttpHeaders.AUTHORIZATION)) {
            tokenProvider.getAccessToken().ifPresent(t -> request.getHeaders().setBearerAuth(t));
        }
        return execution.execute(request, body);
    }
}
