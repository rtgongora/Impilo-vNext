package zw.gov.mohcc.impilo.costa.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.servlet.HandlerInterceptor;

import zw.gov.mohcc.impilo.shared.auth.DutyShadow;

import zw.gov.mohcc.impilo.shared.auth.ActorTypeGuard;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

/**
 * Refuses a write to this service unless the caller's actor type satisfies the duty for that path.
 *
 * <p>See {@link MoneyWriteAuthorization} for which paths map to which duty and why
 * {@code @PreAuthorize} is not the instrument here.</p>
 *
 * <h2>Where it runs</h2>
 * <p>As a {@code HandlerInterceptor}, so it runs after the handler is resolved but <b>before the
 * controller method and therefore before any entity is loaded</b>. That is the property
 * {@code ActorTypeGuard} asks for at every call site — "call this before loading the target entity"
 * — so a refused caller cannot use {@code POST /costa/v1/bills/{id}/approve} as an oracle for which
 * bill ids exist. Getting that for free everywhere is a reason to prefer the interceptor over 81
 * hand-placed calls.</p>
 *
 * <h2>Reads are untouched</h2>
 * <p>Only {@code POST}, {@code PUT}, {@code PATCH} and {@code DELETE} are gated. Read authorization
 * in this service is a separate question with a different answer, and widening this change to cover
 * it would mean guessing at a dozen more paths under time pressure. Stated, not silently skipped.</p>
 *
 * <h2>A null trust context is refused, not waved through</h2>
 * <p>{@code ActorTypeGuard.require} treats a null context as presenting no actor type and refuses
 * it. That matters because {@code TrustContextHolder.get()} returns null when the v1.1 headers were
 * absent — and "no context" must never be the cheapest way past a money gate.</p>
 */
public class MoneyWriteGuardInterceptor implements HandlerInterceptor {

    private final DutyShadow.Mode dutyMode;

    public MoneyWriteGuardInterceptor(
            @Value("${impilo.security.duty.mode:SHADOW}") String dutyMode) {
        this.dutyMode = DutyShadow.Mode.parse(dutyMode);
    }

    private static boolean isWrite(String method) {
        return "POST".equals(method) || "PUT".equals(method)
                || "PATCH".equals(method) || "DELETE".equals(method);
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // Guard the ORIGINAL request only. Spring re-runs the interceptor chain on the ERROR
        // dispatch, and by then TrustContextFilter has cleared its thread-local in a finally
        // block — so the guard would see no actor type, refuse, and rewrite the real outcome as a
        // bare 403.
        //
        // Found live, not reasoned about: a bill draft posted as PROVIDER passed this gate,
        // reached BillService.createDraft, failed a facility_id not-null constraint, and came back
        // 403 with an empty body while the log showed the insert. A guard that turns every
        // downstream error into "forbidden" hides the actual fault and looks like an authorization
        // bug in code that is working.
        if (request.getDispatcherType() != jakarta.servlet.DispatcherType.REQUEST) {
            return true;
        }
        if (!isWrite(request.getMethod())) {
            return true;
        }
        String path = request.getRequestURI();
        if (MoneyWriteAuthorization.isExempt(path)) {
            return true;
        }
        // Throws ResponseStatusException(403) naming what was required and what was presented, so
        // the first legitimate operator refused can act on the message rather than guess.
        var ctx = TrustContextHolder.get();
        ActorTypeGuard.require(ctx, MoneyWriteAuthorization.dutyFor(path));

        // Duty shadow. Runs AFTER the actor-type gate so the census counts only callers that
        // already cleared it -- otherwise every citizen refusal would also appear as a duty
        // would-deny and the rate would say nothing about duty. Changes no response in SHADOW.
        if (MoneyWriteAuthorization.isMoneyDecision(path)) {
            DutyShadow.observe(dutyMode, "this money decision", path,
                    ctx == null ? null : ctx.actorType());
        }
        return true;
    }
}
