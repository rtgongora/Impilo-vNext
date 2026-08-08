package zw.gov.mohcc.impilo.zibo.config;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.web.servlet.HandlerInterceptor;

import zw.gov.mohcc.impilo.shared.auth.ActorTypeGuard;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

/**
 * Refuses a change to governed terminology unless the caller's actor type satisfies its duty.
 *
 * <p>See {@link TerminologyWriteAuthorization} for which paths are governed, which are read-shaped,
 * and why neither {@code @PreAuthorize} nor an Envoy policy rule alone is the instrument here.</p>
 *
 * <h2>Where it runs</h2>
 * <p>As a {@code HandlerInterceptor}, so it runs after the handler is resolved but <b>before the
 * controller method and therefore before any artifact is loaded</b>. A refused caller cannot use
 * {@code POST /v1/artifacts/{id}/publish} as an oracle for which artifact ids exist.</p>
 *
 * <h2>Reads are untouched</h2>
 * <p>Only {@code POST}, {@code PUT}, {@code PATCH} and {@code DELETE} are gated. Read authorization
 * for terminology is a separate question — national vocabulary is meant to be widely readable, and
 * the rights regime that decides what may be read and redistributed is Addendum A.33 work with its
 * own manifest. Stated, not silently skipped.</p>
 *
 * <h2>A null trust context is refused, not waved through</h2>
 * <p>{@code ActorTypeGuard.require} treats a null context as presenting no actor type and refuses
 * it. {@code TrustContextHolder.get()} returns null when the v1.1 headers were absent, and "no
 * context" must never be the cheapest way past a governance gate.</p>
 */
public class TerminologyWriteGuardInterceptor implements HandlerInterceptor {

    private static boolean isWrite(String method) {
        return "POST".equals(method) || "PUT".equals(method)
                || "PATCH".equals(method) || "DELETE".equals(method);
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // Guard the ORIGINAL request only. Spring re-runs the interceptor chain on the ERROR
        // dispatch, by which time TrustContextFilter has cleared its thread-local in a finally
        // block — so the guard would see no actor type, refuse, and rewrite the real outcome as a
        // bare 403. That turns every downstream fault into "forbidden" and hides the actual error,
        // which costa hit live before this pattern was corrected there.
        if (request.getDispatcherType() != DispatcherType.REQUEST) {
            return true;
        }
        if (!isWrite(request.getMethod())) {
            return true;
        }
        String path = request.getRequestURI();
        if (TerminologyWriteAuthorization.isReadShaped(path)) {
            return true;
        }
        // Throws ResponseStatusException(403) naming what was required and what was presented, so
        // the first legitimate steward refused can act on the message rather than guess.
        ActorTypeGuard.require(TrustContextHolder.get(), TerminologyWriteAuthorization.dutyFor(path));
        return true;
    }
}
