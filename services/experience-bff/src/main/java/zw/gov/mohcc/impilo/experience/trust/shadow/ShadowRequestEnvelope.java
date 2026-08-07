package zw.gov.mohcc.impilo.experience.trust.shadow;

import java.util.Map;

/**
 * Everything the shadow probe needs, captured on the request thread and complete before it leaves.
 *
 * <h2>Why an envelope, and not the request</h2>
 * <p>The obvious implementation hands the {@code HttpServletRequest} to an executor. It works in
 * testing and fails in production, because a servlet container recycles the request object the
 * moment the response is committed. Async work then reads headers belonging to a <em>different
 * user's</em> request — a cross-tenant identity mix-up produced by a measurement, which is the
 * worst possible source of one. Under Tomcat with {@code recycledFacades=false} (the default) the
 * read usually succeeds and returns the wrong data rather than throwing, so the bug is silent.</p>
 *
 * <p>So nothing servlet-typed crosses the boundary. This record is deeply immutable: the map is
 * copied and wrapped at construction, and every field is a value. It is safe to publish to another
 * thread and impossible to invalidate by returning from a filter.</p>
 *
 * <h2>The bearer</h2>
 * <p>{@code bearer} is the only credential here. It exists so the PDP can resolve the roles a
 * browser session would have had; it is never persisted, never logged and never forwarded to a
 * domain service. {@link #toString()} redacts it, because the realistic way a token escapes is not
 * a log line someone wrote — it is an object landing in an exception message or a debug dump.</p>
 *
 * @param method         the live request's method
 * @param path           the live request's path
 * @param prodVerdict    the PDP's already-final answer, read from {@code x-decision}. Null when the
 *                       route bypassed ext_authz — which is itself a measurement.
 * @param prodActorType  the actor type production used, read from {@code x-actor-type}
 * @param trustContext   the trust/context header values production saw. Same inputs, so a delta
 *                       means roles, not drift.
 * @param bearer         transient; see above
 * @param capturedAtNanos when capture completed, so queue delay is measurable rather than assumed
 */
public record ShadowRequestEnvelope(
        String method,
        String path,
        String prodVerdict,
        String prodActorType,
        Map<String, String> trustContext,
        String bearer,
        long capturedAtNanos
) {

    public ShadowRequestEnvelope {
        // Copy, then wrap. Copying alone would still hand out a mutable view; wrapping alone would
        // leave the caller holding a handle it could mutate after publication.
        trustContext = trustContext == null
                ? Map.of()
                : Map.copyOf(trustContext);
    }

    /** Redacts the bearer. See the class doc — this is containment, not decoration. */
    @Override
    public String toString() {
        return "ShadowRequestEnvelope[method=" + method
                + ", path=" + path
                + ", prodVerdict=" + prodVerdict
                + ", bearer=<redacted>]";
    }
}
