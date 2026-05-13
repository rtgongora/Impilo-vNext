package zw.gov.mohcc.impilo.mushex.service.rail;

/**
 * Strategy for choosing which payment rail adapter a {@code PaymentIntent} is targeted
 * at. The default implementation ({@code DefaultRailSelectionPolicy}) is deterministic
 * and stateless; future implementations may consult tenant- or facility-scoped
 * preferences.
 *
 * <p>See {@code docs/design/g4-rail-selection-policy.md}.
 */
public interface RailSelectionPolicy {

    /**
     * Select an effective rail and explanatory metadata for the given request.
     *
     * @param request inputs derived from the inbound {@code CreateIntentRequest} and
     *                already-known platform context (e.g. impilo-simulation flag)
     * @return a successful selection, including the effective rail, the original
     *         preference (if any), fallback flag and a {@code RailSelectionReason}
     * @throws RailSelectionException when the request cannot produce a valid selection
     *         (unknown rail, or unavailable preferred rail with fallback disallowed)
     */
    RailSelectionResult select(RailSelectionRequest request);
}
