package zw.gov.mohcc.impilo.gl.config;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.web.servlet.HandlerInterceptor;

import zw.gov.mohcc.impilo.shared.auth.ActorTypeGuard;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

/**
 * Refuses a general-ledger write unless the caller's actor type satisfies the duty for that path.
 *
 * <p>See {@link GlWriteAuthorization} for what was open and why {@code @PreAuthorize} is not the
 * instrument here.</p>
 *
 * <p>Runs before the controller method and therefore before any entity is loaded — the property
 * {@code ActorTypeGuard} asks for at every call site, so a refused caller cannot use
 * {@code POST /internal/v1/gl/periods/{id}/close} as an oracle for which period ids exist.</p>
 *
 * <p><b>Guards the original request only.</b> Spring re-runs the interceptor chain on the ERROR
 * dispatch, by which point {@code TrustContextFilter} has cleared its thread-local in a finally
 * block — so the guard would see no actor type, refuse, and rewrite the real outcome as a bare 403.
 * That was found live in costa: a write PASSED the gate, reached the DAO, failed a constraint, and
 * came back 403 while the log showed the insert. Carried here so the same defect is not
 * reintroduced by copying the pattern without the fix.</p>
 *
 * <p>Reads are untouched; read authorization in this service is a separate question.</p>
 */
public class GlWriteGuardInterceptor implements HandlerInterceptor {

    private static boolean isWrite(String method) {
        return "POST".equals(method) || "PUT".equals(method)
                || "PATCH".equals(method) || "DELETE".equals(method);
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (request.getDispatcherType() != DispatcherType.REQUEST) {
            return true;
        }
        if (!isWrite(request.getMethod())) {
            return true;
        }
        String path = request.getRequestURI();
        if (GlWriteAuthorization.isExempt(path)) {
            return true;
        }
        // A null trust context presents no actor type and is refused, never waved through.
        ActorTypeGuard.require(TrustContextHolder.get(), GlWriteAuthorization.dutyFor(path));
        return true;
    }
}
