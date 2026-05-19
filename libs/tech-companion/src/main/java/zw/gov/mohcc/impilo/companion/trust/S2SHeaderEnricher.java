package zw.gov.mohcc.impilo.companion.trust;

import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.companion.context.RequestContext;
import zw.gov.mohcc.impilo.companion.context.RequestContextHolder;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Builds the canonical set of trust headers for an outbound internal
 * service-to-service call. Implements the headers prescribed by
 * {@code SERVICE_TO_SERVICE_TRUST_PATTERN.md}.
 *
 * <p>This is intentionally header-only — wire format independent. Callers
 * can apply the headers to RestClient/WebClient/Feign/OkHttp builders.
 */
@Component
public class S2SHeaderEnricher {

    private final ServiceIdentity identity;

    public S2SHeaderEnricher(ServiceIdentity identity) {
        this.identity = identity;
    }

    /**
     * Build the trust header map for an outbound S2S call, inheriting from the
     * inbound request context (tenant, pod, correlation, actor, etc.) when
     * present and overlaying caller-service identity headers.
     *
     * @param requestSource origination class for this call
     * @param purposeOfUse  optional purpose-of-use override
     * @param idempotencyKey optional idempotency key
     */
    public Map<String, String> headersFor(RequestSource requestSource, String purposeOfUse, String idempotencyKey) {
        Map<String, String> out = new HashMap<>();
        Optional<RequestContext> ctx = RequestContextHolder.current();

        out.put(CompanionHeaders.TENANT_ID,      ctx.map(RequestContext::tenantId).orElse("system"));
        out.put(CompanionHeaders.POD_ID,         ctx.map(RequestContext::podId).orElse("system"));
        out.put(CompanionHeaders.REQUEST_ID,     UUID.randomUUID().toString());
        out.put(CompanionHeaders.CORRELATION_ID, ctx.map(RequestContext::correlationId).orElseGet(() -> UUID.randomUUID().toString()));

        // Service identity (caller)
        out.put(CompanionHeaders.SERVICE_ID,      identity.id());
        out.put(CompanionHeaders.SERVICE_NAME,    identity.name());
        out.put(CompanionHeaders.SERVICE_VERSION, identity.version());
        out.put(CompanionHeaders.REQUEST_SOURCE,  requestSource.name());

        if (purposeOfUse != null && !purposeOfUse.isBlank()) {
            out.put(CompanionHeaders.PURPOSE_OF_USE, purposeOfUse);
        }
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            out.put(CompanionHeaders.IDEMPOTENCY_KEY, idempotencyKey);
        }
        return out;
    }

    /** Convenience: HttpHeaders variant. */
    public HttpHeaders httpHeadersFor(RequestSource requestSource, String purposeOfUse, String idempotencyKey) {
        HttpHeaders h = new HttpHeaders();
        headersFor(requestSource, purposeOfUse, idempotencyKey).forEach(h::set);
        return h;
    }
}
