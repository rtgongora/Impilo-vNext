package zw.gov.mohcc.impilo.live.integration;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;

import java.util.List;

final class IntegrationHttpSupport {

    private IntegrationHttpSupport() {}

    static HttpHeaders trustHeaders(TrustContext ctx) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        if (ctx != null) {
            if (ctx.tenantId() != null) {
                headers.set(TrustContext.H_TENANT_ID, ctx.tenantId().toString());
            }
            if (ctx.actorId() != null) {
                headers.set(TrustContext.H_ACTOR_ID, ctx.actorId());
            }
            if (ctx.actorType() != null) {
                headers.set(TrustContext.H_ACTOR_TYPE, ctx.actorType());
            }
            if (ctx.correlationId() != null) {
                headers.set(TrustContext.H_CORRELATION_ID, ctx.correlationId().toString());
            }
            if (ctx.purposeOfUse() != null) {
                headers.set(TrustContext.H_PURPOSE_OF_USE, ctx.purposeOfUse());
            }
        }
        return headers;
    }

    static String trimSlash(String url) {
        if (url == null) return "";
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
