package zw.gov.mohcc.impilo.tshepo.authz.dto;

import java.time.Instant;

/**
 * The answer to "does this actor hold a break-glass grant right now".
 *
 * <p><b>Two verdicts, not three.</b> This service can only ever answer {@code VALID} or
 * {@code NO_GRANT}. "I could not reach tshepo-authz" is not a verdict this service is able to
 * return — by definition, a service that cannot be reached returns nothing at all. That third
 * outcome is synthesised by the <em>client</em>, and the whole point of the split is that a caller
 * must never be able to confuse the two. A response body is evidence a decision was made; its
 * absence is not evidence of anything.</p>
 *
 * <p>Deliberately absent: roles, permissions, visibility ceilings, capabilities, tokens. This
 * record answers one closed question and carries nothing a caller could mistake for an
 * entitlement.</p>
 *
 * @param verdict      {@code VALID} or {@code NO_GRANT}
 * @param source       which grant model answered — {@code ESCALATION_GRANT} or
 *                     {@code BREAK_GLASS_REQUEST}; null when there is no grant
 * @param grantRef     identifier of the grant that answered, for the audit trail; null on NO_GRANT
 * @param expiresAt    when that grant lapses; null on NO_GRANT
 * @param reviewStatus post-hoc review state of the grant, where the model tracks one
 */
public record BreakGlassGrantValidationResponse(
        String verdict,
        String source,
        String grantRef,
        Instant expiresAt,
        String reviewStatus
) {

    public static final String VERDICT_VALID = "VALID";
    public static final String VERDICT_NO_GRANT = "NO_GRANT";

    public static final String SOURCE_ESCALATION_GRANT = "ESCALATION_GRANT";
    public static final String SOURCE_BREAK_GLASS_REQUEST = "BREAK_GLASS_REQUEST";

    public static BreakGlassGrantValidationResponse valid(String source, String grantRef,
                                                          Instant expiresAt, String reviewStatus) {
        return new BreakGlassGrantValidationResponse(VERDICT_VALID, source, grantRef, expiresAt, reviewStatus);
    }

    public static BreakGlassGrantValidationResponse noGrant() {
        return new BreakGlassGrantValidationResponse(VERDICT_NO_GRANT, null, null, null, null);
    }
}
