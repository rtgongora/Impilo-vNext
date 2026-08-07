package zw.gov.mohcc.impilo.tshepo.authz.shadow;

import java.util.Map;

/**
 * Purpose-built input for the shadow evaluation endpoint.
 *
 * <p>Deliberately <b>not</b> the ext_authz shape. The shadow endpoint must never become an
 * alternate way to authenticate an ordinary request, so it takes its own DTO, lives on its own
 * path, and returns nothing an enforcement path could act on.</p>
 *
 * <p><b>The bearer field is the only credential that crosses this boundary, and it stops here.</b>
 * It is used to validate a session and resolve roles, then discarded. {@link #toString()} is
 * overridden to redact it, because the realistic way a token leaks is not a deliberate log line —
 * it is a DTO landing inside an exception message or a debug dump.</p>
 *
 * @param method           the original request method, so shadow and production evaluate the same thing
 * @param path             the original request path
 * @param prodVerdict      what ext_authz already decided — the comparison baseline, already final
 * @param prodActorType    the actor type production used, for the persona-change delta
 * @param trustContext     the trust/context header values production saw (tenant, actor, purpose,
 *                         facility, workspace, provider, department, ward, programme, subject,
 *                         assurance, workflow state). Same inputs, so a delta means roles, not drift.
 * @param bearer           transient. Never persisted, logged, traced or forwarded.
 * @param probeMode        ASYNC or INLINE_CANARY — which measurement produced this
 * @param totalAddedMillis wall-clock the caller measured, for the inline canary only
 */
public record ShadowEvaluationRequest(
        String method,
        String path,
        String prodVerdict,
        String prodActorType,
        Map<String, String> trustContext,
        String bearer,
        String probeMode,
        Integer totalAddedMillis
) {

    /**
     * Redacts the bearer. This is not decoration: a DTO reaching a log or an exception message is
     * the most likely way a token escapes, and that path does not go through a line anyone wrote
     * on purpose.
     */
    @Override
    public String toString() {
        return "ShadowEvaluationRequest[method=" + method
                + ", path=" + path
                + ", prodVerdict=" + prodVerdict
                + ", probeMode=" + probeMode
                + ", bearer=<redacted>]";
    }
}
